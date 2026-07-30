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
 * Tamper evident record of security and administration relevant actions.
 *
 * <p>
 * Audit entries never contain private keys, passwords or complete result tokens. Tokens
 * are recorded by their public prefix only, which is enough to correlate a support
 * request with a submission but not enough to open the result page.
 *
 * <p>
 * Declared as a shared module, because practically every module records audit events and
 * an audit dependency carries no business coupling.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit", allowedDependencies = {})
@org.jspecify.annotations.NullMarked
package org.gitgrader.audit;
