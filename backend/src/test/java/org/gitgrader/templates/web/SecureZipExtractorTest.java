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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SecureZipExtractor}, which unpacks an instructor's uploaded archive
 * into server-owned storage.
 *
 * <p>
 * An entry name is attacker-controlled and becomes a filesystem path, so a traversing
 * name is refused rather than normalised. Each refusal also asserts that the destination
 * does not exist afterwards, because a half-extracted template is a directory an operator
 * could mistake for a published version, and an entry-count overrun has already spent the
 * disk by the time it is noticed.
 *
 * <p>
 * The permissive cases are load-bearing too: an archive recording only permission bits
 * has no file-type bits and must still extract, and the result must be readable by the
 * grading sandbox, which runs as a different user from the application.
 */
class SecureZipExtractorTest {

	@TempDir
	private Path temporaryDirectory;

	@Test
	@DisplayName("rejects a zip-slip entry without leaving a partial directory")
	void zipSlipEntryIsRejectedWithoutPartialDirectory() throws IOException {
		Path destination = this.temporaryDirectory.resolve("template/version");
		MockMultipartFile upload = new MockMultipartFile("file", "unsafe.zip", "application/zip",
				zip("../outside.txt", "secret"));

		assertThatThrownBy(() -> new SecureZipExtractor().extract(upload, destination))
			.isInstanceOf(ArchiveUploadException.class)
			.hasMessageContaining("unsafe path");
		assertThat(destination).doesNotExist();
		assertThat(this.temporaryDirectory.resolve("template/outside.txt")).doesNotExist();
	}

	@Test
	@DisplayName("rejects an archive with too many entries without leaving a partial directory")
	void tooManyEntriesAreRejectedWithoutPartialDirectory() throws IOException {
		Path destination = this.temporaryDirectory.resolve("template/version");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (int index = 0; index <= 2_000; index++) {
				zip.putNextEntry(new ZipEntry("entry-" + index));
				zip.closeEntry();
			}
		}
		MockMultipartFile upload = new MockMultipartFile("file", "large.zip", "application/zip", bytes.toByteArray());

		assertThatThrownBy(() -> new SecureZipExtractor().extract(upload, destination))
			.isInstanceOf(ArchiveUploadException.class)
			.hasMessageContaining("2000 entries");
		assertThat(destination).doesNotExist();
	}

	@Test
	@DisplayName("extracts an entry whose permission bits lack the file-type bits")
	void entryCarryingPermissionBitsWithoutFileTypeBitsIsExtracted() throws IOException {
		Path destination = this.temporaryDirectory.resolve("template/version");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
			ZipArchiveEntry entry = new ZipArchiveEntry("README.md");
			entry.setUnixMode(0600);
			zip.putArchiveEntry(entry);
			zip.write("hello".getBytes(StandardCharsets.UTF_8));
			zip.closeArchiveEntry();
		}
		MockMultipartFile upload = new MockMultipartFile("file", "python.zip", "application/zip", bytes.toByteArray());

		new SecureZipExtractor().extract(upload, destination);

		assertThat(destination.resolve("README.md")).hasContent("hello");
	}

	@Test
	@DisplayName("extracts content the grading sandbox can read")
	void extractedContentIsReadableByTheGradingSandbox() throws IOException {
		Path destination = this.temporaryDirectory.resolve("suite/v1");
		MockMultipartFile upload = new MockMultipartFile("file", "suite.zip", "application/zip",
				zip("hidden.test.js", "assert(true)"));

		new SecureZipExtractor().extract(upload, destination);

		assertThat(java.nio.file.Files.getPosixFilePermissions(destination)).contains(PosixFilePermission.OTHERS_READ,
				PosixFilePermission.OTHERS_EXECUTE);
		assertThat(java.nio.file.Files.getPosixFilePermissions(destination.resolve("hidden.test.js")))
			.contains(PosixFilePermission.OTHERS_READ);
	}

	private static byte[] zip(String name, String content) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry(name));
			zip.write(content.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

}
