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

package org.gitgrader.git.internal;

import java.nio.file.Path;

import org.gitgrader.configuration.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Holds the repository path builder to one directory per student.
 *
 * <p>
 * A student number reaches this method straight from the public self-registration form,
 * which constrains only its length. Because the path is assembled by concatenation and
 * only normalised when it is resolved on disk, a number containing a separator used to
 * let two different registrations name one bare repository: {@code ../a1/victim} under
 * assignment {@code a1} normalises to the same directory as the plain number
 * {@code victim}. Both rows passed the unique index, because that compares the stored
 * strings rather than the directories they denote, and each student then authenticated
 * with their own key against their own row.
 *
 * <p>
 * {@link StorageProperties#resolveInside} does not catch this: it rejects a path that
 * leaves the repository root, and this one never leaves it.
 */
class RepositoryPathSafetyTest {

	@Test
	@DisplayName("builds the path a student clones from")
	void buildsTheCloneablePath() {
		assertThat(GitRepositoryService.repositoryPathFor("cs101", "assignment-01", "s1001"))
			.isEqualTo("cs101/assignment-01/s1001");
	}

	@Test
	@DisplayName("refuses a student number that would reach another student's repository")
	void refusesAliasingStudentNumber() {
		assertThatThrownBy(
				() -> GitRepositoryService.repositoryPathFor("cs101", "assignment-01", "../assignment-01/victim"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("student number");
	}

	@Test
	@DisplayName("the refused number would otherwise have resolved onto the victim's directory")
	void demonstratesTheAliasThatIsBeingRefused() {
		Path root = Path.of("/srv/gitgrader/repositories");
		Path victim = StorageProperties.resolveInside(root, "cs101/assignment-01/victim.git");
		Path alias = StorageProperties.resolveInside(root, "cs101/assignment-01/../assignment-01/victim.git");

		assertThat(alias).isEqualTo(victim);
	}

	@ParameterizedTest
	@ValueSource(strings = { "..", ".", "../victim", "a/b", "a\\b", "s1001/", "/s1001", "s1001\u0000x", "s 1001", "" })
	@DisplayName("refuses every separator, traversal and control character in a student number")
	void refusesUnsafeStudentNumbers(String studentNumber) {
		assertThatThrownBy(() -> GitRepositoryService.repositoryPathFor("cs101", "assignment-01", studentNumber))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "s1001", "S1001", "12345678", "s.1001", "s_1001", "s-1001", "a" })
	@DisplayName("accepts the shapes a real student number takes")
	void acceptsOrdinaryStudentNumbers(String studentNumber) {
		assertThatCode(() -> GitRepositoryService.repositoryPathFor("cs101", "assignment-01", studentNumber))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "..", "a/b", "../other" })
	@DisplayName("refuses a course or assignment key that would reach outside its own directory")
	void refusesUnsafeKeys(String key) {
		assertThatThrownBy(() -> GitRepositoryService.repositoryPathFor(key, "assignment-01", "s1001"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GitRepositoryService.repositoryPathFor("cs101", key, "s1001"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
