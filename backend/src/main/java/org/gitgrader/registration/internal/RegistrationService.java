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

package org.gitgrader.registration.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditService;
import org.gitgrader.audit.ClientAddressHasher;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentView;
import org.gitgrader.registration.web.AvailabilityResponse;
import org.gitgrader.registration.StudentRegistered;
import org.gitgrader.registration.web.RegistrationRequest;
import org.gitgrader.registration.web.RegistrationResponse;
import org.gitgrader.security.RateLimiter;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.sshkeys.SshKeyView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates student self-registration.
 */
@Service
public class RegistrationService {

	private static final Logger logger = LoggerFactory.getLogger(RegistrationService.class);

	private final AppProperties appProperties;

	private final RateLimiter rateLimiter;

	private final CourseCatalog courseCatalog;

	private final StudentDirectory studentDirectory;

	private final StudentRegistry studentRegistry;

	private final SshKeyRegistry sshKeyRegistry;

	private final RegistrationAttemptLog attemptLog;

	private final ClientAddressHasher hasher;

	private final AuditService auditService;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	public RegistrationService(AppProperties appProperties, RateLimiter rateLimiter, CourseCatalog courseCatalog,
			StudentDirectory studentDirectory, StudentRegistry studentRegistry, SshKeyRegistry sshKeyRegistry,
			RegistrationAttemptLog attemptLog, ClientAddressHasher hasher, AuditService auditService, Clock clock,
			ApplicationEventPublisher events) {
		this.appProperties = appProperties;
		this.rateLimiter = rateLimiter;
		this.courseCatalog = courseCatalog;
		this.studentDirectory = studentDirectory;
		this.studentRegistry = studentRegistry;
		this.sshKeyRegistry = sshKeyRegistry;
		this.attemptLog = attemptLog;
		this.hasher = hasher;
		this.auditService = auditService;
		this.clock = clock;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public AvailabilityResponse getAvailability() {
		boolean open = this.appProperties.registration().enabled();

		List<AvailabilityResponse.CourseOffering> offerings = this.courseCatalog
			.findCourses(CourseStatus.ACTIVE, Pageable.unpaged())
			.stream()
			.filter(c -> isRegistrationOpen(c, Instant.now(this.clock)))
			.map(c -> new AvailabilityResponse.CourseOffering(c.courseKey(), c.name(),
					this.courseCatalog.findClasses(c.id())
						.stream()
						.map(cls -> new AvailabilityResponse.ClassOffering(cls.classKey(), cls.name()))
						.toList()))
			.toList();

		return new AvailabilityResponse(open, null, null, offerings);
	}

	@Transactional
	public RegistrationResponse register(RegistrationRequest request, String clientAddress) {
		Instant now = Instant.now(this.clock);
		String ipHash = this.hasher.hash(clientAddress);
		String studentNumberHash = this.hasher.hash(request.studentNumber().toLowerCase(java.util.Locale.ROOT));
		String emailHash = this.hasher.hash(request.email().toLowerCase(java.util.Locale.ROOT));

		// The order below is deliberate: the cheapest and least disclosing checks run
		// first, so a flood is rejected before it can touch the database, and an attacker
		// cannot use response timing to distinguish "closed" from "already registered".
		requireRegistrationEnabled();
		requireWithinRateLimits(clientAddress, now, ipHash, studentNumberHash, emailHash);
		CourseView course = requireOpenCourse(request, now);
		requireClassBelongsToCourse(request, course);
		requireStudentNumberAvailable(request, now, ipHash, studentNumberHash, emailHash);

		StudentView student = createStudent(request, now, ipHash, studentNumberHash, emailHash);
		SshKeyView sshKey = this.sshKeyRegistry.register(student.id(), "Registration Key", request.publicKey(),
				SshKeyOrigin.REGISTRATION, null);

		this.auditService.record(AuditRecord.of(AuditEventType.STUDENT_REGISTERED)
			.subject("Student", student.id().toString())
			.with("studentNumber", student.studentNumber())
			.with("courseKey", course.courseKey())
			.with("classLabel", request.classKey())
			.build());
		recordAttempt(now, ipHash, "ACCEPTED", null, studentNumberHash, emailHash);
		this.events.publishEvent(new StudentRegistered(student.id(), student.studentNumber(), course.id(),
				course.courseKey(), request.classKey(), now));

		return new RegistrationResponse(student.id(), student.studentNumber(), student.fullName(), student.status(),
				sshKey.fingerprint());
	}

	private void requireRegistrationEnabled() {
		if (!this.appProperties.registration().enabled()) {
			throw new RegistrationClosedException("Registration is disabled globally.");
		}
	}

	private void requireWithinRateLimits(String clientAddress, Instant now, String ipHash, String studentNumberHash,
			String emailHash) {
		if (!this.rateLimiter.tryConsumeRegistrationGlobal()
				|| !this.rateLimiter.tryConsumeRegistrationPerIp(clientAddress)) {
			recordAttempt(now, ipHash, "RATE_LIMITED", "Exceeded registration rate limit", studentNumberHash,
					emailHash);
			throw new RateLimitExceededException("Registration rate limit exceeded.");
		}
	}

	private CourseView requireOpenCourse(RegistrationRequest request, Instant now) {
		CourseView course = this.courseCatalog.findCourses(CourseStatus.ACTIVE, Pageable.unpaged())
			.stream()
			.filter((candidate) -> candidate.courseKey().equals(request.courseKey()))
			.findFirst()
			.orElseThrow(() -> new RegistrationClosedException("Course not found or inactive: " + request.courseKey()));

		if (!isRegistrationOpen(course, now)) {
			throw new RegistrationClosedException("Registration is not open for course: " + request.courseKey());
		}
		return course;
	}

	private void requireClassBelongsToCourse(RegistrationRequest request, CourseView course) {
		if (request.classKey() == null) {
			return;
		}
		boolean classExists = this.courseCatalog.findClasses(course.id())
			.stream()
			.anyMatch((candidate) -> candidate.classKey().equals(request.classKey()));
		if (!classExists) {
			throw new RegistrationClosedException("Class not found in course: " + request.classKey());
		}
	}

	private void requireStudentNumberAvailable(RegistrationRequest request, Instant now, String ipHash,
			String studentNumberHash, String emailHash) {
		if (this.studentDirectory.findByStudentNumber(request.studentNumber()).isPresent()) {
			recordAttempt(now, ipHash, "DUPLICATE", "Student number already registered", studentNumberHash, emailHash);
			throw new DuplicateRegistrationException("Student number already registered");
		}
	}

	/**
	 * Creates the student, relying on the database for the e-mail uniqueness check.
	 *
	 * <p>
	 * The e-mail collision is caught rather than pre-checked on purpose: a check followed
	 * by an insert is a race, and the unique index is the only thing that actually
	 * guarantees the constraint under concurrent submissions.
	 * @param request the submitted registration
	 * @param now the current instant
	 * @param ipHash keyed hash of the client address
	 * @param studentNumberHash keyed hash of the student number
	 * @param emailHash keyed hash of the e-mail address
	 * @return the created student
	 */
	private StudentView createStudent(RegistrationRequest request, Instant now, String ipHash, String studentNumberHash,
			String emailHash) {
		try {
			StudentRegistration registrationData = new StudentRegistration(request.studentNumber(), request.firstName(),
					request.lastName(), request.email(), request.classKey(), ipHash);
			return this.studentRegistry.register(registrationData);
		}
		catch (DataIntegrityViolationException ex) {
			recordAttempt(now, ipHash, "DUPLICATE", "Email already registered", studentNumberHash, emailHash);
			throw new DuplicateRegistrationException("Email already registered", ex);
		}
	}

	private boolean isRegistrationOpen(CourseView course, Instant now) {
		return course.status() == CourseStatus.ACTIVE && course.registrationEnabled()
				&& (course.registrationOpensAt() == null || !now.isBefore(course.registrationOpensAt()))
				&& (course.registrationClosesAt() == null || !now.isAfter(course.registrationClosesAt()));
	}

	private void recordAttempt(Instant now, String ipHash, String outcome, String reason, String studentNumberHash,
			String emailHash) {
		this.attemptLog.record(now, ipHash, outcome, reason, studentNumberHash, emailHash);
	}

}
