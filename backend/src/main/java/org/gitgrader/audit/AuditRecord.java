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

package org.gitgrader.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A single auditable action, described independently of how it is stored.
 *
 * <p>
 * <strong>What must never appear in an audit record:</strong> a private key, a password,
 * an LDAP bind credential, a complete result token, or a raw client IP address. The
 * builder offers no way to attach any of them, and {@link #detail()} is a plain map that
 * a reviewer can read at a glance.
 *
 * @param type what happened
 * @param severity how much attention it deserves
 * @param outcome whether the action succeeded, failed or was denied
 * @param actorType the kind of principal that acted
 * @param actorId stable identifier of the actor, or {@code null} for anonymous
 * @param actorName human readable actor label
 * @param subjectType the kind of object acted upon
 * @param subjectId identifier of the object acted upon
 * @param courseId owning course, when the action is scoped to one
 * @param sourceIpHash keyed hash of the source address, never the address itself
 * @param correlationId ties this record to the log lines of the same operation
 * @param detail additional structured context
 */
public record AuditRecord(AuditEventType type, AuditSeverity severity, AuditOutcome outcome, ActorType actorType,
		@Nullable String actorId, @Nullable String actorName, @Nullable String subjectType, @Nullable String subjectId,
		@Nullable UUID courseId, @Nullable String sourceIpHash, @Nullable String correlationId,
		Map<String, Object> detail) {

	public AuditRecord {
		detail = Map.copyOf(detail);
	}

	/**
	 * Starts building a record for the given event type.
	 * @param type what happened
	 * @return a new builder
	 */
	public static Builder of(AuditEventType type) {
		return new Builder(type);
	}

	/** The kind of principal that performed an action. */
	public enum ActorType {

		/** A student, identified by the SSH key used for the transport. */
		STUDENT,
		/** An instructor authenticated through the directory. */
		INSTRUCTOR,
		/** An administrator authenticated through the directory. */
		ADMIN,
		/** The platform itself, for scheduled and automatic actions. */
		SYSTEM,
		/** An unauthenticated caller, as on the public registration endpoint. */
		ANONYMOUS

	}

	/** How much attention a record deserves. */
	public enum AuditSeverity {

		/** Routine, expected activity. */
		INFO,
		/** Worth noticing when reviewing a course. */
		NOTICE,
		/** Something was refused or looks abusive. */
		WARNING,
		/** Security relevant and should page someone. */
		CRITICAL

	}

	/** Whether the audited action was allowed to proceed. */
	public enum AuditOutcome {

		/** The action completed. */
		SUCCESS,
		/** The action was attempted and failed. */
		FAILURE,
		/** The action was refused by an authorization or policy check. */
		DENIED

	}

	/**
	 * Fluent builder for {@link AuditRecord}.
	 *
	 * <p>
	 * Deliberately has no setter for anything secret. Extending this class is how a
	 * reviewer can tell that a new audit call site cannot start logging credentials.
	 */
	public static final class Builder {

		private final AuditEventType type;

		private final Map<String, Object> detail = new LinkedHashMap<>();

		private AuditSeverity severity = AuditSeverity.INFO;

		private AuditOutcome outcome = AuditOutcome.SUCCESS;

		private ActorType actorType = ActorType.SYSTEM;

		private @Nullable String actorId;

		private @Nullable String actorName;

		private @Nullable String subjectType;

		private @Nullable String subjectId;

		private @Nullable UUID courseId;

		private @Nullable String sourceIpHash;

		private @Nullable String correlationId;

		private Builder(AuditEventType type) {
			this.type = type;
		}

		/**
		 * Sets how much attention this record deserves.
		 * @param value the severity
		 * @return this builder
		 */
		public Builder severity(AuditSeverity value) {
			this.severity = value;
			return this;
		}

		/**
		 * Marks the audited action as failed.
		 * @return this builder
		 */
		public Builder failed() {
			this.outcome = AuditOutcome.FAILURE;
			return this;
		}

		/**
		 * Marks the audited action as refused by policy, and raises the severity because
		 * a denial is always more interesting than a success.
		 * @return this builder
		 */
		public Builder denied() {
			this.outcome = AuditOutcome.DENIED;
			this.severity = AuditSeverity.WARNING;
			return this;
		}

		/**
		 * Records who acted.
		 * @param kind the kind of principal
		 * @param id stable identifier, may be {@code null}
		 * @param name human readable label, may be {@code null}
		 * @return this builder
		 */
		public Builder actor(ActorType kind, @Nullable String id, @Nullable String name) {
			this.actorType = kind;
			this.actorId = id;
			this.actorName = name;
			return this;
		}

		/**
		 * Records what was acted upon.
		 * @param kind the kind of object
		 * @param id identifier of the object
		 * @return this builder
		 */
		public Builder subject(String kind, @Nullable String id) {
			this.subjectType = kind;
			this.subjectId = id;
			return this;
		}

		/**
		 * Scopes the record to a course.
		 * @param id the course
		 * @return this builder
		 */
		public Builder course(@Nullable UUID id) {
			this.courseId = id;
			return this;
		}

		/**
		 * Records the already hashed source address.
		 *
		 * <p>
		 * The parameter is a hash by contract. Call sites obtain it from
		 * {@code ClientAddressHasher}; there is intentionally no overload that accepts a
		 * raw address.
		 * @param hash keyed hash of the client address
		 * @return this builder
		 */
		public Builder sourceIpHash(@Nullable String hash) {
			this.sourceIpHash = hash;
			return this;
		}

		/**
		 * Ties this record to the surrounding operation.
		 * @param id the correlation identifier
		 * @return this builder
		 */
		public Builder correlationId(@Nullable String id) {
			this.correlationId = id;
			return this;
		}

		/**
		 * Adds one structured detail entry.
		 * @param key detail name
		 * @param value detail value; {@code null} values are dropped
		 * @return this builder
		 */
		public Builder with(String key, @Nullable Object value) {
			if (value != null) {
				this.detail.put(key, value);
			}
			return this;
		}

		/**
		 * Builds the immutable record.
		 * @return the audit record
		 */
		public AuditRecord build() {
			return new AuditRecord(this.type, this.severity, this.outcome, this.actorType, this.actorId, this.actorName,
					this.subjectType, this.subjectId, this.courseId, this.sourceIpHash, this.correlationId,
					this.detail);
		}

	}

}
