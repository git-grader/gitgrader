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
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.templates.TemplateCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provisioning has to survive meeting a repository that is already on disk.
 *
 * <p>
 * The database row and the directory are not written together. Anything that fails after
 * the directory exists rolls the row back and leaves the directory, and a database
 * restored from a backup can be older than the volume beside it. Both leave a directory
 * with no row, which is the state this covers.
 */
class GitRepositoryProvisioningTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);

	private static final UUID ASSIGNMENT = UUID.randomUUID();

	private static final UUID STUDENT = UUID.randomUUID();

	private static final String PATH = "cs101/assignment-01/s1000";

	private GitRepositoryService service;

	private Path root;

	@BeforeEach
	void setUp(@TempDir Path tempDir) {
		this.root = tempDir.resolve("repositories");
		RepositoryRecordRepository records = mock(RepositoryRecordRepository.class);
		when(records.findByStudentIdAndAssignmentId(any(), any())).thenReturn(Optional.empty());
		when(records.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));

		StorageProperties storage = new StorageProperties(this.root.toString(), tempDir.resolve("templates").toString(),
				tempDir.resolve("tests").toString(), tempDir.resolve("artifacts").toString(),
				tempDir.resolve("tmp").toString());
		this.service = new GitRepositoryService(records, mock(TemplateCatalog.class), storage, CLOCK);
	}

	@Test
	@DisplayName("adopts a repository that is already on disk instead of failing forever")
	void adoptsAnExistingRepository() {
		RepositoryRecord first = this.service.provision(ASSIGNMENT, STUDENT, PATH, null);
		assertThat(first.repositoryPath()).isEqualTo(PATH);

		// The row is gone and the directory is not, which is what a rolled-back
		// provisioning or a restored database leaves behind. This used to throw, the
		// registration event then retried onto the same directory forever, and the
		// student
		// held a repository they could never push to.
		assertThatCode(() -> this.service.provision(ASSIGNMENT, STUDENT, PATH, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("does not seed a template over work already pushed to an adopted repository")
	void leavesAnAdoptedRepositoryWithHistoryAlone() throws IOException {
		this.service.provision(ASSIGNMENT, STUDENT, PATH, null);
		Path bare = this.root.resolve(PATH + ".git");
		String pushed = commitInto(bare);

		this.service.provision(ASSIGNMENT, STUDENT, PATH, null);

		try (Repository repository = FileRepositoryBuilder.create(bare.toFile())) {
			assertThat(repository.resolve(Constants.R_HEADS + "main").name())
				.as("an adopted repository that already holds work must not be seeded again")
				.isEqualTo(pushed);
		}
	}

	/**
	 * Writes one commit straight into a bare repository.
	 * @param bare the repository to write to
	 * @return the commit {@code refs/heads/main} now points at
	 * @throws IOException if the repository cannot be written
	 */
	private static String commitInto(Path bare) throws IOException {
		try (Repository repository = FileRepositoryBuilder.create(bare.toFile());
				ObjectInserter inserter = repository.newObjectInserter()) {
			DirCacheEntry entry = new DirCacheEntry("solution.js");
			entry.setFileMode(FileMode.REGULAR_FILE);
			entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, "work".getBytes(StandardCharsets.UTF_8)));

			DirCache index = DirCache.newInCore();
			DirCacheBuilder builder = index.builder();
			builder.add(entry);
			builder.finish();

			PersonIdent who = new PersonIdent("Student", "student@example.org", Instant.now(CLOCK), ZoneOffset.UTC);
			CommitBuilder commit = new CommitBuilder();
			commit.setTreeId(index.writeTree(inserter));
			commit.setAuthor(who);
			commit.setCommitter(who);
			commit.setMessage("work");
			ObjectId commitId = inserter.insert(commit);
			inserter.flush();

			RefUpdate update = repository.updateRef(Constants.R_HEADS + "main");
			update.setNewObjectId(commitId);
			update.forceUpdate();
			return commitId.name();
		}
	}

}
