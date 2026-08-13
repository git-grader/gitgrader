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

package org.gitgrader.security;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import org.gitgrader.audit.ClientAddressHasher;
import org.gitgrader.configuration.SecurityProperties;
import org.gitgrader.configuration.SecurityProperties.RateLimits;
import org.springframework.stereotype.Component;

/**
 * Enforces rate limits for public endpoints.
 */
@Component
public class RateLimiter {

	/**
	 * Ceiling on how many distinct addresses are tracked per limit.
	 *
	 * <p>
	 * Every unseen address allocates a bucket, and expiry alone bounds only how long one
	 * lives, not how many exist at once. A burst from many sources - a botnet, or simply
	 * a wide IPv6 range - would otherwise grow these maps until the process runs out of
	 * memory, which is a denial of service reached through the very component meant to
	 * prevent one. Caffeine evicts least-recently-used entries past this point: a
	 * displaced attacker gets a fresh allowance, which is a far better failure than the
	 * application dying.
	 */
	private static final long MAX_TRACKED_ADDRESSES = 100_000L;

	private final ClientAddressHasher hasher;

	private final Cache<String, Bucket> registrationPerIp;

	private final Cache<String, Bucket> registrationGlobal;

	private final Cache<String, Bucket> resultLookupPerIp;

	private final Cache<String, Bucket> loginPerIp;

	private final Cache<String, Bucket> sshAuthPerIp;

	private final RateLimits limits;

	public RateLimiter(ClientAddressHasher hasher, SecurityProperties securityProperties) {
		this.hasher = hasher;
		this.limits = securityProperties.rateLimits();

		Duration duration = this.limits.blockDuration();

		this.registrationPerIp = perAddressCache(duration);
		this.registrationGlobal = Caffeine.newBuilder().expireAfterAccess(duration).build();
		this.resultLookupPerIp = perAddressCache(duration);
		this.loginPerIp = perAddressCache(duration);
		this.sshAuthPerIp = perAddressCache(duration);
	}

	private static Cache<String, Bucket> perAddressCache(Duration duration) {
		return Caffeine.newBuilder().maximumSize(MAX_TRACKED_ADDRESSES).expireAfterAccess(duration).build();
	}

	public boolean tryConsumeRegistrationPerIp(String clientAddress) {
		return this.registrationPerIp
			.get(this.hasher.hash(clientAddress),
					(k) -> createBucket(this.limits.registrationPerHourPerIp(), Duration.ofHours(1)))
			.tryConsume(1);
	}

	public boolean tryConsumeRegistrationGlobal() {
		return this.registrationGlobal
			.get("GLOBAL", (k) -> createBucket(this.limits.registrationPerHourGlobal(), Duration.ofHours(1)))
			.tryConsume(1);
	}

	public boolean tryConsumeResultLookupPerIp(String clientAddress) {
		return this.resultLookupPerIp
			.get(this.hasher.hash(clientAddress),
					(k) -> createBucket(this.limits.resultLookupPerMinutePerIp(), Duration.ofMinutes(1)))
			.tryConsume(1);
	}

	public boolean tryConsumeLoginPerIp(String clientAddress) {
		return this.loginPerIp
			.get(this.hasher.hash(clientAddress),
					(k) -> createBucket(this.limits.loginPerMinutePerIp(), Duration.ofMinutes(1)))
			.tryConsume(1);
	}

	public boolean tryConsumeSshAuthPerIp(String clientAddress) {
		return this.sshAuthPerIp
			.get(this.hasher.hash(clientAddress),
					(k) -> createBucket(this.limits.sshAuthPerMinutePerIp(), Duration.ofMinutes(1)))
			.tryConsume(1);
	}

	private Bucket createBucket(long capacity, Duration period) {
		Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
		return Bucket.builder().addLimit(limit).build();
	}

}
