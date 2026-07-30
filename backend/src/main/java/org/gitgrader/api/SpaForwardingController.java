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

package org.gitgrader.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page application for client-side routes.
 *
 * <p>
 * A student who opens a result link, or an instructor who reloads {@code /students/123},
 * sends that path to the server. Nothing on the backend maps it, so without this forward
 * they would get a 404 for a page that exists purely in the browser's router.
 *
 * <p>
 * The patterns are enumerated rather than expressed as a catch-all. A greedy {@code /**}
 * forward is the usual approach and it is a mistake here: it swallows {@code /api/**}
 * 404s and turns a genuine "no such endpoint" into an HTML page, which makes a typo in a
 * client call almost impossible to diagnose.
 */
@Controller
public class SpaForwardingController {

	/** Where every client-side route resolves; the built SPA shell. */
	private static final String FORWARD_TO_SHELL = "forward:/index.html";

	/**
	 * Forwards the instructor application's routes to the SPA shell.
	 * @return a forward to {@code index.html}
	 */
	@GetMapping({ "/", "/login", "/dashboard", "/students/**", "/courses/**", "/assignments/**", "/submissions/**",
			"/reports/**", "/admin/**" })
	public String instructorApp() {
		return FORWARD_TO_SHELL;
	}

	/**
	 * Forwards the public routes to the SPA shell.
	 *
	 * <p>
	 * {@code /result/**} is matched here as well as by its own security filter chain: the
	 * chain decides the headers, this decides what gets rendered.
	 * @return a forward to {@code index.html}
	 */
	@GetMapping({ "/register", "/register/**", "/result/**" })
	public String publicApp() {
		return FORWARD_TO_SHELL;
	}

}
