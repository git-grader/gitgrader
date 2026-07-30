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

package org.gitgrader.security.internal;

import java.util.Optional;
import java.util.UUID;

import org.gitgrader.security.domain.ResultToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for result tokens.
 */
@Repository
public interface ResultTokenRepository extends JpaRepository<ResultToken, UUID> {

	/**
	 * Finds a token by its hash.
	 * @param tokenHash hashed token
	 * @return the token entity, if present
	 */
	Optional<ResultToken> findByTokenHash(String tokenHash);

}
