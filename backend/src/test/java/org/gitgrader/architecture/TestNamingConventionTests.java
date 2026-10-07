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

package org.gitgrader.architecture;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces that every test carries a human-readable name.
 *
 * <p>
 * {@code @DisplayName} is the only description a reader of a failed CI run gets. The
 * alternative is the method name, and a method name has to be a valid Java identifier: it
 * cannot contain a space, so {@code refusesToGradeAnUnimplementedReportFormat} reads as
 * one run-on word, and the reader has to hold the whole suite in their head to decode it.
 * Almost every name in this repository is a sentence precisely because
 * {@code @DisplayName} permits spaces.
 *
 * <p>
 * The convention was adopted file by file, which is how conventions decay: each new test
 * matches the file it lands in, and a file untouched for a year keeps the old style. This
 * rule is what stops the two styles coexisting, and it fails on the file that regressed
 * rather than on the contributor who wrote a passing test.
 *
 * <p>
 * The annotations are read from the bytecode, so a comment cannot satisfy the rule and no
 * source parser has to stay in step with the formatter. ArchUnit supplies the inventory
 * of test classes; {@link JavaMethod#reflect()} supplies the annotations themselves.
 */
class TestNamingConventionTests {

	private static final String ROOT = "org.gitgrader";

	/**
	 * Imported without {@code DO_NOT_INCLUDE_TESTS}, since the classes under inspection
	 * are themselves the test classes.
	 */
	private static final JavaClasses TEST_CLASSES = new ClassFileImporter().importPackages(ROOT);

	@Test
	@DisplayName("every test method and nested class carries a display name")
	void everyTestIsNamed() {
		// Nested classes arrive as members of the imported set rather than through the
		// enclosing type, so both are inspected where they are.
		List<String> unnamed = Stream
			.concat(TEST_CLASSES.stream()
				.filter(JavaClass::isTopLevelClass)
				.flatMap(TestNamingConventionTests::unnamedMethodsOf),
					TEST_CLASSES.stream()
						.filter(TestNamingConventionTests::isNestedTestClass)
						.filter(nested -> !nested.isAnnotatedWith(DisplayName.class))
						.map(nested -> nested.getName()))
			.sorted()
			.toList();

		assertThat(unnamed).as("""
				tests with no @DisplayName - add one naming what the test pins down, in the \
				house style: a lowercase verb phrase reading as a claim about the subject, \
				with no trailing period""").isEmpty();
	}

	private static Stream<String> unnamedMethodsOf(JavaClass type) {
		return type.getAllMethods()
			.stream()
			.filter(TestNamingConventionTests::isTestMethod)
			.filter(method -> !method.isAnnotatedWith(DisplayName.class))
			.map(method -> type.getName() + "#" + method.getName());
	}

	private static boolean isTestMethod(JavaMethod method) {
		boolean isTest = method.isAnnotatedWith(Test.class) || method.isAnnotatedWith(ParameterizedTest.class);
		return isTest && !Modifier.isPrivate(method.reflect().getModifiers());
	}

	private static boolean isNestedTestClass(JavaClass member) {
		boolean nested = member.isNestedClass() && member.isAnnotatedWith(Nested.class);
		return nested && !member.getModifiers().contains(JavaModifier.PRIVATE);
	}

}
