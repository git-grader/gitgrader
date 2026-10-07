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

package org.gitgrader.courses.internal;

import java.util.List;
import java.util.UUID;

import org.gitgrader.courses.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persists enrolments, the link between a student and a class. */
interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

	boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

	List<Enrollment> findByStudentId(UUID studentId);

	List<Enrollment> findByCourseIdAndClassId(UUID courseId, UUID classId);

	@Query("SELECT e.studentId FROM Enrollment e WHERE e.courseId = :courseId")
	List<UUID> findStudentIdsByCourseId(@Param("courseId") UUID courseId);

	@Query("SELECT e.studentId FROM Enrollment e WHERE e.courseId = :courseId AND e.classId = :classId")
	List<UUID> findStudentIdsByCourseIdAndClassId(@Param("courseId") UUID courseId, @Param("classId") UUID classId);

}
