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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.submissions.web.SubmissionContentEntry;
import org.gitgrader.submissions.web.SubmissionFileContent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads submitted file trees and content from the bare Git repository at the graded
 * commit.
 *
 * <p>
 * An instructor reviews code as-is at the commit that was graded, not the current branch
 * state. Exporting by commit SHA is consistent with how the grading run is reproduced:
 * the result is always explainable from the accepted commit.
 */
@Service
public class SubmissionContentService {

	private static final Logger logger = LoggerFactory.getLogger(SubmissionContentService.class);

	/** How much of a file is inspected for a nul byte before it is called binary. */
	private static final int BINARY_SNIFF_LIMIT = 8192;

	private final SubmissionService submissions;

	private final StorageProperties storage;

	SubmissionContentService(SubmissionService submissions, StorageProperties storage) {
		this.submissions = submissions;
		this.storage = storage;
	}

	/**
	 * Lists the file tree at the graded commit of the given submission.
	 * @param submissionId submission UUID
	 * @param path optional subtree path within the repository; {@code null} or {@code ""}
	 * means the root
	 * @return non-empty list of entries; directories end with {@code /}
	 */
	public List<SubmissionContentEntry> listTree(UUID submissionId, @Nullable String path) {
		SubmissionView sub = this.submissions.findById(submissionId)
			.orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
		String repositoryPath = sub.repositoryPath();
		if (repositoryPath == null) {
			throw new IllegalArgumentException("No repository path for submission: " + submissionId);
		}
		Path bare = StorageProperties.resolveInside(this.storage.repositories(), repositoryPath + ".git");

		try (Repository repository = FileRepositoryBuilder.create(bare.toFile());
				RevWalk walk = new RevWalk(repository)) {
			ObjectId commitId = repository.resolve(sub.commitSha());
			if (commitId == null) {
				logger.warn("Cannot resolve commitSha={}, repositoryPath={}, submissionId={}", sub.commitSha(),
						repositoryPath, submissionId);
				return List.of();
			}
			return listEntries(repository, walk.parseCommit(commitId).getTree(), path);
		}
		catch (IOException ex) {
			// Repository corruption or a missing object: no partial result is usable, so
			// the failure is surfaced as a server error rather than an empty listing.
			throw new UncheckedIOException("Cannot read submitted files of submission " + submissionId, ex);
		}
	}

	/**
	 * Lists the direct children of one tree, or the tree itself when it is a file.
	 * @param repository opened bare repository
	 * @param rootTree tree of the graded commit
	 * @param path subtree path, or {@code null} for the root
	 */
	private static List<SubmissionContentEntry> listEntries(Repository repository, RevTree rootTree,
			@Nullable String path) throws IOException {
		var entries = new ArrayList<SubmissionContentEntry>();
		if (path == null || path.isEmpty()) {
			// The root tree: one direct TreeWalk over it. forPath would reject the
			// empty path with "Empty path not permitted", so no lookup is needed here.
			try (TreeWalk walk = new TreeWalk(repository)) {
				walk.addTree(rootTree);
				while (walk.next()) {
					entries.add(toEntry(repository, walk, ""));
				}
			}
			return entries;
		}
		var treeWalk = TreeWalk.forPath(repository, path, rootTree);
		if (treeWalk == null) {
			return List.of();
		}
		if (!treeWalk.isSubtree()) {
			return List.of(toEntry(repository, treeWalk, ""));
		}
		var prefix = path.endsWith("/") ? path : path + "/";
		try (TreeWalk children = new TreeWalk(repository)) {
			children.addTree(treeWalk.getObjectId(0));
			while (children.next()) {
				entries.add(toEntry(repository, children, prefix));
			}
		}
		return entries;
	}

	/**
	 * Returns the content of one file at the graded commit.
	 * @param submissionId submission UUID
	 * @param filePath path of the file within the repository, e.g. {@code src/main.js}
	 * @return the content, or a binary marker when the file is not UTF-8 text
	 */
	@SuppressWarnings("PMD.ExceptionAsFlowControl")
	public SubmissionFileContent readFile(UUID submissionId, String filePath) {
		SubmissionView sub = this.submissions.findById(submissionId)
			.orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
		String repositoryPath = sub.repositoryPath();
		if (repositoryPath == null) {
			throw new IllegalArgumentException("No repository path for submission: " + submissionId);
		}
		Path bare = StorageProperties.resolveInside(this.storage.repositories(), repositoryPath + ".git");

		try (Repository repository = FileRepositoryBuilder.create(bare.toFile());
				RevWalk walk = new RevWalk(repository)) {
			ObjectId commitId = repository.resolve(sub.commitSha());
			if (commitId == null) {
				throw new UncheckedIOException(new IOException("Commit " + sub.commitSha() + " not found"));
			}

			var treeWalk = TreeWalk.forPath(repository, filePath, walk.parseCommit(commitId).getTree());
			if (treeWalk == null) {
				throw new IOException("File not found at path: " + filePath);
			}

			ObjectLoader loader = repository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
			byte[] bytes = loader.getBytes();

			String content = decodeAsText(bytes);
			if (content == null) {
				// Binary file — return no content, only a marker.
				return new SubmissionFileContent(filePath, null, true);
			}
			return new SubmissionFileContent(filePath, content, false);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + filePath + " of submission " + submissionId, ex);
		}
	}

	/**
	 * Converts a TreeWalk entry at its current position to a
	 * {@link SubmissionContentEntry}. Directories report no size.
	 * @param repository opened bare repository
	 * @param tw walk positioned at the entry
	 * @param parentPrefix path of the tree being listed, empty for the root
	 */
	private static SubmissionContentEntry toEntry(Repository repository, TreeWalk tw, String parentPrefix)
			throws IOException {
		// The leaf name comes from getNameString(); a walk opened at a subtree reports
		// entry paths relative to that subtree, so the parent prefix restores the
		// repository-rooted path the API contract promises.
		String name = tw.getNameString();
		boolean isDir = tw.isSubtree();
		if (isDir) {
			name += "/";
		}
		Long size = isDir ? null : repository.open(tw.getObjectId(0), Constants.OBJ_BLOB).getSize();
		return new SubmissionContentEntry(parentPrefix + name, name, isDir ? "dir" : "file", size, null);
	}

	/**
	 * Attempts to decode raw bytes as UTF-8 text. Returns {@code null} if the bytes
	 * appear to be binary (a nul byte in the sniff window or invalid UTF-8).
	 */
	static @Nullable String decodeAsText(byte[] bytes) {
		if (bytes.length == 0) {
			return "";
		}
		int checkLength = Math.min(bytes.length, BINARY_SNIFF_LIMIT);
		for (int i = 0; i < checkLength; i++) {
			if (bytes[i] == 0) {
				// A nul byte anywhere in the header strongly suggests a binary format.
				return null;
			}
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		}
		catch (CharacterCodingException ex) {
			return null;
		}
	}

}
