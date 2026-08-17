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

package org.gitgrader.identity.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.InstructorDirectory;
import org.gitgrader.identity.InstructorView;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentSearch;
import org.gitgrader.identity.StudentView;
import org.gitgrader.identity.domain.Instructor;
import org.gitgrader.identity.domain.Student;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default identity directory and registry implementation. */
@Service
@Transactional
public class DefaultIdentityService implements StudentDirectory, StudentRegistry, InstructorDirectory {

	private final StudentRepository students;

	private final InstructorRepository instructors;

	private final Clock clock;

	DefaultIdentityService(StudentRepository students, InstructorRepository instructors, Clock clock) {
		this.students = students;
		this.instructors = instructors;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StudentView> findById(UUID id) {
		return this.students.findById(id).map(Student::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StudentView> findByStudentNumber(String studentNumber) {
		return this.students.findByStudentNumberIgnoreCase(studentNumber).map(Student::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<StudentView> search(StudentSearch search, Pageable pageable) {
		return this.students.findAll(specification(search), pageable).map(Student::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StudentView> findByIds(Collection<UUID> ids) {
		return this.students.findByIdIn(ids).stream().map(Student::toView).toList();
	}

	@Override
	public StudentView register(StudentRegistration registration) {
		return this.students.save(new Student(registration, this.clock)).toView();
	}

	@Override
	public StudentView verify(UUID studentId, Actor actor) {
		Student student = requireStudent(studentId);
		student.verify(actor, this.clock);
		return student.toView();
	}

	@Override
	public StudentView suspend(UUID studentId, String reason, Actor actor) {
		Student student = requireStudent(studentId);
		student.suspend(reason, actor, this.clock);
		return student.toView();
	}

	@Override
	public StudentView archive(UUID studentId) {
		Student student = requireStudent(studentId);
		student.archive(this.clock);
		return student.toView();
	}

	@Override
	public StudentView anonymize(UUID studentId) {
		Student student = requireStudent(studentId);
		student.anonymize(this.clock);
		return student.toView();
	}

	@Override
	public InstructorView upsertOnLogin(String username, String displayName, @Nullable String email,
			Set<String> roles) {
		Instructor instructor = this.instructors.findByUsernameIgnoreCase(username)
			.orElseGet(() -> new Instructor(username, displayName, email, roles, this.clock));
		if (this.instructors.findByUsernameIgnoreCase(username).isPresent()) {
			instructor.updateOnLogin(displayName, email, roles, this.clock);
		}
		return this.instructors.save(instructor).toView();
	}

	private Student requireStudent(UUID id) {
		return this.students.findById(id).orElseThrow(() -> new EntityNotFoundException("Student not found: " + id));
	}

	private static Specification<Student> specification(StudentSearch search) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (search.status() != null) {
				predicates.add(builder.equal(root.get("status"), search.status()));
			}
			if (search.classLabel() != null) {
				predicates.add(builder.equal(root.get("classLabel"), search.classLabel()));
			}
			addTextPredicate(search.text(), root, builder, predicates);
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static void addTextPredicate(@Nullable String text, jakarta.persistence.criteria.Root<Student> root,
			jakarta.persistence.criteria.CriteriaBuilder builder, List<Predicate> predicates) {
		if (text == null || text.isBlank()) {
			return;
		}
		String pattern = "%" + text.toLowerCase(Locale.ROOT) + "%";
		predicates.add(builder.or(builder.like(builder.lower(root.get("studentNumber")), pattern),
				builder.like(builder.lower(root.get("firstName")), pattern),
				builder.like(builder.lower(root.get("lastName")), pattern),
				builder.like(builder.lower(root.get("email")), pattern)));
	}

}
