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
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

/**
 * Refuses to publish a student-visible template that appears to contain secrets.
 *
 * <p>
 * Templates are cloned by students; hidden test suites are not. Those two live under
 * different storage roots precisely so that the separation is structural rather than a
 * matter of remembering. This guard is the last check before publication, for the case
 * where somebody assembled a template by copying a working directory that still had the
 * hidden suite, a solution or a key sitting in it.
 *
 * <p>
 * It is deliberately conservative: a false rejection costs an instructor one rename,
 * while a false acceptance hands the entire course the answers.
 */
@Component
public class TemplateContentGuard {

	/** Document extensions that would carry a written-out answer. */
	private static final List<String> ANSWER_DOCUMENT_EXTENSIONS = List.of(".md", ".txt", ".pdf", ".doc", ".docx",
			".rtf", ".html");

	/**
	 * Everything that disqualifies a path from a student-visible template.
	 *
	 * <p>
	 * Expressed as a named list rather than one long boolean expression so each rule can
	 * be read and extended on its own. The first match wins and the scan stops.
	 *
	 * <p>
	 * <strong>Why "solution" is not simply banned.</strong> An earlier revision rejected
	 * any name starting with {@code solution}, which refused the most natural template
	 * layout there is: a stub at {@code src/solution.js} that the student fills in. A
	 * guard that rejects ordinary templates gets switched off, and a guard that is
	 * switched off protects nothing. A <em>source file</em> named {@code solution.*} is
	 * therefore allowed, while a {@code solution/} directory, a written-out
	 * {@code solution.md} and anything named {@code reference-solution*} stay refused.
	 */
	private static final List<ForbiddenRule> FORBIDDEN_RULES = List.of(
			(path, name, relative) -> name.startsWith("hidden"),
			(path, name, relative) -> path.contains("hidden-tests"),
			(path, name, relative) -> name.startsWith(".secret"),
			(path, name, relative) -> name.startsWith("reference-solution"),
			(path, name, relative) -> "solution".equals(name) || "solutions".equals(name),
			(path, name, relative) -> name.startsWith("solution")
					&& ANSWER_DOCUMENT_EXTENSIONS.stream().anyMatch(name::endsWith),
			(path, name, relative) -> path.contains("answer-key"),
			// Only at the template root: that is where the hidden suite's manifest lives,
			// while a nested manifest.json is ordinary project metadata a student needs.
			(path, name, relative) -> relative.getNameCount() == 1 && "manifest.json".equals(name),
			(path, name, relative) -> ".env".equals(name), (path, name, relative) -> name.endsWith(".pem"),
			(path, name, relative) -> name.endsWith(".key"), (path, name, relative) -> isPrivateKeyMaterial(name));

	/**
	 * Scans a template tree and rejects the first suspicious path.
	 * @param templateDirectory the resolved template directory
	 * @throws TemplateContentRejectedException when forbidden material is present
	 * @throws IllegalStateException when the directory cannot be read
	 */
	public void validate(Path templateDirectory) {
		try (Stream<Path> paths = Files.walk(templateDirectory)) {
			paths.filter((path) -> !path.equals(templateDirectory))
				.map(templateDirectory::relativize)
				.filter(TemplateContentGuard::isForbidden)
				.findFirst()
				.ifPresent((path) -> {
					throw new TemplateContentRejectedException(
							"Template contains forbidden hidden or secret material: " + path);
				});
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not scan template content", exception);
		}
	}

	private static boolean isForbidden(Path relativePath) {
		Path fileName = relativePath.getFileName();
		if (fileName == null) {
			return false;
		}
		String path = relativePath.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
		String name = fileName.toString().toLowerCase(Locale.ROOT);
		return FORBIDDEN_RULES.stream().anyMatch((rule) -> rule.matches(path, name, relativePath));
	}

	private static boolean isPrivateKeyMaterial(String name) {
		// A .pub file is a public key and is harmless. Anything else carrying these names
		// is private key material that must never be handed to a student.
		return (name.startsWith("id_rsa") || name.startsWith("id_ed25519")) && !name.endsWith(".pub");
	}

	/**
	 * One reason a path may not appear in a student-visible template.
	 */
	@FunctionalInterface
	private interface ForbiddenRule {

		/**
		 * Whether this rule rejects the given path.
		 * @param path the lower-cased relative path, using forward slashes
		 * @param name the lower-cased file name
		 * @param relativePath the raw relative path
		 * @return true when the path must be rejected
		 */
		boolean matches(String path, String name, Path relativePath);

	}

}
