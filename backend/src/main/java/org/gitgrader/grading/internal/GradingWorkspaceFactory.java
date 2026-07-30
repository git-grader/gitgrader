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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
		logger.debug("Materialised {}@{} into {}", repositoryPath, commitSha, workspace);
		return workspace;
	}

	/**
	 * Removes a workspace once the run is finished.
	 * @param workspace the directory to remove
	 */
	public void discard(Path workspace) {
		try (Stream<Path> paths = Files.walk(workspace)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
		catch (IOException ex) {
			// Student content is untrusted, so a leftover workspace is worth a warning,
			// but failing the run over it would turn a cleanup problem into a bad grade.
			logger.warn("Could not remove grading workspace {}", workspace, ex);
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
