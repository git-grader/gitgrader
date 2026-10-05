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

package org.gitgrader.runtimes;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * How a runtime runs the submission and the hidden suite.
 *
 * <p>
 * A runtime either runs both in one sandbox, which is what every runtime built before the
 * shim existed does, or it splits them across two containers that talk over a Unix
 * socket. Those are different grading topologies, and picking the wrong one is not a
 * cosmetic mistake: a shimmed runtime whose hidden suite never opens the socket fails
 * every check with a connection error, which reads as a failing student rather than a
 * misconfigured runtime.
 * </p>
 *
 * <p>
 * Because of that, a runtime created now has to state which topology it wants, and the
 * single-container answer is spelled {@value #LEGACY} rather than left blank. The column
 * itself still stores NULL for that answer, so nothing about how an existing runtime
 * grades changes; only the way an operator declares a new one does.
 * </p>
 *
 * @see NewRuntime#shimKind()
 */
public final class ShimTopology {

	/**
	 * The declared form of "run the submission and the suite in one sandbox".
	 *
	 * <p>
	 * Deliberately a word rather than an absent value: a blank field is an oversight, and
	 * an oversight here silently selects a grading topology.
	 */
	public static final String LEGACY = "legacy";

	private static final Pattern KIND = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,31}$");

	private ShimTopology() {
	}

	/**
	 * Reports whether a stored topology asks for the two-container split.
	 *
	 * <p>
	 * This is the check every grading path uses. An absent topology means one sandbox, so
	 * a runtime created before the shim existed keeps grading exactly as it did.
	 * @param stored the value on the runtime row, possibly null
	 * @return true when the runtime runs the submission and the suite apart
	 */
	public static boolean isDeclared(@Nullable String stored) {
		return stored != null && !stored.isBlank();
	}

	/**
	 * Reports whether a declared kind asks for the single-sandbox topology.
	 * @param declared the value an operator supplied
	 * @return true when the runtime should run without a shim
	 */
	public static boolean isLegacy(@Nullable String declared) {
		return LEGACY.equals(declared);
	}

	/**
	 * Turns a declared kind into the value stored on the runtime row.
	 * @param declared the value an operator supplied, {@value #LEGACY} for one sandbox
	 * @return the kind to persist, or null for the single-sandbox topology
	 * @throws IllegalArgumentException if the declaration is unusable
	 */
	public static @Nullable String toStored(@Nullable String declared) {
		requireValid(declared);
		return isLegacy(declared) ? null : declared;
	}

	/**
	 * Rejects a declaration that could not select a topology on purpose.
	 * @param declared the value an operator supplied
	 * @throws IllegalArgumentException if the declaration is blank or malformed
	 */
	public static void requireValid(@Nullable String declared) {
		if (declared == null || declared.isBlank()) {
			throw new IllegalArgumentException("A runtime must declare its grading topology: '" + LEGACY
					+ "' to run the submission and the hidden suite in one sandbox, or the name of the shim that "
					+ "connects them");
		}
		String trimmed = declared.strip();
		if (!KIND.matcher(trimmed).matches()) {
			throw new IllegalArgumentException("The grading topology must be '" + LEGACY
					+ "' or a lowercase name such as 'node', made of " + "letters, digits, dot, dash or underscore");
		}
		if (trimmed.length() != declared.length()) {
			throw new IllegalArgumentException("The grading topology must not begin or end with whitespace");
		}
	}

	/**
	 * Rejects a shim command on a runtime that will never start a shim.
	 * @param declared the declared topology
	 * @param shimCommand the command supplied alongside it
	 * @throws IllegalArgumentException if a legacy runtime carries a shim command
	 */
	public static void requireConsistent(@Nullable String declared, @Nullable String shimCommand) {
		if (isLegacy(declared) && shimCommand != null && !shimCommand.isBlank()) {
			throw new IllegalArgumentException("A runtime declared '" + LEGACY
					+ "' runs in one sandbox and starts no shim, so it cannot carry " + "a shim command");
		}
	}

}
