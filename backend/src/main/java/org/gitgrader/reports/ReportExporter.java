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

package org.gitgrader.reports;

import java.io.IOException;

/** Exports a course report in one representation. */
public interface ReportExporter {

	/**
	 * Returns the representation produced by this exporter.
	 * @return representation produced by this exporter
	 */
	ReportFormat format();

	/**
	 * Serializes a report.
	 * @param report report to serialize
	 * @return serialized bytes
	 * @throws IOException when serialization fails
	 */
	byte[] export(CourseReport report) throws IOException;

}
