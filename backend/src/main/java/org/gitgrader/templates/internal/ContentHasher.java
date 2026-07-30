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

package org.gitgrader.templates.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** Computes deterministic SHA-256 metadata over sorted relative paths and file bytes. */
final class ContentHasher {

	private static final int LENGTH_BYTES = Long.BYTES;

	private ContentHasher() {
	}

	static ContentSnapshot snapshot(Path directory) {
		try {
			List<Path> files = regularFiles(directory);
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			long totalBytes = updateDigest(directory, files, digest);
			return new ContentSnapshot(HexFormat.of().formatHex(digest.digest()), files.size(), totalBytes);
		}
		catch (IOException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Could not hash publication content", exception);
		}
	}

	private static List<Path> regularFiles(Path directory) throws IOException {
		try (Stream<Path> paths = Files.walk(directory)) {
			return paths.filter(Files::isRegularFile)
				.sorted(Comparator.comparing((path) -> directory.relativize(path).toString()))
				.toList();
		}
	}

	private static long updateDigest(Path directory, List<Path> files, MessageDigest digest) throws IOException {
		long totalBytes = 0;
		for (Path file : files) {
			byte[] pathBytes = directory.relativize(file)
				.toString()
				.replace('\\', '/')
				.getBytes(StandardCharsets.UTF_8);
			byte[] content = Files.readAllBytes(file);
			updateLengthAndBytes(digest, pathBytes);
			updateLengthAndBytes(digest, content);
			totalBytes += content.length;
		}
		return totalBytes;
	}

	private static void updateLengthAndBytes(MessageDigest digest, byte[] bytes) {
		digest.update(ByteBuffer.allocate(LENGTH_BYTES).putLong(bytes.length).array());
		digest.update(bytes);
	}

}
