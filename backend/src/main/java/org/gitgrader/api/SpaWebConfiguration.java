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

import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * Serves the built frontend and answers client-side routes with its shell.
 *
 * <p>
 * A student who opens a result link, or an instructor who reloads {@code /students/123},
 * sends that path to the server. Nothing on the backend maps it, so without a fallback
 * they get a 404 for a page that exists purely in the browser's router.
 *
 * <p>
 * This replaced a controller that listed the client-side routes one by one. The list was
 * a second copy of the router that nobody remembered to update: {@code /materials} was
 * missing for a release, and the frontend's own "page not found" screen could never be
 * reached because an unlisted address was answered with a JSON 404 instead. Deciding by
 * shape rather than by name removes the copy, so a route added to the router works on
 * reload without anyone touching Java.
 *
 * <p>
 * The list existed to protect one property, and that property is kept here: a mistyped
 * API call must stay diagnosable. {@code /api} and {@code /actuator} are answered before
 * the fallback is considered, so they still produce the ordinary "no such endpoint" 404
 * rather than an HTML page a client would try to parse as JSON. Requests that name a file
 * are excluded for the same reason - a missing script answered with the shell would load
 * as HTML under a 200 and fail somewhere far from the cause.
 *
 * <p>
 * Nothing here decides who may see a page. The security filter chain runs first and picks
 * its chain from the address as it was requested, so an anonymous reload of
 * {@code /students/123} is still sent to the sign-in page and {@code /result/**} still
 * carries its stricter policy. Serving the shell as a resource rather than forwarding to
 * it keeps that true, because a forward would be dispatched again and could be authorized
 * against {@code /index.html} instead of the address the browser actually asked for.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfiguration implements WebMvcConfigurer {

	/** Where the bundler writes its content-hashed output. */
	private static final String ASSETS_PATTERN = "/assets/**";

	private static final String[] STATIC_LOCATIONS = { "classpath:/META-INF/resources/", "classpath:/resources/",
			"classpath:/static/", "classpath:/public/" };

	/** Namespaces that must keep answering with their own 404 rather than the shell. */
	private static final String[] RESERVED_ROOTS = { "api", "actuator" };

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Every name under /assets carries a hash of its own content, so a changed file
		// is a changed URL and the old one can be kept for as long as a browser likes.
		registry.addResourceHandler(ASSETS_PATTERN)
			.addResourceLocations("classpath:/static/assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

		// The shell is the one file whose name never changes, so caching it is how a
		// browser ends up running last month's application against this month's API. It
		// is small, and revalidating it is what makes a deployment take effect.
		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC_LOCATIONS)
			.setCacheControl(CacheControl.noCache())
			.resourceChain(false)
			.addResolver(new SpaResourceResolver());
	}

	/**
	 * Resolves a real file when there is one, and the application shell when the address
	 * belongs to the browser's router.
	 */
	private static final class SpaResourceResolver extends PathResourceResolver {

		private static final String SHELL = "index.html";

		@Override
		protected @Nullable Resource resolveResourceInternal(@Nullable HttpServletRequest request, String requestPath,
				List<? extends Resource> locations, ResourceResolverChain chain) {

			String path = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
			if (isReserved(path)) {
				return null;
			}

			Resource resource = super.resolveResourceInternal(request, requestPath, locations, chain);
			if (resource != null || request == null || !isNavigation(request) || namesAFile(path)) {
				return resource;
			}

			return super.resolveResourceInternal(request, SHELL, locations, chain);
		}

		private static boolean isReserved(String path) {
			for (String root : RESERVED_ROOTS) {
				if (path.equals(root) || path.startsWith(root + "/")) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Whether the address asks for a file rather than for a page.
		 *
		 * <p>
		 * Only the last segment is examined. A page address may well contain a dot
		 * further up - a course key or a result token is free to - and treating those as
		 * files would answer a real page with a 404.
		 * @param path the requested path, without its leading slash
		 * @return true when the last segment carries an extension
		 */
		private static boolean namesAFile(String path) {
			return path.substring(path.lastIndexOf('/') + 1).indexOf('.') >= 0;
		}

		private static boolean isNavigation(HttpServletRequest request) {
			return HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
		}

	}

}
