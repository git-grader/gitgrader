/*
 * Copyright the GitGrader contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gitgrader.git.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.gitgrader.assignments.AdmissionDecision;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.GitProperties;
import org.gitgrader.git.CommitSignatureResult;
import org.gitgrader.git.PushFeedback;
import org.gitgrader.git.PushFeedbackWriter;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.git.internal.PushAdmissionRules.PushVerdict;
import org.gitgrader.git.internal.StudentKeyAuthenticator.AuthenticatedStudent;
import org.gitgrader.security.ResultTokenService;
import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionRefusedException;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Admits or refuses a push, and records the submission when it is admitted.
 *
 * <p>
 * This is the point where the whole product comes together: the SSH identity, the
 * assignment schedule, the commit signatures and the immutable submission record. It runs
 * as a JGit pre-receive hook, which means a refusal happens <em>before</em> any ref is
 * updated - a rejected push leaves the repository exactly as it was.
 *
 * <p>
 * The server-side receive time is captured once, at the top, and used for every decision
 * and for the stored record. Reading the clock again per check would let a long push
 * straddle a deadline.
 */
@Component
public class PushAdmissionHook {

	private static final Logger logger = LoggerFactory.getLogger(PushAdmissionHook.class);

	private final AssignmentCatalog assignmentCatalog;

	private final SubmissionService submissionService;

	private final ResultTokenService resultTokens;

	private final GitRepositoryService repositoryService;

	private final PushFeedbackWriter feedbackWriter;

	private final AppProperties appProperties;

	private final GitProperties gitProperties;

	private final CommitSignatureVerifier signatureVerifier;

	private final Clock clock;

	public PushAdmissionHook(AssignmentCatalog assignmentCatalog, SubmissionService submissionService,
			ResultTokenService resultTokens, GitRepositoryService repositoryService, PushFeedbackWriter feedbackWriter,
			AppProperties appProperties, GitProperties gitProperties, CommitSignatureVerifier signatureVerifier,
			Clock clock) {
		this.assignmentCatalog = assignmentCatalog;
		this.submissionService = submissionService;
		this.resultTokens = resultTokens;
		this.repositoryService = repositoryService;
		this.feedbackWriter = feedbackWriter;
		this.appProperties = appProperties;
		this.gitProperties = gitProperties;
		this.signatureVerifier = signatureVerifier;
		this.clock = clock;
	}

	/**
	 * Installs the admission hook on one receive-pack invocation.
	 * @param session the authenticated SSH session
	 * @param pack the receive-pack about to process the push
	 */
	public void install(ServerSession session, ReceivePack pack) {
		AuthenticatedStudent student = session.getAttribute(StudentKeyAuthenticator.AUTHENTICATED_STUDENT);
		RepositoryRecord repository = session.getAttribute(GitSshServer.RESOLVED_REPOSITORY);
		if (student == null || repository == null) {
			return;
		}
		pack.setPreReceiveHook((receivePack, commands) -> onPreReceive(receivePack, commands, student, repository));
	}

	private void onPreReceive(ReceivePack pack, Collection<ReceiveCommand> commands, AuthenticatedStudent student,
			RepositoryRecord repository) {
		Instant receivedAt = Instant.now(this.clock);
		Optional<AssignmentView> assignment = this.assignmentCatalog.findAssignment(repository.assignmentId());
		if (assignment.isEmpty()) {
			rejectAll(commands, "This assignment no longer exists.");
			return;
		}

		AdmissionDecision decision = this.assignmentCatalog.canAccept(repository.assignmentId(), student.studentId(),
				receivedAt);
		PushAdmissionRules rules = new PushAdmissionRules(this.gitProperties, this.signatureVerifier);

		for (ReceiveCommand command : commands) {
			PushVerdict verdict = rules.evaluate(pack.getRepository(), command, student, decision.accepted(),
					describe(decision));
			if (!verdict.accepted()) {
				reject(pack, command, student, repository, verdict.message());
				continue;
			}
			try {
				accept(pack, student, repository, assignment.get(), decision, verdict, receivedAt);
			}
			catch (SubmissionRefusedException ex) {
				// Recording the submission rolled back, so the ref must not move either.
				// The audit entry survives: it is written in its own transaction.
				reject(pack, command, student, repository, ex.getMessage());
			}
		}
	}

	private void reject(ReceivePack pack, ReceiveCommand command, AuthenticatedStudent student,
			RepositoryRecord repository, @Nullable String message) {
		command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "rejected");
		pack.sendMessage("");
		pack.sendMessage(message);
		logger.info("Rejected push from student {} to {}: {}", student.studentId(), repository.repositoryPath(),
				message);
	}

	/**
	 * Records the submission and writes the push feedback block.
	 *
	 * <p>
	 * The submission is stored before the ref update completes, on purpose: a student
	 * whose network drops after the objects transferred should still have a recorded
	 * attempt rather than silently losing it.
	 * @param pack the receive-pack, used for side-band output
	 * @param student the authenticated student
	 * @param repository the target repository
	 * @param assignment the assignment being answered
	 * @param decision the schedule decision
	 * @param verdict the admission verdict
	 * @param receivedAt the single server-side receive time for this push
	 */
	private void accept(ReceivePack pack, AuthenticatedStudent student, RepositoryRecord repository,
			AssignmentView assignment, AdmissionDecision decision, PushVerdict verdict, Instant receivedAt) {
		RevCommit tip = verdict.tip();
		if (tip == null) {
			throw new IllegalStateException("An accepted push must always carry a branch tip");
		}
		CommitSignatureResult signature = verdict.signature();
		SignatureVerdict recorded = (signature != null) ? toVerdict(signature) : SignatureVerdict.UNSIGNED;

		SubmissionView submission = this.submissionService.record(NewSubmission.builder()
			.target(repository.id(), student.studentId(), assignment.courseId(), assignment.id())
			.repositoryPath(repository.repositoryPath())
			.commit(tip.name(), "refs/heads/main", tip.getShortMessage(), Instant.ofEpochSecond(tip.getCommitTime()))
			.receivedAt(receivedAt)
			.signature(recorded, null, (signature != null) ? signature.keyFingerprint() : null,
					student.transportKeyId())
			.pins(assignment.templateVersionId(), assignment.testSuiteVersionId(), assignment.runtimeId(), null)
			.admission(SubmissionStatus.RECEIVED, decision.late(), decision.effectiveDueAt())
			.build());

		this.repositoryService.recordPush(repository);
		String token = this.resultTokens.issue(submission.id());

		PushFeedback feedback = new PushFeedback(this.appProperties.name(), student.displayName(), assignment.title(),
				submission.shortCommitSha(), recorded.badge(), PushFeedback.PushOutcome.ACCEPTED_PENDING, null, null,
				null, this.appProperties.resultUrl(token), List.of(), null);

		pack.sendMessage("");
		this.feedbackWriter.render(feedback).forEach(pack::sendMessage);
		logger.info("Accepted submission {} from student {} on assignment {}", submission.id(), student.studentId(),
				assignment.id());
	}

	private void rejectAll(Collection<ReceiveCommand> commands, String message) {
		commands.forEach((command) -> command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, message));
	}

	private static SignatureVerdict toVerdict(CommitSignatureResult result) {
		return switch (result.status()) {
			case VERIFIED -> SignatureVerdict.VERIFIED;
			case UNSIGNED -> SignatureVerdict.UNSIGNED;
			case INVALID -> SignatureVerdict.INVALID;
			case UNKNOWN_KEY -> SignatureVerdict.UNKNOWN_KEY;
			case KEY_REVOKED -> SignatureVerdict.KEY_REVOKED;
			case WRONG_OWNER -> SignatureVerdict.WRONG_OWNER;
		};
	}

	private static String describe(AdmissionDecision decision) {
		return switch (decision.outcome()) {
			case ACCEPTED -> "";
			case NOT_YET_OPEN -> "This assignment has not opened yet.";
			case PAST_DEADLINE ->
				"The deadline for this assignment has passed. " + "If you need an extension, contact your instructor.";
			case ASSIGNMENT_CLOSED -> "This assignment is closed.";
			case ASSIGNMENT_ARCHIVED -> "This assignment has been archived.";
			case ASSIGNMENT_DRAFT -> "This assignment is not published yet.";
		};
	}

}
