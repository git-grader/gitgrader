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

package org.gitgrader.runtimes.internal;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.gitgrader.runtimes.RuntimeAdministration;
import org.gitgrader.runtimes.RuntimeCatalog;
import org.gitgrader.runtimes.RuntimeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default runtime catalog and administration implementation. */
@Service
@Transactional
public class DefaultRuntimeService implements RuntimeCatalog, RuntimeAdministration {

	private final RuntimeRepository runtimes;

	private final Clock clock;

	DefaultRuntimeService(RuntimeRepository runtimes, Clock clock) {
		this.runtimes = runtimes;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<RuntimeView> findRuntime(UUID id) {
		return this.runtimes.findById(id).map(org.gitgrader.runtimes.domain.RuntimeDefinition::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RuntimeView> findAll() {
		return this.runtimes.findAllByOrderByRuntimeKeyAsc()
			.stream()
			.map(org.gitgrader.runtimes.domain.RuntimeDefinition::toView)
			.toList();
	}

	@Override
	public RuntimeView create(org.gitgrader.runtimes.RuntimeDefinition definition) {
		return this.runtimes.save(new org.gitgrader.runtimes.domain.RuntimeDefinition(definition, this.clock)).toView();
	}

	@Override
	public RuntimeView update(UUID id, org.gitgrader.runtimes.RuntimeDefinition definition) {
		org.gitgrader.runtimes.domain.RuntimeDefinition runtime = this.runtimes.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Runtime not found: " + id));
		runtime.update(definition, this.clock);
		return this.runtimes.save(runtime).toView();
	}

}
