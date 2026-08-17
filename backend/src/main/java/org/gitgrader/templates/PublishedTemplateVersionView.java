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

package org.gitgrader.templates;

import java.util.UUID;

/**
 * A published template version together with the name of the template it belongs to.
 *
 * <p>
 * Carries the owning template's name because the only consumer is a picker that labels
 * each choice "template — version". Fetching the templates and then their versions
 * separately is what made that picker issue one request per template.
 *
 * @param id version identifier, the value an assignment stores
 * @param templateName name of the owning template
 * @param versionLabel label the instructor gave this version
 */
public record PublishedTemplateVersionView(UUID id, String templateName, String versionLabel) {
}
