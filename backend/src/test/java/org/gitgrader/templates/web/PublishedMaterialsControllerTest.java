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

import java.util.List;
import java.util.UUID;

import org.gitgrader.templates.PublishedTemplateVersionView;
import org.gitgrader.templates.PublishedTestSuiteVersionView;
import org.gitgrader.templates.TemplateCatalog;
import org.gitgrader.templates.TestSuiteCatalog;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The assignment form's choices must arrive in one response, not one per template.
 */
class PublishedMaterialsControllerTest {

	@Test
	void returnsEveryPublishedChoiceInOneResponse() throws Exception {
		TemplateCatalog templates = mock(TemplateCatalog.class);
		TestSuiteCatalog suites = mock(TestSuiteCatalog.class);
		UUID templateVersion = UUID.randomUUID();
		UUID suiteVersion = UUID.randomUUID();
		when(templates.findPublishedVersions())
			.thenReturn(List.of(new PublishedTemplateVersionView(templateVersion, "Java Intro", "v1")));
		when(suites.findPublishedVersions())
			.thenReturn(List.of(new PublishedTestSuiteVersionView(suiteVersion, "Java Intro Tests", "v2", 7, 3)));

		mockMvc(templates, suites).perform(get("/api/v1/materials/published"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.templateVersions[0].id").value(templateVersion.toString()))
			.andExpect(jsonPath("$.templateVersions[0].templateName").value("Java Intro"))
			.andExpect(jsonPath("$.templateVersions[0].versionLabel").value("v1"))
			.andExpect(jsonPath("$.suiteVersions[0].id").value(suiteVersion.toString()))
			.andExpect(jsonPath("$.suiteVersions[0].suiteName").value("Java Intro Tests"))
			.andExpect(jsonPath("$.suiteVersions[0].hiddenTestCount").value(7))
			.andExpect(jsonPath("$.suiteVersions[0].publicTestCount").value(3));

		// The point of the endpoint: the catalogs are asked once each, whatever the
		// number of templates and suites behind them.
		verify(templates).findPublishedVersions();
		verify(suites).findPublishedVersions();
	}

	@Test
	void reportsAnEmptyCatalogAsEmptyListsRatherThanNull() throws Exception {
		TemplateCatalog templates = mock(TemplateCatalog.class);
		TestSuiteCatalog suites = mock(TestSuiteCatalog.class);
		when(templates.findPublishedVersions()).thenReturn(List.of());
		when(suites.findPublishedVersions()).thenReturn(List.of());

		mockMvc(templates, suites).perform(get("/api/v1/materials/published"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.templateVersions").isEmpty())
			.andExpect(jsonPath("$.suiteVersions").isEmpty());
	}

	private static MockMvc mockMvc(TemplateCatalog templates, TestSuiteCatalog suites) {
		return MockMvcBuilders.standaloneSetup(new PublishedMaterialsController(templates, suites)).build();
	}

}
