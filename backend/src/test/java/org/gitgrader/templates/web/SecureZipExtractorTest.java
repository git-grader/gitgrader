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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureZipExtractorTest {

	@TempDir
	private Path temporaryDirectory;

	@Test
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
