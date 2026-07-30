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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/** Exports course progress as RFC 4180-style comma-separated text. */
@Component
public class CsvReportExporter implements ReportExporter {

	private static final String LINE_ENDING = "\r\n";

	@Override
	public ReportFormat format() {
		return ReportFormat.CSV;
	}

	@Override
	public byte[] export(CourseReport report) {
		StringBuilder csv = new StringBuilder();
		appendRow(csv,
				List.of("studentId", "studentNumber", "fullName", "fullyCompleted", "partiallyCompleted", "notStarted",
						"completionRate", "pointsEarned", "pointsRate", "totalPoints", "submissionCount",
						"lastActivityAt"));
		for (StudentProgressRow row : report.students()) {
			appendRow(csv,
					List.of(row.studentId().toString(), row.studentNumber(), row.fullName(),
							Integer.toString(row.fullyCompleted()), Integer.toString(row.partiallyCompleted()),
							Integer.toString(row.notStarted()), row.completionRate().toPlainString(),
							row.pointsEarned().toPlainString(), row.pointsRate().toPlainString(),
							row.totalPoints().toPlainString(), Long.toString(row.submissionCount()),
							formatInstant(row.lastActivityAt())));
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Renders a nullable instant for a spreadsheet cell.
	 *
	 * <p>
	 * Reads the value once. Null-checking one call to a nullable accessor and then
	 * dereferencing a second call is a pattern static analysis rightly rejects, because
	 * nothing guarantees the two calls agree.
	 * @param instant the value, possibly absent
	 * @return the ISO representation, or an empty cell
	 */
	private static String formatInstant(@Nullable Instant instant) {
		return (instant != null) ? instant.toString() : "";
	}

	private static void appendRow(StringBuilder csv, List<String> values) {
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				csv.append(',');
			}
			csv.append(quote(values.get(index)));
		}
		csv.append(LINE_ENDING);
	}

	private static String quote(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

}
