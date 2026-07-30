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

package org.gitgrader.sshkeys;

import java.io.Serial;

/**
 * Raised when submitted key material cannot be accepted.
 *
 * <p>
 * The exception carries only the {@link SshKeyRejectionReason}. It deliberately does not
 * carry the offending input: this exception is thrown on the public registration path,
 * where the input may be a private key, and an exception message is the single most
 * likely thing to end up in a log file.
 */
public class SshKeyRejectedException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final transient SshKeyRejectionReason reason;

	/**
	 * Creates a rejection for the given reason.
	 * @param reason why the key was refused
	 */
	public SshKeyRejectedException(SshKeyRejectionReason reason) {
		super(reason.name());
		this.reason = reason;
	}

	/**
	 * The machine readable rejection reason.
	 * @return the reason
	 */
	public SshKeyRejectionReason reason() {
		return this.reason;
	}

	/**
	 * A message safe to show to an unauthenticated caller.
	 * @return the public explanation
	 */
	public String publicMessage() {
		return this.reason.publicMessage();
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		// Validation failures are an expected outcome on a public form, not a defect.
		// Capturing a stack trace for each one is pure overhead under a submission flood.
		return this;
	}

}
