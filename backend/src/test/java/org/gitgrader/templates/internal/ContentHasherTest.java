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

package org.gitgrader.templates.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

	@TempDir
	private Path directory;

	@Test
	void hashIncludesSortedFileNamesAndContentsWithAccurateStatistics() throws IOException {
		Path first = Files.createDirectories(this.directory.resolve("nested")).resolve("a.txt");
		Files.writeString(first, "abc");
		Files.writeString(this.directory.resolve("z.txt"), "de");
		ContentSnapshot initial = ContentHasher.snapshot(this.directory);

		assertThat(initial.fileCount()).isEqualTo(2);
		assertThat(initial.totalBytes()).isEqualTo(5);
		assertThat(initial.hash()).hasSize(64);

		Files.move(first, first.resolveSibling("b.txt"));
		assertThat(ContentHasher.snapshot(this.directory).hash()).isNotEqualTo(initial.hash());
	}

}
