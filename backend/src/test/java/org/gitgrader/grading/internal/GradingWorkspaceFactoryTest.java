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

package org.gitgrader.grading.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gitgrader.configuration.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link GradingWorkspaceFactory} against a real repository.
 *
 * <p>
 * Exercises real Git objects rather than stubs, because the whole job of this class is to
 * turn a commit id into files on disk. The property that matters most is asserted
 * directly: grading sees the code at <em>that</em> commit, not whatever the branch points
 * at now.
 */
class GradingWorkspaceFactoryTest {

	@TempDir
	private Path root;

	private GradingWorkspaceFactory factory;

	private Path repositories;

	@BeforeEach
	void setUp() throws IOException {
		this.repositories = Files.createDirectories(this.root.resolve("repositories"));
		Path temp = Files.createDirectories(this.root.resolve("tmp"));
		StorageProperties storage = new StorageProperties(this.repositories.toString(),
				this.root.resolve("templates").toString(), this.root.resolve("tests").toString(),
				this.root.resolve("artifacts").toString(), temp.toString());
		this.factory = new GradingWorkspaceFactory(storage);
	}

	@Test
	@DisplayName("exports the tree of one commit, including nested directories")
	void exportsCommitTree() throws Exception {
		String commit = seedRepository("course/assignment/student", "export const answer = 42;");

		Path workspace = this.factory.materialise("course/assignment/student", commit);

		assertThat(workspace.resolve("README.md")).exists();
		assertThat(workspace.resolve("src").resolve("solution.js")).hasContent("export const answer = 42;");
	}

	@Test
	@DisplayName("grades the commit that was submitted, not the current branch tip")
	void exportsTheRequestedCommitNotTheTip() throws Exception {
		// The case this guards: a student pushes again while an earlier run is still
		// queued. Grading the tip would score the newer code under the older submission.
		String firstCommit = seedRepository("course/assignment/racer", "export const answer = 1;");
		amend("course/assignment/racer", "export const answer = 999;");

		Path workspace = this.factory.materialise("course/assignment/racer", firstCommit);

		assertThat(workspace.resolve("src").resolve("solution.js")).hasContent("export const answer = 1;");
	}

	@Test
	@DisplayName("never copies the repository's own git metadata into the sandbox")
	void doesNotExposeGitMetadata() throws Exception {
		String commit = seedRepository("course/assignment/clean", "export const answer = 42;");

		Path workspace = this.factory.materialise("course/assignment/clean", commit);

		// Untrusted code must not be handed a .git directory: it would expose every other
		// ref and object in the student's repository to the sandbox.
		assertThat(workspace.resolve(".git")).doesNotExist();
	}

	@Test
	@DisplayName("refuses a commit that is not in the repository")
	void refusesUnknownCommit() throws Exception {
		seedRepository("course/assignment/missing", "export const answer = 42;");

		assertThatExceptionOfType(IOException.class)
			.isThrownBy(() -> this.factory.materialise("course/assignment/missing", "0".repeat(40)))
			.withMessageContaining("not found");
	}

	@Test
	@DisplayName("removes a workspace once the run is finished")
	void discardsWorkspace() throws Exception {
		String commit = seedRepository("course/assignment/discard", "export const answer = 42;");
		Path workspace = this.factory.materialise("course/assignment/discard", commit);
		assertThat(workspace).exists();

		this.factory.discard(workspace);

		assertThat(workspace).doesNotExist();
	}

	private String seedRepository(String repositoryPath, String content) throws Exception {
		Path bare = this.repositories.resolve(repositoryPath + ".git");
		Files.createDirectories(bare.getParent());
		try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call()) {
			// The bare repository only needs to exist; content arrives via the clone
			// below.
		}
		return commitInto(bare, repositoryPath, content, false);
	}

	private void amend(String repositoryPath, String content) throws Exception {
		commitInto(this.repositories.resolve(repositoryPath + ".git"), repositoryPath, content, true);
	}

	private String commitInto(Path bare, String repositoryPath, String content, boolean second)
			throws GitAPIException, IOException {
		Path work = Files.createTempDirectory(this.root, "work-");
		try (Git git = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(work.toFile()).call()) {
			Files.createDirectories(work.resolve("src"));
			Files.writeString(work.resolve("README.md"), "# Assignment\n", StandardCharsets.UTF_8);
			Files.writeString(work.resolve("src").resolve("solution.js"), content, StandardCharsets.UTF_8);
			git.add().addFilepattern(".").call();
			RevCommit commit = git.commit().setMessage(second ? "update" : "initial").setSign(Boolean.FALSE).call();
			git.push().setRefSpecs(new org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/main")).call();
			return commit.name();
		}
	}

}
