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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import org.gitgrader.git.RepositoryStatus;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.templates.TemplateCatalog;
import org.gitgrader.templates.TemplateVersionView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and resolves the bare repositories students clone and push to.
 *
 * <p>
 * Every filesystem path this class produces is built by resolving a stored value inside
 * the configured repository root through {@link StorageProperties#resolveInside}. The
 * path an SSH client asks for is never turned into a filesystem path directly: it is
 * looked up in the {@code repositories} table first, and only the stored value is
 * resolved. That ordering is what makes a crafted request unable to escape the root.
 *
 * <p>
 * Staying inside the root is not on its own enough to keep students apart, because two
 * different stored values can denote one directory. {@link #repositoryPathFor} is
 * therefore the second half of the guarantee: it admits only segments that survive
 * normalisation unchanged, so one stored path means one repository and one student.
 */
@Service
@Transactional
public class GitRepositoryService {

	private static final Logger logger = LoggerFactory.getLogger(GitRepositoryService.class);

	/** Default branch every provisioned repository starts on. */
	private static final String DEFAULT_BRANCH_REF = "refs/heads/main";

	/** Author recorded on the generated initial commit. */
	private static final String TEMPLATE_AUTHOR_NAME = "GitGrader";

	/** Address recorded on the generated initial commit; never used for mail. */
	private static final String TEMPLATE_AUTHOR_EMAIL = "noreply@localhost";

	/** One path segment that normalisation cannot turn into a different directory. */
	private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

	private final RepositoryRecordRepository repositories;

	private final TemplateCatalog templateCatalog;

	private final StorageProperties storage;

	private final Clock clock;

	public GitRepositoryService(RepositoryRecordRepository repositories, TemplateCatalog templateCatalog,
			StorageProperties storage, Clock clock) {
		this.repositories = repositories;
		this.templateCatalog = templateCatalog;
		this.storage = storage;
		this.clock = clock;
	}

	/**
	 * Builds the canonical repository path for one student and assignment.
	 * @param courseKey the course key
	 * @param assignmentKey the assignment key
	 * @param studentNumber the student number
	 * @return path relative to the repository root, without a {@code .git} suffix
	 */
	public static String repositoryPathFor(String courseKey, String assignmentKey, String studentNumber) {
		requireSafeSegment(courseKey, "course key");
		requireSafeSegment(assignmentKey, "assignment key");
		requireSafeSegment(studentNumber, "student number");
		return courseKey + "/" + assignmentKey + "/" + studentNumber;
	}

	/**
	 * Rejects a value that would denote anything other than one directory of its own.
	 *
	 * <p>
	 * The student number arrives from the public registration form, which constrains only
	 * its length, and is concatenated into a path that is normalised later, when it is
	 * resolved on disk. A number carrying a separator therefore used to be able to name
	 * another student's repository: under assignment {@code a1}, the number
	 * {@code ../a1/victim} normalises to the same directory as the plain number
	 * {@code victim}, while the two stored strings differ and so both satisfy the unique
	 * index. Each student then authenticated with their own key against their own row and
	 * arrived at one shared bare repository.
	 *
	 * <p>
	 * Containment alone cannot catch that, because such a path never leaves the
	 * repository root. Excluding the separators is what makes a segment unable to denote
	 * anything but itself, and {@code .} and {@code ..} are excluded outright because
	 * they denote a directory that already exists.
	 * @param value the segment to check
	 * @param description what the segment is, for the rejection message
	 */
	private static void requireSafeSegment(String value, String description) {
		if (!SAFE_PATH_SEGMENT.matcher(value).matches() || ".".equals(value) || "..".equals(value)) {
			throw new IllegalArgumentException(
					"Unusable " + description + ": only letters, digits, '.', '_' and '-' are allowed");
		}
	}

	/**
	 * Resolves an SSH-requested repository path to a registered repository.
	 *
	 * <p>
	 * Tolerates the {@code .git} suffix and any leading slash, because different clients
	 * send different shapes, but performs the lookup on the normalised value only.
	 * @param requestedPath the raw path from the SSH command line
	 * @return the registered repository, when the path matches one exactly
	 */
	@Transactional(readOnly = true)
	public Optional<RepositoryRecord> resolve(String requestedPath) {
		return this.repositories.findByRepositoryPath(normalise(requestedPath));
	}

	/**
	 * Absolute location of a repository's bare directory on disk.
	 * @param record the registered repository
	 * @return absolute path inside the configured repository root
	 */
	public Path bareDirectory(RepositoryRecord record) {
		return StorageProperties.resolveInside(this.storage.repositories(), record.repositoryPath() + ".git");
	}

	/**
	 * Creates the repository row and its bare directory, seeded from a template version.
	 *
	 * <p>
	 * Idempotent: a student who registers twice, or an instructor who re-runs
	 * provisioning, gets the repository that already exists rather than a second one.
	 * @param assignmentId the assignment
	 * @param studentId the student
	 * @param repositoryPath canonical path from {@link #repositoryPathFor}
	 * @param templateVersionId the template version to seed from, or {@code null} for an
	 * empty repository
	 * @return the provisioned repository
	 */
	public RepositoryRecord provision(UUID assignmentId, UUID studentId, String repositoryPath,
			@Nullable UUID templateVersionId) {
		Optional<RepositoryRecord> existing = this.repositories.findByStudentIdAndAssignmentId(studentId, assignmentId);
		if (existing.isPresent()) {
			return existing.get();
		}

		RepositoryRecord record = this.repositories
			.save(new RepositoryRecord(assignmentId, studentId, normalise(repositoryPath), this.clock));
		Path bare = bareDirectory(record);
		Path bareParent = bare.getParent();
		if (bareParent == null) {
			throw new IllegalStateException("Resolved repository path has no parent: " + bare);
		}
		try {
			Files.createDirectories(bareParent);
			// The database row and the directory are not written atomically: anything
			// that
			// fails after the directory exists rolls the row back and leaves the
			// directory
			// behind, and a restored database can be older than the volume beside it.
			// Refusing an existing directory made both cases permanent - every retry of
			// the registration event failed on the same directory, and the student was
			// left with a repository they could never push to.
			boolean adopted = Files.isDirectory(bare);
			try (Repository repository = FileRepositoryBuilder.create(bare.toFile())) {
				if (!adopted) {
					repository.create(true);
				}
				// JGit still defaults a new repository's HEAD to "master". Students and
				// the push admission rules both work in terms of "main", so the default
				// branch is pinned here rather than left to the JGit version in use.
				RefUpdate head = repository.updateRef(Constants.HEAD, true);
				head.disableRefLog();
				head.link(DEFAULT_BRANCH_REF);
			}
			// An adopted repository is only seeded when it holds nothing, so a retry
			// cannot write a second template commit over work a student has pushed.
			if (!adopted || isEmpty(bare)) {
				seedFromTemplate(bare, templateVersionId);
			}
			else {
				logger.info("Adopted the repository already present at {}", bare);
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not provision repository at " + bare, ex);
		}

		record.markProvisioned(templateVersionId, this.clock);
		logger.info("Provisioned repository {} for student {} on assignment {}", record.repositoryPath(), studentId,
				assignmentId);
		return this.repositories.save(record);
	}

	/**
	 * Records that a push was accepted into a repository.
	 * @param record the repository
	 */
	public void recordPush(RepositoryRecord record) {
		record.recordPush(this.clock);
		this.repositories.save(record);
	}

	/**
	 * Whether the repository is provisioned and not locked or archived.
	 * @param record the repository
	 * @return true when a push may proceed on repository grounds alone
	 */
	public boolean acceptsPushes(RepositoryRecord record) {
		return record.status() == RepositoryStatus.READY;
	}

	/**
	 * Whether a bare repository holds no refs at all.
	 * @param bare the bare repository directory
	 * @return true when nothing has been committed or pushed to it
	 * @throws IOException if the repository cannot be read
	 */
	private static boolean isEmpty(Path bare) throws IOException {
		try (Repository repository = FileRepositoryBuilder.create(bare.toFile())) {
			return repository.getRefDatabase().getRefs().isEmpty();
		}
	}

	/**
	 * Copies a published template version into a freshly created bare repository.
	 *
	 * <p>
	 * The template is committed through a temporary work tree rather than written into
	 * the bare repository directly, because a bare repository has no index to add files
	 * to. The work tree is removed afterwards so no student-visible content is left on a
	 * server path.
	 * @param bare the bare repository directory
	 * @param templateVersionId the version to seed from, or {@code null} to leave empty
	 * @throws IOException if the filesystem operations fail
	 * @throws GitAPIException if the initial commit cannot be created
	 */
	private void seedFromTemplate(Path bare, @Nullable UUID templateVersionId) throws IOException {
		if (templateVersionId == null) {
			return;
		}
		Optional<TemplateVersionView> version = this.templateCatalog.findVersion(templateVersionId);
		if (version.isEmpty()) {
			logger.warn("Template version {} not found; repository left empty", templateVersionId);
			return;
		}

		Path source = StorageProperties.resolveInside(this.storage.templates(), version.get().storagePath());
		if (!Files.isDirectory(source)) {
			logger.warn("Template content missing at {}; repository left empty", source);
			return;
		}

		try (Repository repository = FileRepositoryBuilder.create(bare.toFile());
				ObjectInserter inserter = repository.newObjectInserter()) {
			DirCache index = DirCache.newInCore();
			DirCacheBuilder builder = index.builder();
			addTemplateFiles(source, source, builder, inserter);
			builder.finish();

			ObjectId treeId = index.writeTree(inserter);
			CommitBuilder commit = new CommitBuilder();
			commit.setTreeId(treeId);
			PersonIdent author = new PersonIdent(TEMPLATE_AUTHOR_NAME, TEMPLATE_AUTHOR_EMAIL, Instant.now(this.clock),
					ZoneOffset.UTC);
			commit.setAuthor(author);
			commit.setCommitter(author);
			commit.setMessage("Initial assignment template\n");

			ObjectId commitId = inserter.insert(commit);
			inserter.flush();

			RefUpdate update = repository.updateRef(DEFAULT_BRANCH_REF);
			update.setNewObjectId(commitId);
			update.setRefLogMessage("initial template", false);
			RefUpdate.Result result = update.forceUpdate();
			if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED) {
				throw new IOException("Could not create the initial template commit: " + result);
			}
		}
	}

	/**
	 * Inserts every template file as a blob and stages it in an in-core index.
	 *
	 * <p>
	 * Writing straight into the bare repository avoids checking the template out to a
	 * work tree. Beyond being faster, it removes the case that actually broke here: a
	 * clone of a still-empty repository has no upstream branch, so the follow-up push
	 * silently sent nothing and the student received an empty repository.
	 * @param root the template root, used to compute repository-relative paths
	 * @param current the directory being visited
	 * @param builder the in-core index being populated
	 * @param inserter the object inserter
	 * @throws IOException if a file cannot be read or stored
	 */
	private void addTemplateFiles(Path root, Path current, DirCacheBuilder builder, ObjectInserter inserter)
			throws IOException {
		try (Stream<Path> entries = Files.list(current)) {
			for (Path entry : entries.sorted().toList()) {
				if (Files.isDirectory(entry)) {
					addTemplateFiles(root, entry, builder, inserter);
					continue;
				}
				byte[] content = Files.readAllBytes(entry);
				DirCacheEntry indexEntry = new DirCacheEntry(root.relativize(entry).toString().replace('\\', '/'));
				indexEntry.setFileMode(Files.isExecutable(entry) ? FileMode.EXECUTABLE_FILE : FileMode.REGULAR_FILE);
				indexEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content));
				builder.add(indexEntry);
			}
		}
	}

	private static String normalise(String requestedPath) {
		String value = requestedPath.trim();
		while (value.startsWith("/")) {
			value = value.substring(1);
		}
		if (value.endsWith(".git")) {
			value = value.substring(0, value.length() - ".git".length());
		}
		return value;
	}

}
