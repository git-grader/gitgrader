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

package org.gitgrader.grading.internal;

import java.util.Optional;

/**
 * Proves that the Docker daemon resolves the configured bind roots to the directories
 * this application writes into.
 *
 * <p>
 * A bind whose source the daemon cannot resolve is not an error to Docker: it creates an
 * empty directory and starts the container anyway. The install step then runs against an
 * empty workspace, no report is produced, and every declared test is recorded as not
 * executed - which scores the student zero for a mount the platform got wrong.
 *
 * <p>
 * This cannot be checked from inside a grading container. Everything a sandbox can report
 * travels over the standard streams and exit status the submission itself controls, so a
 * submission could forge the failure and have its own run written off as broken
 * infrastructure. The check therefore runs in a container of its own that executes
 * nothing but the probe.
 */
interface SandboxMountProbe {

	/**
	 * Reports whether the sandbox mounts are usable.
	 * @param image the image to probe with, which the pending run has already resolved
	 * @return why grading cannot proceed, or empty when the mounts are usable
	 */
	Optional<String> unusableReason(String image);

}
