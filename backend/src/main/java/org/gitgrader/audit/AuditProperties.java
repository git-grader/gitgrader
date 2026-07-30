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

package org.gitgrader.audit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Audit retention and keyed address-hashing configuration.
 *
 * @param ipHashKey server-side HMAC key; blank selects an ephemeral startup key
 * @param retention duration for which audit events should be retained
 */
@ConfigurationProperties(prefix = "audit")
public record AuditProperties(@DefaultValue("") String ipHashKey, @DefaultValue("P365D") Duration retention) {
}
