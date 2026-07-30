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

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Provides read-only access to student profiles. */
public interface StudentDirectory {

	/**
	 * Finds a student by stable identifier.
	 * @param id profile identifier
	 * @return matching student, if present
	 */
	Optional<StudentView> findById(UUID id);

	/**
	 * Finds a student by case-insensitive student number.
	 * @param studentNumber institutional student number
	 * @return matching student, if present
	 */
	Optional<StudentView> findByStudentNumber(String studentNumber);

	/**
	 * Searches student profiles using optional filters.
	 * @param search optional filters
	 * @param pageable requested page
	 * @return matching students
	 */
	Page<StudentView> search(StudentSearch search, Pageable pageable);

}
