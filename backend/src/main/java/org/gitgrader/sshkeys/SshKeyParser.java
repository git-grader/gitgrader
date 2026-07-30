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

package org.gitgrader.sshkeys;

import java.security.PublicKey;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.gitgrader.configuration.GitProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The single gate through which all submitted SSH key material passes.
 *
 * <p>
 * <strong>Order of checks is deliberate.</strong> The private-key test runs first, on the
 * raw text, before parsing and before anything is logged or persisted. A student who
 * pastes a private key into a public web form has already made a mistake; the platform's
 * job at that point is to refuse it without ever writing it down.
 *
 * <p>
 * Parsing itself is delegated to Apache MINA SSHD rather than hand-rolled. The
 * {@code authorized_keys} format has enough edge cases - options prefixes, base64
 * padding, certificate key types, security-key algorithms - that a bespoke parser would
 * be a steady source of bugs, and this project does not write its own cryptography.
 */
@Component
public class SshKeyParser {

	/**
	 * Markers that identify PRIVATE key material.
	 *
	 * <p>
	 * Covers OpenSSH's own format, the PEM formats emitted by {@code ssh-keygen -m PEM}
	 * and by OpenSSL, and PuTTY's {@code .ppk}. The list is matched case-insensitively
	 * against the whole input, so a private key surrounded by explanatory text is still
	 * caught.
	 */
	private static final List<String> PRIVATE_KEY_MARKERS = List.of("-----BEGIN OPENSSH PRIVATE KEY-----",
			"-----BEGIN RSA PRIVATE KEY-----", "-----BEGIN DSA PRIVATE KEY-----", "-----BEGIN EC PRIVATE KEY-----",
			"-----BEGIN PRIVATE KEY-----", "-----BEGIN ENCRYPTED PRIVATE KEY-----", "PRIVATE KEY-----",
			"PUTTY-USER-KEY-FILE");

	/**
	 * Algorithms refused regardless of configuration.
	 *
	 * <p>
	 * Only DSA. The protocol caps {@code ssh-dss} at 1024-bit DSA and OpenSSH has had it
	 * disabled by default for years.
	 *
	 * <p>
	 * <strong>Note on RSA.</strong> {@code ssh-rsa} is deliberately NOT in this set. In
	 * the {@code authorized_keys} format {@code ssh-rsa} is the name of the key blob, and
	 * every RSA key is written that way - including a fresh 4096-bit one.
	 * {@code rsa-sha2-256} and {@code rsa-sha2-512} are <em>signature algorithm</em>
	 * names, not blob types, and never appear in a public key file. Rejecting
	 * {@code ssh-rsa} here would therefore reject all RSA keys, not just SHA-1
	 * signatures. The SHA-1 concern is real but belongs at verification time, which is
	 * where {@code SshSignatureVerifier} enforces a SHA-2 signature algorithm.
	 */
	private static final Set<String> ALWAYS_REJECTED = Set.of("ssh-dss");

	/** Longest input accepted, before any parsing. */
	private static final int MAX_INPUT_LENGTH = 16_384;

	private final GitProperties gitProperties;

	public SshKeyParser(GitProperties gitProperties) {
		this.gitProperties = gitProperties;
	}

	/**
	 * Parses and validates one submitted SSH public key.
	 * @param submitted raw text exactly as the user supplied it
	 * @return the validated key with its derived fingerprint
	 * @throws SshKeyRejectedException if the material is not an acceptable public key
	 */
	public SshPublicKey parse(String submitted) {
		String text = rejectPrivateKeyMaterial(submitted);
		String singleLine = requireSingleEntry(text);
		AuthorizedKeyEntry entry = parseEntry(singleLine);
		PublicKey publicKey = resolve(entry);
		String keyType = resolveKeyType(entry, publicKey);

		requireAllowedAlgorithm(keyType);
		Integer keyBits = requireSufficientStrength(publicKey, keyType);

		String fingerprint = KeyUtils.getFingerPrint(publicKey);
		if (!StringUtils.hasText(fingerprint)) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.MALFORMED);
		}

		String comment = StringUtils.hasText(entry.getComment()) ? entry.getComment().trim() : null;
		return new SshPublicKey(keyType, normalise(keyType, entry), fingerprint, keyBits, comment, publicKey);
	}

	/**
	 * Computes the OpenSSH SHA-256 fingerprint of an already parsed key.
	 * @param publicKey the key
	 * @return fingerprint in {@code SHA256:...} form
	 */
	public String fingerprintOf(PublicKey publicKey) {
		return KeyUtils.getFingerPrint(publicKey);
	}

	/**
	 * Refuses private key material and normalises whitespace.
	 *
	 * <p>
	 * Runs before everything else and never includes the input in the thrown exception.
	 * @param submitted the raw submitted text
	 * @return the trimmed text, guaranteed to contain no private key marker
	 */
	private String rejectPrivateKeyMaterial(String submitted) {
		if (!StringUtils.hasText(submitted)) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.EMPTY);
		}
		if (submitted.length() > MAX_INPUT_LENGTH) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.MALFORMED);
		}
		String upper = submitted.toUpperCase(Locale.ROOT);
		for (String marker : PRIVATE_KEY_MARKERS) {
			if (upper.contains(marker)) {
				throw new SshKeyRejectedException(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED);
			}
		}
		return submitted.trim();
	}

	/**
	 * Collapses the input to exactly one key entry.
	 * @param text trimmed submitted text
	 * @return the single non-comment line
	 */
	private String requireSingleEntry(String text) {
		List<String> lines = text.lines()
			.map(String::trim)
			.filter(StringUtils::hasText)
			.filter((line) -> !line.startsWith("#"))
			.toList();
		if (lines.isEmpty()) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.EMPTY);
		}
		if (lines.size() > 1) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.MULTIPLE_KEYS);
		}
		return lines.getFirst();
	}

	private AuthorizedKeyEntry parseEntry(String line) {
		try {
			return AuthorizedKeyEntry.parseAuthorizedKeyEntry(line);
		}
		catch (RuntimeException ex) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.MALFORMED);
		}
	}

	private PublicKey resolve(AuthorizedKeyEntry entry) {
		try {
			PublicKey resolved = entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
			if (resolved == null) {
				throw new SshKeyRejectedException(SshKeyRejectionReason.UNSUPPORTED_KEY_TYPE);
			}
			return resolved;
		}
		catch (SshKeyRejectedException ex) {
			throw ex;
		}
		catch (Exception ex) {
			// A key type MINA SSHD knows by name but cannot decode, or corrupt base64.
			throw new SshKeyRejectedException(SshKeyRejectionReason.MALFORMED);
		}
	}

	private String resolveKeyType(AuthorizedKeyEntry entry, PublicKey publicKey) {
		String declared = entry.getKeyType();
		if (StringUtils.hasText(declared)) {
			return declared;
		}
		String derived = KeyUtils.getKeyType(publicKey);
		if (!StringUtils.hasText(derived)) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.UNSUPPORTED_KEY_TYPE);
		}
		return derived;
	}

	private void requireAllowedAlgorithm(String keyType) {
		if (ALWAYS_REJECTED.contains(keyType)) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.WEAK_ALGORITHM);
		}
		if (!this.gitProperties.allowedKeyTypes().contains(keyType)) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.UNSUPPORTED_KEY_TYPE);
		}
	}

	/**
	 * Enforces the minimum modulus size for algorithms where size is a free parameter.
	 * @param publicKey the parsed key
	 * @param keyType the SSH algorithm name
	 * @return the key size in bits, or {@code null} when the algorithm has a fixed size
	 */
	private Integer requireSufficientStrength(PublicKey publicKey, String keyType) {
		int bits = KeyUtils.getKeySize(publicKey);
		if (bits <= 0) {
			return null;
		}
		boolean rsa = keyType.startsWith("rsa-sha2-") || "ssh-rsa".equals(keyType);
		if (rsa && bits < GitProperties.MINIMUM_RSA_KEY_BITS) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.KEY_TOO_SHORT);
		}
		return bits;
	}

	/**
	 * Rebuilds the canonical {@code "<type> <base64>"} form.
	 *
	 * <p>
	 * Storing a normalised representation means the same key pasted with different
	 * comments or surrounding whitespace is stored identically, so exact-match lookups
	 * and duplicate detection behave the way an operator expects.
	 * @param keyType the SSH algorithm name
	 * @param entry the parsed entry
	 * @return canonical single-line form without the comment
	 */
	private String normalise(String keyType, AuthorizedKeyEntry entry) {
		return keyType + " " + java.util.Base64.getEncoder().encodeToString(entry.getKeyData());
	}

}
