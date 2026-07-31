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

package org.gitgrader.templates.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Atomically extracts bounded, regular-file-only ZIP uploads. */
@Component
public class SecureZipExtractor {

	private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L;

	private static final int MAX_ENTRIES = 2_000;

	private static final long MAX_COMPRESSION_RATIO = 100L;

	private static final int COPY_BUFFER_BYTES = 8_192;

	/**
	 * Extracts a ZIP into the destination without leaving partial content on failure.
	 * @param upload uploaded ZIP
	 * @param destination final destination directory
	 */
	public void extract(MultipartFile upload, Path destination) {
		Path parent = destination.getParent();
		if (parent == null) {
			throw new ArchiveUploadException("The archive destination is invalid.");
		}
		Path temporary = null;
		Path archive = null;
		try {
			Files.createDirectories(parent);
			temporary = Files.createTempDirectory(parent, ".upload-");
			archive = Files.createTempFile(parent, ".archive-", ".zip");
			upload.transferTo(archive);
			extractArchive(archive, temporary);
			grantSandboxAccess(temporary);
			if (Files.exists(destination)) {
				throw new ArchiveUploadException("A version with this label already exists.");
			}
			moveAtomically(temporary, destination);
			temporary = null;
		}
		catch (IOException exception) {
			throw new ArchiveUploadException("The ZIP archive could not be extracted.", exception);
		}
		finally {
			delete(temporary);
			delete(archive);
		}
	}

	// Extraction happens in a temporary directory, and the JDK creates those readable
	// only by their owner. The grading sandbox mounts a published test suite and runs as
	// a different user, so content left at 0700 is invisible to it: the tests never run,
	// the submission scores zero, and nothing says why. Directories therefore have to be
	// traversable and files readable, exactly as staged sample content already is.
	private static void grantSandboxAccess(Path root) throws IOException {
		if (!root.getFileSystem().supportedFileAttributeViews().contains("posix")) {
			return;
		}
		Set<PosixFilePermission> directoryPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
		Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString("rw-r--r--");
		List<Path> paths;
		try (Stream<Path> walk = Files.walk(root)) {
			paths = walk.toList();
		}
		for (Path path : paths) {
			Files.setPosixFilePermissions(path, Files.isDirectory(path) ? directoryPermissions : filePermissions);
		}
	}

	private static void extractArchive(Path archive, Path temporary) throws IOException {
		try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
			Enumeration<ZipArchiveEntry> entries = zip.getEntries();
			long total = 0;
			int count = 0;
			while (entries.hasMoreElements()) {
				ZipArchiveEntry entry = entries.nextElement();
				count++;
				if (count > MAX_ENTRIES) {
					throw new ArchiveUploadException("The ZIP archive contains more than 2000 entries.");
				}
				Path output = ZipEntrySafety.resolve(entry, temporary);
				if (entry.isDirectory()) {
					Files.createDirectories(output);
					continue;
				}
				Files.createDirectories(java.util.Objects.requireNonNull(output.getParent()));
				try (InputStream input = zip.getInputStream(entry)) {
					total = copyBounded(input, output, total);
				}
				validateCompressionRatio(entry, Files.size(output));
			}
		}
	}

	private static long copyBounded(InputStream input, Path output, long total) throws IOException {
		byte[] buffer = new byte[COPY_BUFFER_BYTES];
		long updated = total;
		try (var stream = Files.newOutputStream(output)) {
			int read = input.read(buffer);
			while (read != -1) {
				updated += read;
				if (updated > MAX_UNCOMPRESSED_BYTES) {
					throw new ArchiveUploadException("The ZIP archive expands beyond 50 MB.");
				}
				stream.write(buffer, 0, read);
				read = input.read(buffer);
			}
		}
		return updated;
	}

	private static void validateCompressionRatio(ZipArchiveEntry entry, long uncompressedSize) {
		long compressedSize = entry.getCompressedSize();
		if (uncompressedSize > 0
				&& (compressedSize <= 0 || uncompressedSize > compressedSize * MAX_COMPRESSION_RATIO)) {
			throw new ArchiveUploadException("The ZIP archive exceeds the 100:1 compression ratio limit.");
		}
	}

	private static void moveAtomically(Path source, Path destination) throws IOException {
		Files.move(source, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}

	static void delete(Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try {
			Files.walkFileTree(path, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
					Files.deleteIfExists(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
					Files.deleteIfExists(directory);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ignored) {
			// Cleanup is best effort; the upload failure remains the actionable
			// exception.
		}
	}

}
