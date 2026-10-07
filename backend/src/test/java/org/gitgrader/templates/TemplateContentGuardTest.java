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

package org.gitgrader.templates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.gitgrader.configuration.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TemplateContentGuard}, which vets an uploaded template before it can
 * be published.
 *
 * <p>
 * A template is instructor-supplied and lands in the grading sandbox beside the hidden
 * suite, so a reference solution, an answer key, a private key or a {@code .env} that
 * travelled in with the archive has to be refused. The template key is resolved through
 * {@link StorageProperties#resolveInside} for the same reason: it becomes a filesystem
 * path segment, and {@code ../} must not reach the hidden tests.
 *
 * <p>
 * The allow-list matters as much as the deny-list. An earlier revision refused
 * {@code solution.js}, which an ordinary assignment template legitimately contains, and a
 * guard that blocks normal work is a guard an operator turns off.
 */
class TemplateContentGuardTest {

	@TempDir
	private Path directory;

	@ParameterizedTest
	@DisplayName("rejects every hidden-test and secret pattern")
	@MethodSource("forbiddenPaths")
	void rejectsEveryHiddenTestAndSecretPattern(String relativePath) throws IOException {
		Path file = this.directory.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "secret");

		assertThatThrownBy(() -> new TemplateContentGuard().validate(this.directory))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("forbidden");
	}

	@Test
	@DisplayName("allows the public key variant of a rejected substring")
	void publicKeyVariantIsAllowed() throws IOException {
		Files.writeString(this.directory.resolve("id_ed25519.pub"), "public");
		new TemplateContentGuard().validate(this.directory);
	}

	@Test
	@DisplayName("refuses a template key that escapes the storage root")
	void templateKeyCannotEscapeTheStorageRoot() {
		assertThatThrownBy(() -> StorageProperties.resolveInside(this.directory, "../hidden-tests"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("escapes");
	}

	@ParameterizedTest
	@DisplayName("allows ordinary template content")
	@MethodSource("allowedPaths")
	void allowsOrdinaryTemplateContent(String relativePath) throws IOException {
		Path file = this.directory.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "stub");

		new TemplateContentGuard().validate(this.directory);
	}

	private static Stream<String> forbiddenPaths() {
		return Stream.of("hidden", "hiddenCase/test.txt", "src/my-hidden-tests/test.txt", ".secret-token",
				"reference-solution.java", "solution/answers.js", "solutions/answers.js", "solution.md", "solution.pdf",
				"docs/answer-key.txt", "manifest.json", ".env", "certificate.pem", "private.key", "id_rsa",
				"id_rsa_backup", "id_ed25519", "id_ed25519-old");
	}

	/**
	 * Layouts an ordinary assignment template legitimately uses.
	 *
	 * <p>
	 * A stub the student fills in is very often called {@code solution.js}. An earlier
	 * revision of the guard refused exactly that, which would have made the guard the
	 * first thing an operator turned off.
	 * @return template-relative paths that must be accepted
	 */
	private static Stream<String> allowedPaths() {
		return Stream.of("src/solution.js", "solution.js", "solution.ts", "src/index.js", "public-tests/basic.test.js",
				"package.json", "src/manifest.json", "README.md", "id_ed25519.pub");
	}

}
