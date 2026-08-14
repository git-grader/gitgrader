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
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.gitgrader.configuration.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Materialises the exact commit under grading into a scratch directory.
 *
 * <p>
 * The commit tree is exported directly from the bare repository rather than by checking
 * out a branch. That matters for correctness: a student who pushes again while an earlier
 * run is queued must not have the newer code graded under the older submission's
 * identity. Exporting by commit id makes the run reproducible no matter what happened to
 * the branch afterwards.
 *
 * <p>
 * Nothing from the repository's history is copied - only the tree at that one commit - so
 * the sandbox never sees a {@code .git} directory it could use to reach other refs.
 */
@Component
public class GradingWorkspaceFactory {

	private static final Logger logger = LoggerFactory.getLogger(GradingWorkspaceFactory.class);

	private final StorageProperties storage;

	public GradingWorkspaceFactory(StorageProperties storage) {
		this.storage = storage;
	}

	/**
	 * Exports one commit into a fresh directory.
	 * @param repositoryPath repository path relative to the repository root
	 * @param commitSha the commit to export
	 * @return the populated workspace directory
	 * @throws IOException if the repository or the commit cannot be read
	 */
	public Path materialise(String repositoryPath, String commitSha) throws IOException {
		Path bare = StorageProperties.resolveInside(this.storage.repositories(), repositoryPath + ".git");
		Files.createDirectories(this.storage.temp());
		Path workspace = Files.createTempDirectory(this.storage.temp(), "gitgrader-run-");

		try {
			exportInto(bare, repositoryPath, commitSha, workspace);
		}
		catch (IOException | RuntimeException ex) {
			// A workspace that failed to build is never returned, so the caller has no
			// path to clean up and the half-written copy would sit in the temp directory
			// until someone noticed. A commit that is simply gone is enough to reach
			// here, and it repeats on every retry.
			discard(workspace);
			throw ex;
		}

		logger.debug("Materialised {}@{} into {}", repositoryPath, commitSha, workspace);
		return workspace;
	}

	private void exportInto(Path bare, String repositoryPath, String commitSha, Path workspace) throws IOException {
		try (Repository repository = FileRepositoryBuilder.create(bare.toFile());
				RevWalk walk = new RevWalk(repository);
				TreeWalk treeWalk = new TreeWalk(repository)) {
			ObjectId commitId = repository.resolve(commitSha);
			if (commitId == null) {
				throw new IOException("Commit " + commitSha + " not found in " + repositoryPath);
			}
			try {
				treeWalk.addTree(walk.parseCommit(commitId).getTree());
			}
			catch (MissingObjectException ex) {
				// resolve() accepts any well-formed hash, so a syntactically valid commit
				// that was never pushed only fails here. JGit's own message names the
				// object but not the repository, which is the part an operator needs.
				throw new IOException("Commit " + commitSha + " not found in " + repositoryPath, ex);
			}
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				writeEntry(repository, treeWalk, workspace);
			}
		}
		openToTheSandboxUser(workspace);
	}

	/**
	 * Widens the workspace so the grading sandbox can read and write it.
	 *
	 * <p>
	 * The sandbox deliberately runs as an unprivileged user unrelated to this process,
	 * and a directory created here is private to its owner, so the sandbox cannot read a
	 * single file of it. That surfaces as a run in which no test executes at all: the
	 * suite fails to load the submitted code and reports nothing, which is recorded as a
	 * score of zero rather than as an error, so a correct submission is failed silently.
	 *
	 * <p>
	 * The application cannot chown to the sandbox user, having no privilege to give files
	 * away, so access is granted by mode instead. Read and traverse only: the sandbox has
	 * to load the submission, not modify it, and the content is the student's own work
	 * inside a per-run directory on a private volume. A runtime whose install or test
	 * command needs to write should be given a writable mount of its own rather than
	 * having this opened up.
	 * @param workspace the populated workspace
	 * @throws IOException if the permissions cannot be applied
	 */
	private static void openToTheSandboxUser(Path workspace) throws IOException {
		try (Stream<Path> paths = Files.walk(workspace)) {
			for (Path path : paths.toList()) {
				Files.setPosixFilePermissions(path, Files.isDirectory(path)
						? PosixFilePermissions.fromString("rwxrwxrwx") : PosixFilePermissions.fromString("rw-r--r--"));
			}
		}
	}

	/**
	 * Removes a workspace once the run is finished.
	 *
	 * <p>
	 * The sandbox mounts this directory writable and runs as its own user, so it can
	 * leave behind a directory this process is not permitted to enter - one {@code mkdir}
	 * and {@code chmod 000} is enough. A single walk over the tree gives up at the first
	 * such directory <em>before</em> deleting anything, so what leaked was not the
	 * offending directory but the whole workspace, including the full copy of the
	 * student's repository, on every run of that submission.
	 *
	 * <p>
	 * Deleting depth-first and carrying on past a subtree that cannot be read bounds that
	 * to the directory actually responsible. Symbolic links are removed rather than
	 * followed, so a link the sandbox pointed outside the workspace deletes the link and
	 * nothing else.
	 * @param workspace the directory to remove
	 */
	public void discard(Path workspace) {
		List<Path> unremovable = new ArrayList<>();
		deleteTree(workspace, unremovable, true);
		if (!unremovable.isEmpty()) {
			// Student content is untrusted, so a leftover is worth a warning, but failing
			// the run over it would turn a cleanup problem into a bad grade.
			logger.warn("Could not fully remove grading workspace {}; {} path(s) remain, starting with {}", workspace,
					unremovable.size(), unremovable.getFirst());
		}
	}

	/**
	 * Deletes one path, and everything below it when it is a real directory.
	 * @param path the path to remove
	 * @param unremovable collects whatever could not be removed
	 * @param mayWiden whether a directory that cannot be listed may be reopened by
	 * widening its permissions, which succeeds only where this process owns it
	 */
	private static void deleteTree(Path path, List<Path> unremovable, boolean mayWiden) {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
				for (Path child : children) {
					deleteTree(child, unremovable, true);
				}
			}
			catch (IOException ex) {
				if (mayWiden && widen(path)) {
					deleteTree(path, unremovable, false);
				}
				else {
					unremovable.add(path);
				}
				return;
			}
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			unremovable.add(path);
		}
	}

	private static boolean widen(Path directory) {
		try {
			Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
			return true;
		}
		catch (IOException | UnsupportedOperationException ex) {
			return false;
		}
	}

	private void writeEntry(Repository repository, TreeWalk treeWalk, Path workspace) throws IOException {
		Path target = StorageProperties.resolveInside(workspace, treeWalk.getPathString());
		Path parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		ObjectLoader loader = repository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
		try (OutputStream out = Files.newOutputStream(target)) {
			loader.copyTo(out);
		}
	}

}
