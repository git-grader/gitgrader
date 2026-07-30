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

package org.gitgrader.configuration;

import java.nio.file.Path;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Filesystem layout of everything that is not in the database.
 *
 * <p>
 * The separation of {@code testsDirectory} from {@code repositoriesDirectory} and
 * {@code templatesDirectory} is a security boundary, not an organisational preference.
 * Hidden test suites must not be reachable from anything a student can clone, so they are
 * stored on a different path, are never copied into a template, and are mounted into a
 * grading sandbox read only for the lifetime of a single run.
 *
 * @param repositoriesDirectory bare Git repositories, one per student and assignment
 * @param templatesDirectory immutable published template versions
 * @param testsDirectory hidden test suites; never served, never cloned, never logged
 * @param artifactsDirectory optional retained grading artifacts
 * @param tempDirectory scratch space for provisioning and export
 */
@ConfigurationProperties(prefix = "storage")
@Validated
public record StorageProperties(

		@DefaultValue("/data/git/repositories") @NotBlank String repositoriesDirectory,

		@DefaultValue("/data/templates") @NotBlank String templatesDirectory,

		@DefaultValue("/data/tests") @NotBlank String testsDirectory,

		@DefaultValue("/data/artifacts") @NotBlank String artifactsDirectory,

		@DefaultValue("/data/tmp") @NotBlank String tempDirectory) {

	/**
	 * Root of the bare repository tree.
	 * @return absolute, normalised path
	 */
	public Path repositories() {
		return Path.of(this.repositoriesDirectory).toAbsolutePath().normalize();
	}

	/**
	 * Root of the published template tree.
	 * @return absolute, normalised path
	 */
	public Path templates() {
		return Path.of(this.templatesDirectory).toAbsolutePath().normalize();
	}

	/**
	 * Root of the hidden test suite tree.
	 * @return absolute, normalised path
	 */
	public Path tests() {
		return Path.of(this.testsDirectory).toAbsolutePath().normalize();
	}

	/**
	 * Root of the retained artifact tree.
	 * @return absolute, normalised path
	 */
	public Path artifacts() {
		return Path.of(this.artifactsDirectory).toAbsolutePath().normalize();
	}

	/**
	 * Root of the scratch tree.
	 * @return absolute, normalised path
	 */
	public Path temp() {
		return Path.of(this.tempDirectory).toAbsolutePath().normalize();
	}

	/**
	 * Resolves a path inside a root, refusing anything that escapes it.
	 *
	 * <p>
	 * Repository names contain a course key, an assignment key and a student identifier,
	 * all of which originate outside the process - the course and assignment keys from an
	 * instructor, and the repository path from the SSH client's exec line. Every
	 * filesystem lookup driven by such input goes through this method, so that
	 * {@code ../} sequences and absolute paths cannot walk out of the intended tree.
	 * @param root the directory the result must stay inside
	 * @param relative the untrusted relative path
	 * @return the resolved, normalised path
	 * @throws IllegalArgumentException if the result would escape {@code root}
	 */
	public static Path resolveInside(Path root, String relative) {
		Path base = root.toAbsolutePath().normalize();
		Path resolved = base.resolve(relative).normalize();
		if (!resolved.startsWith(base)) {
			throw new IllegalArgumentException("Path escapes its designated root directory");
		}
		return resolved;
	}

}
