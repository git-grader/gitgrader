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

import java.util.List;

import org.gitgrader.GitGraderApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the modular monolith actually stays modular.
 *
 * <p>
 * The whole design rests on modules only reaching across boundaries they declared in
 * their {@code package-info.java}. Nothing about that is self-enforcing: a stray import
 * compiles perfectly and quietly turns the monolith into a ball of mud. This test is what
 * makes the boundary real.
 *
 * <p>
 * It is also what keeps a JPA entity from leaking between modules, because Modulith
 * treats every subpackage as module-internal. A separate ArchUnit rule for that would be
 * redundant and, as an earlier revision of this suite showed, easy to state wrongly.
 */
class ModularityTests {

	private static final ApplicationModules MODULES = ApplicationModules.of(GitGraderApplication.class);

	@Test
	@DisplayName("no module reaches across a boundary it did not declare")
	void modulesRespectTheirDeclaredDependencies() {
		// Fails on an undeclared module dependency, a cycle between modules, or access to
		// another module's internal packages.
		MODULES.verify();
	}

	@Test
	@DisplayName("every expected application module is detected")
	void detectsEveryModule() {
		List<String> names = MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString).toList();

		assertThat(names).contains("audit", "identity", "sshkeys", "registration", "courses", "templates", "runtimes",
				"assignments", "submissions", "grading", "git", "security");
	}

	@Test
	@DisplayName("writes the module documentation referenced by docs/architecture.md")
	void writesModuleDocumentation() {
		// Generated rather than hand-maintained: a diagram that has to be updated by hand
		// is a diagram that is wrong.
		new Documenter(MODULES).writeDocumentation().writeIndividualModulesAsPlantUml();
	}

}
