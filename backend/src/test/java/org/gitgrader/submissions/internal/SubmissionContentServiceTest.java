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

package org.gitgrader.submissions.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.submissions.web.SubmissionContentEntry;
import org.gitgrader.submissions.web.SubmissionFileContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SubmissionContentService} against a real repository.
 *
 * <p>
 * Real Git objects rather than stubs, because the class exists to answer questions only
 * Git can answer: what does the tree at this commit contain, and is this blob text or
 * something else.
 */
class SubmissionContentServiceTest {

	@TempDir
	private Path root;

	private SubmissionContentService service;

	private Path repositories;

	private SubmissionService submissions;

	private UUID submissionId;

	@BeforeEach
	void setUp() throws IOException {
		this.repositories = Files.createDirectories(this.root.resolve("repositories"));
		StorageProperties storage = new StorageProperties(this.repositories.toString(),
				this.root.resolve("templates").toString(), this.root.resolve("tests").toString(),
				this.root.resolve("artifacts").toString(), this.root.resolve("tmp").toString());
		this.submissions = mock(SubmissionService.class);
		this.service = new SubmissionContentService(this.submissions, storage);
	}

	/**
	 * Stubs the submission lookup so the service resolves the repository the test just
	 * seeded. The view's repository path must agree with {@link #seedRepository}, or the
	 * service would read a repository that does not exist.
	 */
	private void givenSubmission(String repositoryPath, String commitSha) {
		SubmissionView view = new SubmissionView(UUID.randomUUID(), UUID.randomUUID(), repositoryPath,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), commitSha, commitSha.substring(0, 7),
				"refs/heads/main", "initial", Instant.parse("2026-01-01T10:00:00Z"), SignatureVerdict.UNSIGNED, null,
				SubmissionStatus.RECEIVED, false, null, null, null);
		when(this.submissions.findById(view.id())).thenReturn(Optional.of(view));
		this.submissionId = view.id();
	}

	@Test
	@DisplayName("lists the root tree of the graded commit")
	void listsRootTree() throws Exception {
		String commit = seedRepository("course/assignment/root", "export const answer = 42;");
		givenSubmission("course/assignment/root", commit);

		List<SubmissionContentEntry> entries = this.service.listTree(this.submissionId, null);

		assertThat(entries).extracting(SubmissionContentEntry::name)
			.containsExactlyInAnyOrder("README.md", "src/", "build.sh");
	}

	@Test
	@DisplayName("lists a subtree when a path is given")
	void listsSubtree() throws Exception {
		String commit = seedRepository("course/assignment/tree", "export const answer = 42;");
		givenSubmission("course/assignment/tree", commit);

		List<SubmissionContentEntry> entries = this.service.listTree(this.submissionId, "src");

		assertThat(entries).extracting(SubmissionContentEntry::path).containsExactly("src/solution.js");
	}

	@Test
	@DisplayName("returns an empty list for a path that does not exist")
	void listsUnknownPathAsEmpty() throws Exception {
		String commit = seedRepository("course/assignment/unknown", "export const answer = 42;");
		givenSubmission("course/assignment/unknown", commit);

		assertThat(this.service.listTree(this.submissionId, "no/such/path")).isEmpty();
	}

	@Test
	@DisplayName("reads the content of one file at the graded commit")
	void readsFileContent() throws Exception {
		String commit = seedRepository("course/assignment/read", "export const answer = 42;");
		givenSubmission("course/assignment/read", commit);

		SubmissionFileContent content = this.service.readFile(this.submissionId, "src/solution.js");

		assertThat(content.path()).isEqualTo("src/solution.js");
		assertThat(content.binary()).isFalse();
		assertThat(content.content()).isEqualTo("export const answer = 42;");
	}

	@Test
	@DisplayName("reports a binary file without content rather than mojibake")
	void reportsBinaryFileWithoutContent() throws Exception {
		// A PNG signature followed by a nul byte: both signals the sniffer looks for.
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00 };
		String commit = seedRepository("course/assignment/binary", png);
		givenSubmission("course/assignment/binary", commit);

		SubmissionFileContent content = this.service.readFile(this.submissionId, "src/solution.js");

		assertThat(content.binary()).isTrue();
		assertThat(content.content()).isNull();
	}

	@Test
	@DisplayName("decodes valid UTF-8 and rejects invalid UTF-8")
	void decodesStrictly() {
		assertThat(SubmissionContentService.decodeAsText("plain".getBytes(StandardCharsets.UTF_8))).isEqualTo("plain");
		assertThat(SubmissionContentService.decodeAsText(new byte[0])).isEmpty();
		// A nul byte in the sniff window is the signature most binary formats share.
		assertThat(SubmissionContentService.decodeAsText(new byte[] { 'a', 0, 'b' })).isNull();
		// Invalid UTF-8: the decoder must reject it rather than emit replacement
		// characters.
		assertThat(SubmissionContentService.decodeAsText(new byte[] { 'a', (byte) 0xff, (byte) 0xfe })).isNull();
	}

	@Test
	@DisplayName("refuses a submission the service does not know")
	void refusesUnknownSubmission() {
		assertThatThrownBy(() -> this.service.readFile(UUID.randomUUID(), "src/solution.js"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private String seedRepository(String repositoryPath, String content) throws Exception {
		return seedRepository(repositoryPath, content.getBytes(StandardCharsets.UTF_8));
	}

	private String seedRepository(String repositoryPath, byte[] content) throws Exception {
		Path bare = this.repositories.resolve(repositoryPath + ".git");
		Files.createDirectories(bare.getParent());
		Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call().close();
		Path work = Files.createTempDirectory(this.root, "work-");
		try (Git git = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(work.toFile()).call()) {
			Files.createDirectories(work.resolve("src"));
			Files.writeString(work.resolve("README.md"), "# Assignment\n", StandardCharsets.UTF_8);
			Files.write(work.resolve("src").resolve("solution.js"), content);
			Path script = work.resolve("build.sh");
			Files.writeString(script, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
			Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
			git.add().addFilepattern(".").call();
			RevCommit commit = git.commit().setMessage("initial").setSign(Boolean.FALSE).call();
			git.push().setRefSpecs(new RefSpec("HEAD:refs/heads/main")).call();
			return commit.name();
		}
	}

}
