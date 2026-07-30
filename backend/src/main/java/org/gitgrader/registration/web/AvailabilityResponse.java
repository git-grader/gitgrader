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

package org.gitgrader.registration.web;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Indicates whether registration is open and which courses can be joined.
 */
public record AvailabilityResponse(boolean open, @Nullable Instant opensAt, @Nullable Instant closesAt,
		List<CourseOffering> courses) {

	/**
	 * A course currently accepting registrations.
	 *
	 * @param courseKey stable key used in clone URLs
	 * @param name display name
	 * @param classes the classes a student may pick
	 */
	public record CourseOffering(String courseKey, String name, List<ClassOffering> classes) {
	}

	/**
	 * A class within a course.
	 *
	 * @param classKey stable key
	 * @param name display name
	 */
	public record ClassOffering(String classKey, String name) {
	}
}
