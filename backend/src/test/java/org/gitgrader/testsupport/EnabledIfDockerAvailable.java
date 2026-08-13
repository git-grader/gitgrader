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

package org.gitgrader.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs a test only where a Docker engine can actually be reached, except on CI.
 *
 * <p>
 * {@code ./mvnw clean verify} is the command contributors are asked to run before opening
 * a pull request, and without this a contributor who has no Docker engine meets a
 * Testcontainers stack trace rather than an answer. Skipping is the friendlier outcome
 * locally.
 *
 * <p>
 * It is the wrong outcome on CI, where a missing engine means the pipeline is broken, not
 * that these tests are irrelevant. A silent skip there would turn the only tests that
 * prove a container really starts into tests that pass by not running. So CI is made to
 * run them regardless and fail loudly if it cannot.
 *
 * @see DockerAvailableCondition
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ExtendWith(DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {

}
