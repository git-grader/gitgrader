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

package org.gitgrader.submissions;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Optional filters for submission listings.
 *
 * <p>
 * Every filter is applied by the database before the page is cut. Filtering a page after
 * it has been read instead drops matching rows that sat on other pages and reports the
 * matches on the current page as the total, so a course with matches spread across pages
 * shows an empty first page and a count that contradicts it.
 *
 * @param courseId course to include
 * @param assignmentId assignment to include
 * @param studentId student to include
 * @param status lifecycle state to include
 */
public record SubmissionSearch(@Nullable UUID courseId, @Nullable UUID assignmentId, @Nullable UUID studentId,
		@Nullable SubmissionStatus status) {
}
