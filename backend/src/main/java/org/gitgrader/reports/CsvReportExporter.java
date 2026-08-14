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

	/**
	 * Leading characters a spreadsheet reads as the start of a formula.
	 *
	 * <p>
	 * The tab and carriage return are here because Excel skips leading whitespace before
	 * deciding, so they smuggle any of the others past a check that looks only at the
	 * first character.
	 */
	private static final String FORMULA_LEADS = "=+-@\t\r";

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
			csv.append(quote(neutraliseFormula(values.get(index))));
		}
		csv.append(LINE_ENDING);
	}

	/**
	 * Keeps a cell from being read as a formula by the spreadsheet that opens it.
	 *
	 * <p>
	 * A student's name reaches this file exactly as they typed it at registration, which
	 * is a public endpoint that constrains a name's length and nothing else. A name
	 * beginning {@code =}, {@code +}, {@code -} or {@code @} is a formula to Excel,
	 * LibreOffice and Sheets, and an export exists to be opened: {@code HYPERLINK} and
	 * {@code WEBSERVICE} send the row's contents to whoever wrote the name, and DDE has
	 * historically reached the shell.
	 *
	 * <p>
	 * The RFC quoting below does not help, because the spreadsheet removes those quotes
	 * before deciding what the cell is. A leading apostrophe is what forces text, and it
	 * is not displayed. Applied to every cell rather than to the names alone: the numeric
	 * columns are all counts and non-negative ratios this class formats itself, so none
	 * can legitimately begin with one of these characters, and a guard that has to be
	 * remembered per column is one that will eventually be forgotten.
	 * @param value the cell content
	 * @return the content, prefixed so that it stays text
	 */
	private static String neutraliseFormula(String value) {
		if (value.isEmpty() || FORMULA_LEADS.indexOf(value.charAt(0)) < 0) {
			return value;
		}
		return "'" + value;
	}

	private static String quote(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

}
