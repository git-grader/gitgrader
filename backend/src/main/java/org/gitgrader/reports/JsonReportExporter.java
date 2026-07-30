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

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** Exports a course report as JSON using the application's Jackson 3 mapper. */
@Component
public class JsonReportExporter implements ReportExporter {

	private final ObjectMapper objectMapper;

	public JsonReportExporter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.JSON;
	}

	@Override
	public byte[] export(CourseReport report) throws IOException {
		return this.objectMapper.writeValueAsBytes(report);
	}

}
