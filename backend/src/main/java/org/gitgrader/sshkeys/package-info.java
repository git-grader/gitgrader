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
 * Registration, validation, fingerprinting and lifecycle of student SSH public keys.
 *
 * <p>
 * A key is never physically deleted. It moves through
 * {@code ACTIVE -> REVOKED | REPLACED | SUSPENDED} so that a submission signed years ago
 * can still be attributed to the exact key that signed it.
 *
 * <p>
 * Only public keys are ever accepted. The module rejects anything that parses as a
 * private key before it can reach persistence or a log.
 */
@org.springframework.modulith.ApplicationModule(displayName = "SSH keys", allowedDependencies = "identity")
@org.jspecify.annotations.NullMarked
package org.gitgrader.sshkeys;
