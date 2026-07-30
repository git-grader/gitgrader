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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Exports course progress as an Office Open XML workbook. */
@Component
public class XlsxReportExporter implements ReportExporter {

	private static final List<String> HEADERS = List.of("Student ID", "Student number", "Full name", "Fully completed",
			"Partially completed", "Not started", "Completion rate", "Points earned", "Points rate", "Total points",
			"Submission count", "Last activity");

	private static final List<Function<StudentProgressRow, String>> VALUES = List.of(
			(row) -> row.studentId().toString(), StudentProgressRow::studentNumber, StudentProgressRow::fullName,
			(row) -> Integer.toString(row.fullyCompleted()), (row) -> Integer.toString(row.partiallyCompleted()),
			(row) -> Integer.toString(row.notStarted()), (row) -> row.completionRate().toPlainString(),
			(row) -> row.pointsEarned().toPlainString(), (row) -> row.pointsRate().toPlainString(),
			(row) -> row.totalPoints().toPlainString(), (row) -> Long.toString(row.submissionCount()),
			(row) -> formatInstant(row.lastActivityAt()));

	/**
	 * Renders a nullable instant for a spreadsheet cell.
	 * @param instant the value, possibly absent
	 * @return the ISO representation, or an empty cell
	 */
	private static String formatInstant(@Nullable Instant instant) {
		return (instant != null) ? instant.toString() : "";
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] export(CourseReport report) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Course report");
			Row header = sheet.createRow(0);
			for (int column = 0; column < HEADERS.size(); column++) {
				header.createCell(column).setCellValue(HEADERS.get(column));
			}
			for (int index = 0; index < report.students().size(); index++) {
				Row row = sheet.createRow(index + 1);
				StudentProgressRow progress = report.students().get(index);
				for (int column = 0; column < VALUES.size(); column++) {
					row.createCell(column).setCellValue(VALUES.get(column).apply(progress));
				}
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

}
