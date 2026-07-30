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

package org.gitgrader.identity;

import org.jspecify.annotations.Nullable;

/**
 * Values accepted when creating a student profile.
 *
 * @param studentNumber institutional student number
 * @param firstName given name
 * @param lastName family name
 * @param email contact address
 * @param classLabel optional class label
 * @param registrationIpHash keyed source-address hash
 */
public record StudentRegistration(String studentNumber, String firstName, String lastName, String email,
		@Nullable String classLabel, @Nullable String registrationIpHash) {
}
