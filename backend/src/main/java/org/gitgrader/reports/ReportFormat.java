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

import java.util.Locale;

/** Supported report export representations. */
public enum ReportFormat {

	/** Comma-separated text. */
	CSV("text/csv", "csv"),

	/** JavaScript Object Notation. */
	JSON("application/json", "json"),

	/** Office Open XML workbook. */
	XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

	private final String mediaType;

	private final String extension;

	ReportFormat(String mediaType, String extension) {
		this.mediaType = mediaType;
		this.extension = extension;
	}

	/**
	 * Returns the response media type.
	 * @return response media type
	 */
	public String mediaType() {
		return this.mediaType;
	}

	/**
	 * Returns the conventional file extension.
	 * @return conventional file extension
	 */
	public String extension() {
		return this.extension;
	}

	/**
	 * Parses a query parameter without depending on the ambient locale.
	 * @param value requested format
	 * @return parsed format
	 */
	public static ReportFormat parse(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
