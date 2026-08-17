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

package org.gitgrader.runtimes.web;

import java.util.List;

import jakarta.validation.Valid;

import org.gitgrader.runtimes.RuntimeAdministration;
import org.gitgrader.runtimes.RuntimeCatalog;
import org.gitgrader.runtimes.NewRuntime;
import org.gitgrader.runtimes.RuntimeView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Serves runtime reads and administrator-only writes. */
@RestController
@RequestMapping("/api/v1/runtimes")
public class RuntimeController {

	private final RuntimeCatalog catalog;

	private final RuntimeAdministration administration;

	public RuntimeController(RuntimeCatalog catalog, RuntimeAdministration administration) {
		this.catalog = catalog;
		this.administration = administration;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
	public List<RuntimeView> list() {
		return this.catalog.findAll();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public RuntimeView create(@Valid @RequestBody NewRuntime definition) {
		return this.administration.create(definition);
	}

}
