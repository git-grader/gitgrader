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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gitgrader.api.GlobalExceptionHandler;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.templates.ProjectTemplateView;
import org.gitgrader.templates.TemplateAdministration;
import org.gitgrader.templates.TemplateCatalog;
import org.gitgrader.templates.TemplateContentGuard;
import org.gitgrader.templates.TemplateContentRejectedException;
import org.gitgrader.templates.TemplateVersionView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateControllerTest {

	@TempDir
	private Path temporaryDirectory;

	@Test
	void createUploadAndPublishUseServerOwnedStorageAndActor() throws Exception {
		TemplateCatalog catalog = mock(TemplateCatalog.class);
		TemplateAdministration administration = mock(TemplateAdministration.class);
		UUID templateId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		ProjectTemplateView template = new ProjectTemplateView(templateId, "java-intro", "Java Intro", null);
		TemplateVersionView version = version(versionId, templateId, "v1", "java-intro/v1");
		when(administration.createTemplate("java-intro", "Java Intro", null)).thenReturn(templateId);
		when(catalog.findTemplate(templateId)).thenReturn(Optional.of(template));
		when(administration.createVersion(templateId, "v1", "java-intro/v1")).thenReturn(version);
		when(administration.publish(versionId, "teacher")).thenReturn(version);
		MockMvc mockMvc = mockMvc(catalog, administration);

		mockMvc
			.perform(post("/api/v1/templates").contentType("application/json")
				.content("{\"templateKey\":\"java-intro\",\"name\":\"Java Intro\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(templateId.toString()));
		mockMvc
			.perform(multipart("/api/v1/templates/{id}/versions", templateId)
				.file(new MockMultipartFile("file", "template.zip", "application/zip", zip("README.md", "Hello")))
				.param("versionLabel", "v1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.storagePath").value("java-intro/v1"));
		mockMvc.perform(post("/api/v1/templates/versions/{id}/publish", versionId).principal(() -> "teacher"))
			.andExpect(status().isOk());

		verify(administration).createVersion(templateId, "v1", "java-intro/v1");
		verify(administration).publish(versionId, "teacher");
	}

	@Test
	void contentGuardRejectionReturns400() throws Exception {
		TemplateAdministration administration = mock(TemplateAdministration.class);
		UUID versionId = UUID.randomUUID();
		when(administration.publish(versionId, "teacher"))
			.thenThrow(new TemplateContentRejectedException("Template contains forbidden material."));

		mockMvc(mock(TemplateCatalog.class), administration)
			.perform(post("/api/v1/templates/versions/{id}/publish", versionId).principal(() -> "teacher"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Template contains forbidden material."));
	}

	private MockMvc mockMvc(TemplateCatalog catalog, TemplateAdministration administration) {
		StorageProperties storage = new StorageProperties(this.temporaryDirectory.resolve("repositories").toString(),
				this.temporaryDirectory.resolve("templates").toString(),
				this.temporaryDirectory.resolve("tests").toString(),
				this.temporaryDirectory.resolve("artifacts").toString(),
				this.temporaryDirectory.resolve("tmp").toString());
		return MockMvcBuilders
			.standaloneSetup(new TemplateController(catalog, administration, storage, new SecureZipExtractor(),
					new TemplateContentGuard()))
			.setControllerAdvice(new GlobalExceptionHandler(), new ArchiveUploadExceptionHandler())
			.build();
	}

	private static TemplateVersionView version(UUID versionId, UUID templateId, String label, String path) {
		return new TemplateVersionView(versionId, templateId, label, path, "", 0, 0, null, null,
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private static byte[] zip(String name, String content) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry(name));
			zip.write(content.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

}
