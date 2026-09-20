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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.GitProperties;
import org.gitgrader.git.PushFeedbackWriter;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.security.ResultTokenService;
import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the account-status gate that runs before any admission rule.
 *
 * <p>
 * The gate refuses a push when the profile that authenticated is now suspended or
 * archived, or still unverified under a deployment that requires instructor verification.
 * It sits between SSH identity and the assignment schedule; driving it through the
 * pre-receive hook the server installs is what proves the refusal is wired into the push
 * path rather than skipped on the happy day.
 */
class PushAdmissionHookTest {

	private static final UUID COURSE_ID = UUID.randomUUID();

	private static final UUID ASSIGNMENT_ID = UUID.randomUUID();

	private static final UUID STUDENT_ID = UUID.randomUUID();

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);

	private ReceivePack pack;

	private AssignmentCatalog assignmentCatalog;

	private SubmissionService submissionService;

	private CommitSignatureVerifier signatureVerifier;

	private StudentDirectory students;

	@BeforeEach
	void setUp() {
		this.pack = mock(ReceivePack.class);
		this.assignmentCatalog = mock(AssignmentCatalog.class);
		this.submissionService = mock(SubmissionService.class);
		this.signatureVerifier = mock(CommitSignatureVerifier.class);
		this.students = mock(StudentDirectory.class);

		when(this.assignmentCatalog.findAssignment(ASSIGNMENT_ID)).thenReturn(Optional.of(assignmentView()));
	}

	@Test
	@DisplayName("refuses a suspended student's push before any admission rule runs")
	void refusesSuspendedPush() throws Exception {
		givenProfile(StudentStatus.SUSPENDED);

		PushVerdicts outcome = push(requireInstructorVerification(false));

		assertThat(outcome.command().getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
		assertThat(outcome.command().getMessage()).contains("suspended");
		verify(this.signatureVerifier, never()).verify(any(), any(), any(), any());
		verify(this.submissionService, never()).record(any(NewSubmission.class));
	}

	@Test
	@DisplayName("refuses an archived student's push")
	void refusesArchivedPush() throws Exception {
		givenProfile(StudentStatus.ARCHIVED);

		PushVerdicts outcome = push(requireInstructorVerification(false));

		assertThat(outcome.command().getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
		assertThat(outcome.command().getMessage()).contains("archived");
		verify(this.submissionService, never()).record(any(NewSubmission.class));
	}

	@Test
	@DisplayName("refuses an unverified self-registration when the deployment requires verification")
	void refusesUnverifiedSelfRegistrationInStrictDeployments() throws Exception {
		givenProfile(StudentStatus.SELF_REGISTERED);

		PushVerdicts outcome = push(requireInstructorVerification(true));

		assertThat(outcome.command().getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
		assertThat(outcome.command().getMessage()).contains("not yet been verified by an instructor");
		verify(this.submissionService, never()).record(any(NewSubmission.class));
	}

	@Test
	@DisplayName("refuses a push whose profile disappeared since authentication")
	void refusesMissingProfile() throws Exception {
		when(this.students.findById(STUDENT_ID)).thenReturn(Optional.empty());

		PushVerdicts outcome = push(requireInstructorVerification(false));

		assertThat(outcome.command().getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
		assertThat(outcome.command().getMessage()).contains("could not be found");
		verify(this.submissionService, never()).record(any(NewSubmission.class));
	}

	private PushVerdicts push(AppProperties appProperties) throws Exception {
		ServerSession session = mock(ServerSession.class);
		when(session.getAttribute(StudentKeyAuthenticator.AUTHENTICATED_STUDENT))
			.thenReturn(new StudentKeyAuthenticator.AuthenticatedStudent(STUDENT_ID, "Max Muster", UUID.randomUUID(),
					"SHA256:transportkey"));
		when(session.getAttribute(GitSshServer.RESOLVED_REPOSITORY))
			.thenReturn(new RepositoryRecord(ASSIGNMENT_ID, STUDENT_ID, "course/assignment/student", CLOCK));

		PushAdmissionHook hook = new PushAdmissionHook(this.assignmentCatalog, this.submissionService,
				mock(ResultTokenService.class), mock(GitRepositoryService.class), mock(PushFeedbackWriter.class),
				appProperties, gitProperties(), this.signatureVerifier, this.students, CLOCK);
		hook.install(session, this.pack);

		ArgumentCaptor<PreReceiveHook> captor = ArgumentCaptor.forClass(PreReceiveHook.class);
		verify(this.pack).setPreReceiveHook(captor.capture());

		ReceiveCommand command = new ReceiveCommand(ObjectId.zeroId(), ObjectId.zeroId(), "refs/heads/main");
		captor.getValue().onPreReceive(this.pack, List.of(command));

		return new PushVerdicts(command);
	}

	private void givenProfile(StudentStatus status) {
		when(this.students.findById(STUDENT_ID)).thenReturn(Optional.of(new StudentView(STUDENT_ID, "s1000042",
				"Max Muster", "max@example.org", status, "CLASS-A", Instant.parse("2026-02-01T10:00:00Z"))));
	}

	private static AppProperties requireInstructorVerification(boolean requireInstructorVerification) {
		return new AppProperties("GitGrader", URI.create("https://localhost"), "support@example.org",
				"Example Organization", URI.create("https://docs.example.org"), ZoneId.of("UTC"), "/data",
				new AppProperties.Registration(true, requireInstructorVerification, 5),
				new AppProperties.ResultTokens(256, Duration.ofDays(180), 8));
	}

	private static GitProperties gitProperties() {
		return new GitProperties(true, "localhost", 2222, "0.0.0.0", 2222, "git", "/tmp/hostkey.ser",
				"/tmp/repositories", DataSize.ofMegabytes(50), DataSize.ofMegabytes(10), 2000, Set.of("ssh-ed25519"),
				true, Duration.ofMinutes(10));
	}

	private static AssignmentView assignmentView() {
		return new AssignmentView(ASSIGNMENT_ID, COURSE_ID, "assignment-01", "Assignment 01", null, 1,
				AssignmentStatus.OPEN, true, Instant.parse("2026-02-01T10:00:00Z"),
				Instant.parse("2026-12-01T10:00:00Z"), "UTC", new BigDecimal("100"), 10, new BigDecimal("100"), false,
				null, null, null, null, null, null, null, false);
	}

	/**
	 * The single command this test pushes and how the pre-receive hook answered it.
	 *
	 * @param command the pushed ref update, carrying the result the gate chose
	 */
	private record PushVerdicts(ReceiveCommand command) {
	}

}
