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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Selects the exporter matching a requested format. */
@Service
public class ReportExportService {

	private final Map<ReportFormat, ReportExporter> exporters;

	public ReportExportService(List<ReportExporter> exporters) {
		Map<ReportFormat, ReportExporter> indexed = new EnumMap<>(ReportFormat.class);
		exporters.forEach((exporter) -> indexed.put(exporter.format(), exporter));
		this.exporters = Map.copyOf(indexed);
	}

	/**
	 * Exports a report in the requested representation.
	 * @param report report to export
	 * @param format requested representation
	 * @return serialized report
	 * @throws IOException when serialization fails
	 */
	public byte[] export(CourseReport report, ReportFormat format) throws IOException {
		ReportExporter exporter = this.exporters.get(format);
		if (exporter == null) {
			throw new IllegalArgumentException("Unsupported report format");
		}
		return exporter.export(report);
	}

}
