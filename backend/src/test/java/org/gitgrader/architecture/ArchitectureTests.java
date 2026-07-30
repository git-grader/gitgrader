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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.theClass;

/**
 * Structural rules that a compiler cannot express.
 *
 * <p>
 * Each rule here exists because breaking it produces a defect that is expensive to find
 * later: a context that will not start, a security control that can be bypassed, or a
 * layering mistake that only shows up as a cycle months afterwards. Where a rule was
 * written in response to a real failure during development, the Javadoc says so, because
 * that is the part a future contributor most needs to know before relaxing it.
 */
class ArchitectureTests {

	private static final String ROOT = "org.gitgrader";

	private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages(ROOT);

	@Test
	@DisplayName("no Spring bean is a final class")
	void springBeansAreNotFinal() {
		// LEARNED THE HARD WAY: spring-modulith-starter-insight wraps every application
		// module bean in a CGLIB proxy for per-module tracing. CGLIB cannot subclass a
		// final class, so a `public final class` annotated @Component fails context
		// startup with "Cannot subclass final class" - at runtime, not at compile time.
		ArchRule rule = noClasses().that()
			.areAnnotatedWith(Component.class)
			.or()
			.areAnnotatedWith(Service.class)
			.or()
			.areAnnotatedWith(Repository.class)
			.or()
			.areAnnotatedWith(Controller.class)
			.or()
			.areAnnotatedWith(RestController.class)
			.should()
			.haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
			.because("Spring Modulith observability proxies every module bean with CGLIB, "
					+ "which cannot subclass a final class; the context fails to start");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("dependencies are injected through constructors, never into fields")
	void noFieldInjection() {
		ArchRule rule = noFields().should()
			.beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
			.because("constructor injection keeps collaborators explicit and the class "
					+ "constructible in a plain unit test");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("nullability is expressed with JSpecify only")
	void usesJSpecifyNullability() {
		// Spring 7 moved to JSpecify and packages here are @NullMarked. Mixing in the old
		// Spring annotations silently produces two different, partially checked notions
		// of
		// nullability in the same codebase.
		ArchRule rule = noClasses().should()
			.dependOnClassesThat()
			.haveFullyQualifiedName("org.springframework.lang.Nullable")
			.because("packages are @NullMarked; use org.jspecify.annotations.Nullable");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("controllers never touch persistence directly")
	void controllersDoNotReachIntoPersistence() {
		ArchRule rule = noClasses().that()
			.areAnnotatedWith(RestController.class)
			.or()
			.areAnnotatedWith(Controller.class)
			.should()
			.dependOnClassesThat()
			.areAssignableTo("org.springframework.data.repository.Repository")
			.because("business logic belongs in a service; a controller that queries "
					+ "directly cannot be reused by the SSH endpoint or the grading pipeline");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("JPA entities never reach the web layer")
	void entitiesNeverReachTheWebLayer() {
		// Deliberately narrow. Cross-module entity leakage is already impossible -
		// Modulith
		// treats every subpackage as module-internal and ModularityTests enforces it -
		// and
		// a broader rule here wrongly flags a module's own service using its own entity.
		ArchRule rule = noClasses().that()
			.resideInAPackage("..web..")
			.should()
			.dependOnClassesThat()
			.areAnnotatedWith(Entity.class)
			.because("controllers must map to DTO records; serialising an entity exposes "
					+ "whatever fields happen to exist and drags lazy associations into the response");

		rule.allowEmptyShould(true).check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("the domain layer stays free of web and persistence framework types")
	void domainIsFrameworkAgnosticAtTheEdges() {
		ArchRule rule = noClasses().that()
			.resideInAPackage("..domain..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("org.springframework.web..", "jakarta.servlet..", "org.springframework.http..")
			.because("domain rules must be testable without a servlet container");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("untrusted code is never executed by this JVM")
	void neverSpawnsChildProcesses() {
		// The single most important rule in the product. Student code runs ONLY inside a
		// GradingRunner sandbox; a ProcessBuilder anywhere in the application would run
		// it
		// with the privileges of the application itself.
		ArchRule rule = noClasses().should()
			.dependOnClassesThat()
			.haveFullyQualifiedName("java.lang.ProcessBuilder")
			.because("student code must only ever run through GradingRunner, "
					+ "never as a child process of the application JVM");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("time is read from the injected Clock, never from the wall clock")
	void timeComesFromTheClockBean() {
		// Deadlines are legally meaningful and must be deterministically testable.
		// forbidden-apis enforces this too; ArchUnit is here for the readable message.
		ArchRule rule = noClasses().that()
			.resideOutsideOfPackage("org.gitgrader.architecture..")
			.should()
			.callMethod(System.class, "currentTimeMillis")
			.because("inject java.time.Clock and use Instant.now(clock)");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("the storage path guard is the only way untrusted paths are resolved")
	void storageGuardExists() {
		// Repository paths arrive from an SSH exec line and course keys from an
		// instructor. Both end up as filesystem path segments, so the traversal guard has
		// to keep existing and keep being public.
		ArchRule rule = theClass("org.gitgrader.configuration.StorageProperties").should()
			.bePublic()
			.because("resolveInside() is the shared guard against path traversal");

		rule.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("loggers are private static final and named consistently")
	void loggersAreDeclaredConsistently() {
		ArchRule rule = fields().that()
			.haveRawType("org.slf4j.Logger")
			.should()
			.bePrivate()
			.andShould()
			.beStatic()
			.andShould()
			.beFinal()
			.because("a shared, immutable logger per class keeps log output attributable");

		rule.allowEmptyShould(true).check(PRODUCTION_CLASSES);
	}

}
