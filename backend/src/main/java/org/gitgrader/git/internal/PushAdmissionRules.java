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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.gitgrader.configuration.GitProperties;
import org.gitgrader.git.CommitSignatureResult;
import org.gitgrader.git.internal.StudentKeyAuthenticator.AuthenticatedStudent;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the commits in one push may be accepted.
 *
 * <p>
 * Kept free of JGit's {@code ReceivePack} plumbing and of Spring so that every rule can
 * be exercised directly in a unit test. The transport adapter calls {@link #evaluate} and
 * translates the verdict into a protocol response.
 *
 * <p>
 * The checks run in the order the brief lays out, and the order matters: the cheap
 * structural rules run before signature verification, so a push that was never going to
 * be accepted does not pay for cryptography on every commit.
 */
public class PushAdmissionRules {

	/** Abbreviated hash length used in messages, matching git's default. */
	private static final int SHORT_SHA_LENGTH = 7;

	private static final Logger logger = LoggerFactory.getLogger(PushAdmissionRules.class);

	/**
	 * Ceiling on commits inspected per push.
	 *
	 * <p>
	 * Signature verification is per-commit, so an enormous push would otherwise be an
	 * easy way to occupy the server. A student assignment never legitimately approaches
	 * this number.
	 *
	 * <p>
	 * Exceeding it rejects the whole push. Truncating the walk instead - which is what
	 * this did until the check below was added - meant the commits past the ceiling were
	 * never signature checked and were admitted anyway, so a push of more than this many
	 * commits could carry unsigned history in behind a signed tip.
	 */
	private static final int MAX_COMMITS_PER_PUSH = 1_000;

	private final GitProperties gitProperties;

	private final CommitSignatureVerifier signatureVerifier;

	public PushAdmissionRules(GitProperties gitProperties, CommitSignatureVerifier signatureVerifier) {
		this.gitProperties = gitProperties;
		this.signatureVerifier = signatureVerifier;
	}

	/**
	 * Evaluates one ref update.
	 * @param repository the bare repository being pushed to
	 * @param command the ref update requested by the client
	 * @param student the authenticated student
	 * @param assignmentOpen whether the assignment accepts submissions right now
	 * @param closedReason why the assignment is closed, when it is
	 * @return the verdict, never {@code null}
	 */
	public PushVerdict evaluate(Repository repository, ReceiveCommand command, AuthenticatedStudent student,
			boolean assignmentOpen, @Nullable String closedReason) {
		PushVerdict structural = checkStructure(command, assignmentOpen, closedReason);
		if (structural != null) {
			return structural;
		}

		List<RevCommit> newCommits;
		try {
			newCommits = newCommitsIn(repository, command);
		}
		catch (IOException ex) {
			return PushVerdict.rejected("The pushed objects could not be read: " + ex.getMessage());
		}

		PushVerdict oversized = checkShape(repository, newCommits);
		if (oversized != null) {
			return oversized;
		}

		if (!this.gitProperties.requireSignedCommits()) {
			RevCommit tip = newCommits.getFirst();
			return PushVerdict.accepted(tip, CommitSignatureResult
				.rejected(CommitSignatureResult.CommitSignatureStatus.UNSIGNED, null, "Signing not required"));
		}
		return verifyEveryCommit(repository, newCommits, student);
	}

	/**
	 * Refuses a push whose size makes it a burden rather than a submission.
	 *
	 * <p>
	 * Both limits run before any signature is checked, because verification is per-commit
	 * and materialisation is per-file: paying either cost for a push that was never going
	 * to be accepted is exactly the denial of service these bound.
	 * @param repository the repository being pushed to
	 * @param newCommits the commits this push introduces
	 * @return a rejection, or {@code null} when the push is within every limit
	 */
	private @Nullable PushVerdict checkShape(Repository repository, List<RevCommit> newCommits) {
		if (newCommits.isEmpty()) {
			return PushVerdict.rejected("The push contained no new commits.");
		}
		if (newCommits.size() > MAX_COMMITS_PER_PUSH) {
			return PushVerdict.rejected("This push introduces more than " + MAX_COMMITS_PER_PUSH
					+ " new commits, which is more than an assignment is expected to contain. "
					+ "Push your work in smaller steps, or start from the assignment template.");
		}
		return checkTreeSize(repository, newCommits.getFirst());
	}

	/**
	 * Refuses a tip whose tree holds more files than an assignment should.
	 *
	 * <p>
	 * The whole tree is materialised into a workspace before every grading run, so file
	 * count is a cost the platform pays repeatedly rather than once at push time. Pack
	 * and object size are bounded during receive; this bounds the shape the pack expands
	 * into, which a small pack can still make enormous.
	 * @param repository the repository being pushed to
	 * @param tip the commit at the branch tip
	 * @return a rejection, or {@code null} when the tree is within the limit
	 */
	private @Nullable PushVerdict checkTreeSize(Repository repository, RevCommit tip) {
		int limit = this.gitProperties.maxFileCount();
		try (RevWalk walk = new RevWalk(repository); TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(walk.parseCommit(tip).getTree());
			treeWalk.setRecursive(true);
			int files = 0;
			while (treeWalk.next()) {
				files++;
				if (files > limit) {
					return PushVerdict.rejected("This submission contains more than " + limit
							+ " files, which is more than an assignment is expected to hold. "
							+ "Remove build output and dependencies, and add them to .gitignore.");
				}
			}
		}
		catch (IOException ex) {
			return PushVerdict.rejected("The pushed tree could not be read: " + ex.getMessage());
		}
		return null;
	}

	/**
	 * Applies the rules that need no object access.
	 *
	 * <p>
	 * Separated so that a push which was never going to be accepted is refused before any
	 * cryptography runs.
	 * @param command the ref update requested by the client
	 * @param assignmentOpen whether the assignment accepts submissions right now
	 * @param closedReason why the assignment is closed, when it is
	 * @return a rejection, or {@code null} when the structural rules all pass
	 */
	private @Nullable PushVerdict checkStructure(ReceiveCommand command, boolean assignmentOpen,
			@Nullable String closedReason) {
		if (!isAllowedRef(command.getRefName())) {
			return PushVerdict.rejected("Only branch updates under refs/heads/ are accepted. "
					+ "Tags and other refs are not used for grading.");
		}
		if (command.getType() == ReceiveCommand.Type.DELETE) {
			return PushVerdict.rejected(
					"Deleting a branch is not permitted; " + "your submission history has to remain reconstructible.");
		}
		if (!assignmentOpen) {
			boolean explained = closedReason != null && !closedReason.isBlank();
			return PushVerdict
				.rejected(explained ? closedReason : "This assignment is not accepting submissions right now.");
		}
		return null;
	}

	/**
	 * Requires every newly pushed commit to be signed by this student.
	 *
	 * <p>
	 * Checking every commit rather than only the branch tip is deliberate. A student can
	 * sign the tip and leave the parents unsigned, and accepting that would make the
	 * signature meaningless for everything but the last change in the push.
	 * @param repository the repository being pushed to
	 * @param newCommits the commits this push introduces
	 * @param student the authenticated student
	 * @return the verdict
	 */
	private PushVerdict verifyEveryCommit(Repository repository, List<RevCommit> newCommits,
			AuthenticatedStudent student) {
		for (RevCommit commit : newCommits) {
			CommitSignatureResult result = this.signatureVerifier.verify(repository, commit, student.studentId());
			if (!result.isAcceptable()) {
				return PushVerdict.rejected(explain(commit, result), result);
			}
		}
		RevCommit tip = newCommits.getFirst();
		return PushVerdict.accepted(tip, this.signatureVerifier.verify(repository, tip, student.studentId()));
	}

	/**
	 * Turns a signature failure into something a student can act on.
	 * @param commit the offending commit
	 * @param result why it was refused
	 * @return a message safe to send over the Git side band
	 */
	private String explain(RevCommit commit, CommitSignatureResult result) {
		String shortSha = commit.abbreviate(SHORT_SHA_LENGTH).name();
		// The student is told only that the signature is unacceptable, deliberately. The
		// operator needs the reason though, and without it a rejected push is impossible
		// to diagnose from the server side.
		logger.info("Signature on commit {} rejected as {}: key={} detail={}", shortSha, result.status(),
				result.keyFingerprint(), result.detail());
		return switch (result.status()) {
			case UNSIGNED -> "Commit " + shortSha + " is not signed. Enable SSH commit signing:\n"
					+ "  git config --global gpg.format ssh\n"
					+ "  git config --global user.signingkey ~/.ssh/id_ed25519.pub\n"
					+ "  git config --global commit.gpgsign true\n"
					+ "then amend or re-create the commit and push again.";
			case INVALID -> "The signature on commit " + shortSha + " is not valid.";
			case UNKNOWN_KEY -> "Commit " + shortSha + " was signed with a key that is not registered "
					+ "to you. Add the key to your profile, or sign with a key you already registered.";
			case KEY_REVOKED -> "Commit " + shortSha + " was signed with a key that has been revoked "
					+ "or replaced. Sign with your current key and push again.";
			case WRONG_OWNER -> "Commit " + shortSha + " was signed with a key registered to a " + "different student.";
			case VERIFIED -> "";
		};
	}

	private boolean isAllowedRef(String refName) {
		return GitProperties.ALLOWED_REF_PREFIXES.stream().anyMatch(refName::startsWith);
	}

	/**
	 * Enumerates the commits a push introduces, newest first.
	 *
	 * <p>
	 * Everything already reachable from another ref is excluded, so a student who merges
	 * an existing branch is not asked to re-sign history they did not write.
	 * @param repository the repository being pushed to
	 * @param command the ref update
	 * @return the new commits
	 * @throws IOException if the object database cannot be read
	 */
	private List<RevCommit> newCommitsIn(Repository repository, ReceiveCommand command) throws IOException {
		List<RevCommit> commits = new ArrayList<>();
		try (RevWalk walk = new RevWalk(repository)) {
			walk.markStart(walk.parseCommit(command.getNewId()));
			ObjectId oldId = command.getOldId();
			if (oldId != null && !ObjectId.zeroId().equals(oldId) && repository.getObjectDatabase().has(oldId)) {
				walk.markUninteresting(walk.parseCommit(oldId));
			}
			for (RevCommit commit : walk) {
				commits.add(commit);
				// One past the ceiling is all the caller needs to reject the push, and it
				// keeps the walk bounded on a repository with enormous history.
				if (commits.size() > MAX_COMMITS_PER_PUSH) {
					break;
				}
			}
		}
		return commits;
	}

	/**
	 * The outcome of evaluating one ref update.
	 *
	 * @param accepted whether the push may proceed
	 * @param message the reason, when it may not
	 * @param tip the commit at the branch tip, when accepted
	 * @param signature the recorded signature outcome
	 */
	public record PushVerdict(boolean accepted, @Nullable String message, @Nullable RevCommit tip,
			@Nullable CommitSignatureResult signature) {

		static PushVerdict accepted(RevCommit tip, CommitSignatureResult signature) {
			return new PushVerdict(true, null, tip, signature);
		}

		static PushVerdict rejected(String message) {
			return new PushVerdict(false, message, null, null);
		}

		static PushVerdict rejected(String message, CommitSignatureResult signature) {
			return new PushVerdict(false, message, null, signature);
		}
	}

}
