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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.gitgrader.configuration.GitProperties;
import org.gitgrader.git.CommitSignatureResult;
import org.gitgrader.git.CommitSignatureResult.CommitSignatureStatus;
import org.gitgrader.git.internal.PushAdmissionRules.PushVerdict;
import org.gitgrader.git.internal.StudentKeyAuthenticator.AuthenticatedStudent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PushAdmissionRules} against a real Git repository.
 *
 * <p>
 * The commits here are created by JGit rather than faked, because the rules operate on
 * real {@code RevCommit} objects and on the ref-update shapes JGit produces. A test built
 * on stub objects would pass while the production path failed on the first genuine push.
 */
class PushAdmissionRulesTest {

	private static final AuthenticatedStudent STUDENT = new AuthenticatedStudent(UUID.randomUUID(), "Max Muster",
			UUID.randomUUID(), "SHA256:transportkey");

	@TempDir
	private Path workspace;

	private Git git;

	private Repository repository;

	private CommitSignatureVerifier verifier;

	@BeforeEach
	void setUp() throws GitAPIException, IOException {
		this.git = Git.init().setDirectory(this.workspace.toFile()).setInitialBranch("main").call();
		this.repository = this.git.getRepository();
		this.verifier = mock(CommitSignatureVerifier.class);
	}

	@AfterEach
	void tearDown() {
		this.git.close();
	}

	@Test
	@DisplayName("accepts a push whose commits are all signed by this student")
	void acceptsFullySignedPush() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");
		givenSignature(CommitSignatureResult.verified("SHA256:signingkey"));

		PushVerdict verdict = rules(true).evaluate(this.repository, update(commit), STUDENT, true, null);

		assertThat(verdict.accepted()).isTrue();
		assertThat(verdict.tip()).isNotNull();
		assertThat(verdict.tip().name()).isEqualTo(commit.name());
	}

	@Test
	@DisplayName("rejects an unsigned commit with instructions for enabling signing")
	void rejectsUnsignedCommit() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");
		givenSignature(CommitSignatureResult.rejected(CommitSignatureStatus.UNSIGNED, null, "no gpgsig"));

		PushVerdict verdict = rules(true).evaluate(this.repository, update(commit), STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		// The message has to be actionable: a bare "rejected" turns into a support
		// ticket.
		assertThat(verdict.message()).contains("is not signed");
		assertThat(verdict.message()).contains("gpg.format ssh");
		assertThat(verdict.message()).contains("commit.gpgsign true");
	}

	@Test
	@DisplayName("rejects a commit signed with another student's key")
	void rejectsWrongOwner() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");
		givenSignature(CommitSignatureResult.rejected(CommitSignatureStatus.WRONG_OWNER, "SHA256:other",
				"belongs to someone else"));

		PushVerdict verdict = rules(true).evaluate(this.repository, update(commit), STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("different student");
	}

	@Test
	@DisplayName("rejects a commit signed with a revoked key")
	void rejectsRevokedKey() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");
		givenSignature(CommitSignatureResult.rejected(CommitSignatureStatus.KEY_REVOKED, "SHA256:old", "revoked"));

		PushVerdict verdict = rules(true).evaluate(this.repository, update(commit), STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("revoked");
	}

	@Test
	@DisplayName("accepts an unsigned commit when signing is not required")
	void acceptsUnsignedWhenSigningDisabled() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");

		PushVerdict verdict = rules(false).evaluate(this.repository, update(commit), STUDENT, true, null);

		assertThat(verdict.accepted()).isTrue();
	}

	@Test
	@DisplayName("refuses a push after the deadline, with the schedule's own explanation")
	void refusesClosedAssignment() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");

		PushVerdict verdict = rules(true).evaluate(this.repository, update(commit), STUDENT, false,
				"The deadline for this assignment has passed.");

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).isEqualTo("The deadline for this assignment has passed.");
	}

	@Test
	@DisplayName("falls back to a generic message when no reason was supplied")
	void refusesClosedAssignmentWithoutReason() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");

		PushVerdict verdict = rules(true).evaluate(this.repository, update(commit), STUDENT, false, "");

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("not accepting submissions");
	}

	@Test
	@DisplayName("refuses anything that is not a branch update")
	void refusesNonBranchRefs() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");
		ReceiveCommand tagUpdate = new ReceiveCommand(ObjectId.zeroId(), commit.getId(), "refs/tags/v1");

		PushVerdict verdict = rules(true).evaluate(this.repository, tagUpdate, STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("refs/heads/");
	}

	@Test
	@DisplayName("refuses a branch deletion so history stays reconstructible")
	void refusesBranchDeletion() throws Exception {
		RevCommit commit = commit("solution.js", "export const answer = 42;");
		ReceiveCommand deletion = new ReceiveCommand(commit.getId(), ObjectId.zeroId(), "refs/heads/main");

		PushVerdict verdict = rules(true).evaluate(this.repository, deletion, STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("Deleting a branch is not permitted");
	}

	@Test
	@DisplayName("checks every new commit, not only the branch tip")
	void checksEveryCommitNotJustTheTip() throws Exception {
		// The important case: a student signs the tip and leaves the parent unsigned.
		// Accepting that would make the signature meaningless for the earlier change.
		RevCommit first = commit("a.js", "one");
		RevCommit second = commit("b.js", "two");

		when(this.verifier.verify(any(), eq(second), any()))
			.thenReturn(CommitSignatureResult.verified("SHA256:signingkey"));
		when(this.verifier.verify(any(), eq(first), any()))
			.thenReturn(CommitSignatureResult.rejected(CommitSignatureStatus.UNSIGNED, null, "parent unsigned"));

		PushVerdict verdict = rules(true).evaluate(this.repository, update(second), STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("is not signed");
	}

	@Test
	@DisplayName("refuses a tree holding more files than the configured limit")
	void refusesTooManyFiles() throws Exception {
		for (int i = 0; i < 4; i++) {
			commit("file-" + i + ".txt", "content " + i);
		}
		RevCommit tip = commit("file-last.txt", "content");
		givenSignature(CommitSignatureResult.verified("SHA256:signingkey"));

		PushVerdict verdict = rules(true, 3).evaluate(this.repository, update(tip), STUDENT, true, null);

		assertThat(verdict.accepted()).isFalse();
		assertThat(verdict.message()).contains("more than 3 files");
	}

	@Test
	@DisplayName("verifies every commit a push introduces, not only the ones it walked first")
	void verifiesEveryIntroducedCommit() throws Exception {
		RevCommit first = commit("a.txt", "a");
		RevCommit second = commit("b.txt", "b");
		RevCommit tip = commit("c.txt", "c");

		// The ceiling used to truncate the walk rather than refuse the push, so commits
		// past it were admitted without ever being verified. Counting the calls is what
		// detects a regression to that behaviour: one per introduced commit, plus the
		// re-verification of the tip that builds the accepted verdict.
		givenSignature(CommitSignatureResult.verified("SHA256:signingkey"));

		PushVerdict verdict = rules(true, 2000).evaluate(this.repository, update(tip), STUDENT, true, null);

		assertThat(verdict.accepted()).isTrue();
		verify(this.verifier, times(1)).verify(any(), argThat((c) -> c.name().equals(first.name())), any());
		verify(this.verifier, times(1)).verify(any(), argThat((c) -> c.name().equals(second.name())), any());
		verify(this.verifier, times(2)).verify(any(), argThat((c) -> c.name().equals(tip.name())), any());
	}

	@Test
	@DisplayName("grades the ref's tip even when an ancestor carries a later timestamp")
	void takesTheTipFromTheWalkNotFromTheDates() throws Exception {
		// The tip is taken as the first commit the walk yields, which is only the tip
		// because a walk started from one commit pops that commit before anything it
		// leads to. Dates do not decide it, and they must not: a commit's date is
		// whatever its author's clock said, so a second start point or an explicit sort
		// would hand grading an ancestor while refs/heads/main pointed elsewhere.
		RevCommit ancestor = commitDated("start.js", "export const answer = 0;", Duration.ofDays(365));
		RevCommit tip = commitDated("solution.js", "export const answer = 42;", Duration.ZERO);
		assertThat(tip.getCommitTime()).isLessThan(ancestor.getCommitTime());
		givenSignature(CommitSignatureResult.verified("SHA256:signingkey"));

		PushVerdict verdict = rules(true).evaluate(this.repository, update(tip), STUDENT, true, null);

		assertThat(verdict.accepted()).isTrue();
		assertThat(verdict.tip()).isNotNull();
		assertThat(verdict.tip().name()).isEqualTo(tip.name());
	}

	private RevCommit commitDated(String fileName, String content, Duration ahead) throws GitAPIException, IOException {
		Files.writeString(this.workspace.resolve(fileName), content, StandardCharsets.UTF_8);
		this.git.add().addFilepattern(fileName).call();
		PersonIdent when = new PersonIdent("Max Muster", "max@example.org",
				java.util.Date.from(java.time.Instant.parse("2026-03-01T10:00:00Z").plus(ahead)),
				java.util.TimeZone.getTimeZone("UTC"));
		return this.git.commit()
			.setMessage("Add " + fileName)
			.setSign(Boolean.FALSE)
			.setAuthor(when)
			.setCommitter(when)
			.call();
	}

	private PushAdmissionRules rules(boolean requireSignedCommits) {
		return rules(requireSignedCommits, 2000);
	}

	private PushAdmissionRules rules(boolean requireSignedCommits, int maxFileCount) {
		GitProperties properties = new GitProperties(true, "localhost", 2222, "0.0.0.0", 2222, "git",
				"/tmp/hostkey.ser", "/tmp/repositories", DataSize.ofMegabytes(50), DataSize.ofMegabytes(10),
				maxFileCount, Set.of("ssh-ed25519"), requireSignedCommits, Duration.ofMinutes(10));
		return new PushAdmissionRules(properties, this.verifier);
	}

	private void givenSignature(CommitSignatureResult result) {
		when(this.verifier.verify(any(), any(), any())).thenReturn(result);
	}

	private RevCommit commit(String fileName, String content) throws GitAPIException, IOException {
		Files.writeString(this.workspace.resolve(fileName), content, StandardCharsets.UTF_8);
		this.git.add().addFilepattern(fileName).call();
		return this.git.commit().setMessage("Add " + fileName).setSign(Boolean.FALSE).call();
	}

	private ReceiveCommand update(RevCommit tip) {
		return new ReceiveCommand(ObjectId.zeroId(), tip.getId(), "refs/heads/main");
	}

}
