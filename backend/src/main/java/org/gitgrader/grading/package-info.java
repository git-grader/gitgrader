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

/**
 * Execution of hidden test suites and translation of their output into a score.
 *
 * <p>
 * Student code is untrusted. It is never executed inside this JVM; it only ever runs
 * through a {@code GradingRunner}, which is an isolated, short lived, network disabled,
 * resource capped sandbox. Hidden test suites are mounted read only into that sandbox for
 * the duration of a single run and are never reachable from a student repository, a
 * clone, a result page or a log line.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Grading",
		allowedDependencies = { "submissions", "assignments", "runtimes", "templates" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.grading;
