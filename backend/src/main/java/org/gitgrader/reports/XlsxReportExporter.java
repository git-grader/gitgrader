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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import java.util.function.Function;

import org.gitgrader.grading.SubmissionScoreView;
import org.gitgrader.reports.ClassProgressReport.AssignmentProgress;
import org.gitgrader.reports.ClassProgressReport.LatestSubmission;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Exports course progress as an Office Open XML workbook. */
@Component
public class XlsxReportExporter implements ReportExporter {

	private static final List<String> HEADERS = List.of("Student ID", "Student ID / Username", "Full name",
			"Fully completed", "Partially completed", "Not started", "Completion rate", "Points earned", "Points rate",
			"Total points", "Submission count", "Last activity");

	private static final List<Function<StudentProgressRow, String>> VALUES = List.of(
			(row) -> row.studentId().toString(), StudentProgressRow::studentUsername, StudentProgressRow::fullName,
			(row) -> Integer.toString(row.fullyCompleted()), (row) -> Integer.toString(row.partiallyCompleted()),
			(row) -> Integer.toString(row.notStarted()), (row) -> row.completionRate().toPlainString(),
			(row) -> row.pointsEarned().toPlainString(), (row) -> row.pointsRate().toPlainString(),
			(row) -> row.totalPoints().toPlainString(), (row) -> Long.toString(row.submissionCount()),
			(row) -> formatInstant(row.lastActivityAt()));

	private static final List<String> CLASS_HEADERS = List.of("Student ID", "Student ID / Username", "Full name",
			"Student status", "Enrollment status", "Fully completed", "Partially completed", "Not started",
			"Completion rate", "Points earned", "Points rate", "Total points", "Submission count", "Last activity");

	/**
	 * Columns that assignmentHeaders emits per assignment, and that assignmentValues
	 * fills.
	 */
	private static final int ASSIGNMENT_COLUMN_COUNT = 9;

	// One blank cell per column that assignmentHeaders emits, in the same order.
	private static final List<String> BLANK_ASSIGNMENT_VALUES = List.of("", "", "", "", "", "", "", "", "");

	private static final List<String> BLANK_SUBMISSION_VALUES = List.of("", "");

	private static final List<String> BLANK_GRADING_VALUES = List.of("", "", "", "", "");

	/**
	 * Renders a nullable instant for a spreadsheet cell.
	 * @param instant the value, possibly absent
	 * @return the ISO representation, or an empty cell
	 */
	private static String formatInstant(@Nullable Instant instant) {
		return (instant != null) ? instant.toString() : "";
	}

	@Override
	public ExportFormat format() {
		return ExportFormat.XLSX;
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

	@Override
	public byte[] export(ClassProgressReport report) throws IOException {
		List<String> headers = new ArrayList<>(CLASS_HEADERS);
		for (ClassProgressReport.AssignmentSummary assignment : report.assignments()) {
			headers.addAll(assignmentHeaders(assignment.assignmentKey()));
		}
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Class report");
			Row header = sheet.createRow(0);
			for (int column = 0; column < headers.size(); column++) {
				header.createCell(column).setCellValue(headers.get(column));
			}
			for (int index = 0; index < report.students().size(); index++) {
				ClassProgressReport.StudentRow student = report.students().get(index);
				Row row = sheet.createRow(index + 1);
				List<String> values = new ArrayList<>(List.of(student.studentId().toString(), student.studentUsername(),
						student.fullName(), student.status().name(), student.enrollmentStatus().name(),
						Integer.toString(student.fullyCompleted()), Integer.toString(student.partiallyCompleted()),
						Integer.toString(student.notStarted()), student.completionRate().toPlainString(),
						student.pointsEarned().toPlainString(), student.pointsRate().toPlainString(),
						student.totalPoints().toPlainString(), Long.toString(student.submissionCount()),
						formatInstant(student.lastActivityAt())));
				for (ClassProgressReport.AssignmentSummary assignment : report.assignments()) {
					values.addAll(assignmentValues(student.assignments().get(assignment.assignmentKey())));
				}
				for (int column = 0; column < values.size(); column++) {
					row.createCell(column).setCellValue(values.get(column));
				}
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private static List<String> assignmentHeaders(String assignmentKey) {
		return List.of(assignmentKey + " Best percent", assignmentKey + " Best points",
				assignmentKey + " Latest submission", assignmentKey + " Latest submission status",
				assignmentKey + " Latest score percent", assignmentKey + " Latest points awarded",
				assignmentKey + " Tests passed", assignmentKey + " Tests total",
				assignmentKey + " Latest grading status");
	}

	private static List<String> assignmentValues(@Nullable AssignmentProgress progress) {
		if (progress == null) {
			return BLANK_ASSIGNMENT_VALUES;
		}
		List<String> values = new ArrayList<>(ASSIGNMENT_COLUMN_COUNT);
		values.add(progress.bestPercent().toPlainString());
		values.add(progress.bestPoints().toPlainString());
		values.addAll(submissionValues(progress.latestSubmission()));
		values.addAll(gradingValues(progress.latestGrading()));
		return List.copyOf(values);
	}

	private static List<String> submissionValues(@Nullable LatestSubmission submission) {
		if (submission == null) {
			return BLANK_SUBMISSION_VALUES;
		}
		return List.of(formatInstant(submission.receivedAt()), submission.status().name());
	}

	private static List<String> gradingValues(@Nullable SubmissionScoreView grading) {
		if (grading == null) {
			return BLANK_GRADING_VALUES;
		}
		BigDecimal percent = grading.scorePercent();
		BigDecimal pointsAwarded = grading.pointsAwarded();
		return List.of((percent != null) ? percent.toPlainString() : "",
				(pointsAwarded != null) ? pointsAwarded.toPlainString() : "", Integer.toString(grading.testsPassed()),
				Integer.toString(grading.testsTotal()), grading.status().name());
	}

}
