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

package org.gitgrader.templates.web;

import java.nio.file.Path;

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

/** Decides whether a single ZIP entry may be written, and where. */
final class ZipEntrySafety {

	private ZipEntrySafety() {
	}

	/**
	 * Resolves an entry against the destination and refuses anything unsafe.
	 * @param entry archive entry under consideration
	 * @param destination directory the archive is being written into
	 * @return the resolved output path, guaranteed to stay inside the destination
	 */
	static Path resolve(ZipArchiveEntry entry, Path destination) {
		String name = entry.getName();
		if (name == null || name.isBlank()) {
			throw new ArchiveUploadException("The ZIP archive contains an entry with a blank name.");
		}
		String portableName = name.replace('\\', '/');
		Path relative = Path.of(portableName);
		if (hasUnsafePath(portableName, relative)) {
			throw new ArchiveUploadException("The ZIP archive contains an unsafe path.");
		}
		Path output = destination.resolve(relative).normalize();
		if (!output.startsWith(destination)) {
			throw new ArchiveUploadException("The ZIP archive contains an unsafe path.");
		}
		if (!isRegularEntry(entry)) {
			throw new ArchiveUploadException("The ZIP archive contains a non-regular entry.");
		}
		return output;
	}

	private static boolean hasUnsafePath(String portableName, Path relative) {
		return relative.isAbsolute() || portableName.startsWith("/") || portableName.matches("^[a-zA-Z]:/.*")
				|| hasParentSegment(relative);
	}

	private static boolean isRegularEntry(ZipArchiveEntry entry) {
		if (entry.isUnixSymlink()) {
			return false;
		}
		int mode = entry.getUnixMode();
		if (mode == 0) {
			return true;
		}
		// Plenty of archives carry permission bits without the file-type bits that
		// would say what the entry is: anything written by Python's zipfile, and
		// most things produced on Windows. Absent that information the entry is an
		// ordinary file, and refusing it would reject archives instructors really do
		// upload. Entries that do declare a type still have to declare a sane one.
		int type = mode & UnixStat.FILE_TYPE_FLAG;
		if (type == 0) {
			return true;
		}
		return entry.isDirectory() ? type == UnixStat.DIR_FLAG : type == UnixStat.FILE_FLAG;
	}

	private static boolean hasParentSegment(Path path) {
		for (Path segment : path) {
			if ("..".equals(segment.toString())) {
				return true;
			}
		}
		return false;
	}

}
