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
 * The stored submission: what was pushed, when it was received, and where it stands.
 *
 * <p>
 * A submission is recorded the moment a push is admitted and never deleted, because the
 * attempt history is part of what the product promises. Its received-at instant is the
 * server's, not the client's commit date — it is what a deadline is judged against, so a
 * student cannot move it.
 */
@NullMarked
package org.gitgrader.submissions.domain;

import org.jspecify.annotations.NullMarked;
