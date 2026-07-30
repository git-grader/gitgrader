package org.gitgrader.registration.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "registration_attempts")
public class RegistrationAttempt {

	@Id
	private UUID id;

	private Instant attemptedAt;

	private String ipHash;

	private String outcome;

	private @Nullable String reason;

	private @Nullable String studentNumberHash;

	private @Nullable String emailHash;

	protected RegistrationAttempt() {
	}

	public RegistrationAttempt(UUID id, Instant attemptedAt, String ipHash, String outcome, @Nullable String reason,
			@Nullable String studentNumberHash, @Nullable String emailHash) {
		this.id = id;
		this.attemptedAt = attemptedAt;
		this.ipHash = ipHash;
		this.outcome = outcome;
		this.reason = reason;
		this.studentNumberHash = studentNumberHash;
		this.emailHash = emailHash;
	}

	// getters
	public UUID getId() {
		return this.id;
	}

	public Instant getAttemptedAt() {
		return this.attemptedAt;
	}

	public String getIpHash() {
		return this.ipHash;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public @Nullable String getReason() {
		return this.reason;
	}

	public @Nullable String getStudentNumberHash() {
		return this.studentNumberHash;
	}

	public @Nullable String getEmailHash() {
		return this.emailHash;
	}

}
