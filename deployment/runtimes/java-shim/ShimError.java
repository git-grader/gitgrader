// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

package gitgrader;

/**
 * An error that crossed the shim boundary as a protocol error, or that the
 * client raised on the suite side.
 *
 * <p>
 * {@code phase} is the stable machine-readable reason; {@code message} the
 * detail. The exception's class name carries the phase so test runner output
 * carries the same vocabulary the protocol documents.
 */
public class ShimError extends RuntimeException {

	private final String phase;

	public ShimError(String phase, String message) {
		super(message);
		this.phase = phase;
	}

	public String phase() {
		return this.phase;
	}

	@Override
	public String toString() {
		return this.phase + ": " + getMessage();
	}

}
