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

import java.util.List;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.ActorProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports who the caller is.
 *
 * <p>
 * The frontend uses this to decide which navigation entries to render. That is a
 * convenience only: every endpoint enforces its own authorization server side, so hiding
 * a menu item is never what keeps an instructor out of an administrator's screen.
 */
@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {

	private final ActorProvider actors;

	public CurrentUserController(ActorProvider actors) {
		this.actors = actors;
	}

	/**
	 * Returns the signed-in principal and their roles.
	 * @return the current user
	 */
	@GetMapping
	public CurrentUser me() {
		Actor actor = this.actors.currentActor();
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		List<String> roles = (authentication != null)
				? authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList()
				: List.of();
		return new CurrentUser(actor.id(), actor.displayName(), actor.type().name(), roles);
	}

	/**
	 * The signed-in principal as the frontend sees it.
	 *
	 * @param username stable identifier of the principal
	 * @param displayName name shown in the UI
	 * @param actorType which kind of principal this is
	 * @param roles granted authorities, including the {@code ROLE_} prefix
	 */
	public record CurrentUser(String username, String displayName, String actorType, List<String> roles) {

		public CurrentUser {
			roles = List.copyOf(roles);
		}

	}

}
