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

package org.gitgrader.git.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.SignatureVerifierFactory;
import org.eclipse.jgit.lib.SignatureVerifiers;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.signing.ssh.SshSignatureVerifierFactory;
import org.eclipse.jgit.signing.ssh.SigningKeyDatabase;
import org.eclipse.jgit.util.RawParseUtils;
import org.gitgrader.git.CommitSignatureResult;
import org.gitgrader.git.CommitSignatureResult.CommitSignatureStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies that a pushed commit was signed by a key belonging to the expected student.
 *
 * <p>
 * The check runs in two clearly separated stages, and both must pass:
 *
 * <ol>
 * <li>JGit verifies the SSHSIG blob against the commit payload. This project does not
 * implement signature verification itself.</li>
 * <li>GitGrader resolves the recovered key fingerprint against its own key registry and
 * confirms that the key belonged to the expected student and was usable at the time.</li>
 * </ol>
 *
 * <p>
 * Splitting them matters. A signature that verifies cryptographically says nothing about
 * <em>whose</em> key made it, and a naive implementation that stopped after stage one
 * would happily accept a commit signed by any key in the world.
 */
@Component
public class CommitSignatureVerifier {

	private static final Logger logger = LoggerFactory.getLogger(CommitSignatureVerifier.class);

	/** The {@code gpgsig} commit header, as raw bytes. */
	private static final byte[] GPGSIG_HEADER = "gpgsig".getBytes(StandardCharsets.UTF_8);

	/**
	 * Signature algorithms refused even when the signature itself verifies.
	 *
	 * <p>
	 * {@code ssh-rsa} here is the SHA-1 RSA <em>signature</em> algorithm, which is a
	 * different thing from the {@code ssh-rsa} key blob type that {@code SshKeyParser}
	 * accepts. A student with an RSA key can always produce a {@code rsa-sha2-256} or
	 * {@code rsa-sha2-512} signature with a current OpenSSH, so refusing SHA-1 costs them
	 * nothing and closes a known-weak path.
	 */
	private static final Set<String> REFUSED_SIGNATURE_ALGORITHMS = Set.of("ssh-rsa", "ssh-dss");

	private final SigningKeyOwnership ownership;

	public CommitSignatureVerifier(SigningKeyOwnership ownership) {
		this.ownership = ownership;
	}

	/**
	 * Installs the cryptography-only signing key database into JGit.
	 *
	 * <p>
	 * {@link SigningKeyDatabase#setInstance} is a process-wide setting. That is
	 * acceptable here because GitGrader is the only user of JGit signature verification
	 * inside this JVM, and because the authorization decision this class makes afterwards
	 * does not depend on the installed database at all. See
	 * {@link CryptographyOnlySigningKeyDatabase} for the full reasoning.
	 */
	@PostConstruct
	void installSigningKeyDatabase() {
		SigningKeyDatabase.setInstance(new CryptographyOnlySigningKeyDatabase());
		logger.info("Installed GitGrader signing key database; commit signature authorization "
				+ "is resolved against the ssh_keys registry, not against allowed_signers");
		registerSshSignatureVerifier();
	}

	/**
	 * Registers the SSH signature verifier explicitly rather than leaving it to
	 * discovery.
	 *
	 * <p>
	 * JGit finds verifiers with the single argument form of {@code ServiceLoader.load},
	 * which searches the thread's context class loader. That loader is the application's
	 * own during startup but not necessarily on the SSH worker threads that handle a
	 * push, and inside a packaged application the service files live in nested jars the
	 * wrong loader cannot see. The lookup then simply finds nothing, and because a
	 * missing verifier is reported as an unprocessable signature rather than as an error,
	 * the result is that every signed push is refused as invalid with no failure logged
	 * anywhere. Binding the verifier here removes the dependency on which thread asks.
	 */
	private static void registerSshSignatureVerifier() {
		SignatureVerifierFactory factory = new SshSignatureVerifierFactory();
		SignatureVerifiers.set(factory.getType(), factory.create());
		logger.info("Registered the {} commit signature verifier explicitly", factory.getType());
	}

	/**
	 * Verifies one commit against one expected student.
	 * @param repository the repository the commit was pushed to
	 * @param commit the commit to check
	 * @param expectedStudentId the student whose SSH key opened the connection
	 * @return the verdict, never {@code null}
	 */
	public CommitSignatureResult verify(Repository repository, RevCommit commit, UUID expectedStudentId) {
		return verify(repository, commit, expectedStudentId, new HashMap<>());
	}

	/**
	 * Verifies one commit, reusing ownership decisions already made in this push.
	 *
	 * <p>
	 * The cryptography runs for every commit: that is the whole point of checking each
	 * one rather than only the tip. The registry lookup behind it does not need to. A
	 * push of a hundred commits signed with one key asked the same question a hundred
	 * times, on the request path, holding a connection each time.
	 *
	 * <p>
	 * The cache must not outlive the push. A key revoked between two pushes has to be
	 * refused by the second one, so the caller owns the map and creates a new one per
	 * push; the student is fixed for its lifetime, which is why the fingerprint alone
	 * identifies an answer.
	 * @param repository the repository the commit was pushed to
	 * @param commit the commit to check
	 * @param expectedStudentId the student whose SSH key opened the connection
	 * @param ownershipCache decisions already made for this push, keyed by fingerprint
	 * @return the verdict, never {@code null}
	 */
	public CommitSignatureResult verify(Repository repository, RevCommit commit, UUID expectedStudentId,
			Map<String, CommitSignatureResult> ownershipCache) {
		byte[] signature = commit.getRawGpgSignature();
		if (signature == null || signature.length == 0) {
			return CommitSignatureResult.rejected(CommitSignatureStatus.UNSIGNED, null,
					"Commit carries no gpgsig header");
		}
		CommitSignatureResult cryptographic = verifyCryptographically(repository, commit, signature);
		if (!cryptographic.isAcceptable()) {
			return cryptographic;
		}
		// Stage one only proved the signature is sound. Stage two decides whose it is.
		String fingerprint = requireFingerprint(cryptographic);
		return ownershipCache.computeIfAbsent(fingerprint, (key) -> this.ownership.authorize(key, expectedStudentId));
	}

	/**
	 * Runs the cryptographic half of the check.
	 *
	 * <p>
	 * A {@code VERIFIED} result from this method means only that the signature matches
	 * the commit payload and uses an acceptable algorithm. It says nothing about who owns
	 * the key, which is why the caller must always follow it with an ownership check.
	 * @param repository the repository the commit was pushed to
	 * @param commit the commit to check
	 * @param signature the raw gpgsig header value
	 * @return {@code VERIFIED} with a fingerprint, or the reason it failed
	 */
	private CommitSignatureResult verifyCryptographically(Repository repository, RevCommit commit, byte[] signature) {
		byte[] payload = payloadWithoutSignature(commit);
		if (payload == null) {
			return CommitSignatureResult.rejected(CommitSignatureStatus.INVALID, null,
					"Commit has a signature but its payload could not be reconstructed");
		}

		SignatureVerification verification = runJGitVerification(repository, payload, signature);
		if (verification == null) {
			return CommitSignatureResult.rejected(CommitSignatureStatus.INVALID, null,
					"Signature could not be processed");
		}
		if (!verification.verified()) {
			String message = blank(verification.message()) ? "Signature does not match the commit"
					: verification.message();
			return CommitSignatureResult.rejected(CommitSignatureStatus.INVALID, verification.keyFingerprint(),
					message);
		}

		String fingerprint = verification.keyFingerprint();
		if (blank(fingerprint)) {
			return CommitSignatureResult.rejected(CommitSignatureStatus.INVALID, null,
					"Signature verified but no key fingerprint could be recovered");
		}

		String refused = refusedAlgorithm(signature);
		if (refused != null) {
			return CommitSignatureResult.rejected(CommitSignatureStatus.INVALID, fingerprint,
					"Signature uses the deprecated algorithm " + refused
							+ "; re-sign with an Ed25519 key or an rsa-sha2-* signature");
		}
		return CommitSignatureResult.verified(fingerprint);
	}

	private static String requireFingerprint(CommitSignatureResult result) {
		String fingerprint = result.keyFingerprint();
		if (fingerprint == null) {
			throw new IllegalStateException("A verified signature must always carry a fingerprint");
		}
		return fingerprint;
	}

	/**
	 * Rebuilds the exact bytes that were signed.
	 *
	 * <p>
	 * Git signs the commit object with the {@code gpgsig} header removed. The header is
	 * folded across continuation lines, so the end of it is found by skipping split lines
	 * rather than by looking for the next newline.
	 * @param commit the commit
	 * @return the payload, or {@code null} when no signature header is present
	 */
	private @Nullable byte[] payloadWithoutSignature(RevCommit commit) {
		byte[] raw = commit.getRawBuffer();
		int contentStart = RawParseUtils.headerStart(GPGSIG_HEADER, raw, 0);
		if (contentStart < 0) {
			return null;
		}
		int end = RawParseUtils.nextLfSkippingSplitLines(raw, contentStart);
		// headerStart points at the value; step back over "gpgsig" and its space.
		int start = contentStart - (GPGSIG_HEADER.length + 1);
		if (start < 0) {
			return null;
		}
		if (end < raw.length) {
			end++;
		}
		byte[] payload = new byte[raw.length - (end - start)];
		System.arraycopy(raw, 0, payload, 0, start);
		System.arraycopy(raw, end, payload, start, raw.length - end);
		return payload;
	}

	private @Nullable SignatureVerification runJGitVerification(Repository repository, byte[] payload,
			byte[] signature) {
		try {
			GpgConfig config = new GpgConfig(repository.getConfig());
			return SignatureVerifiers.verify(repository, config, payload, signature);
		}
		catch (IOException | RuntimeException ex) {
			// Malformed signature blobs are attacker-controlled input on this path, so a
			// failure here is an expected outcome and must not surface as a 500.
			logger.debug("Signature verification failed to process a commit signature", ex);
			return null;
		}
	}

	/**
	 * Detects a deprecated signature algorithm inside the SSHSIG blob.
	 *
	 * <p>
	 * The armored blob embeds the public key, whose wire encoding begins with the
	 * algorithm name in plain ASCII. Scanning the decoded text for the refused names is
	 * enough to spot a SHA-1 RSA signature without re-parsing the whole structure, and it
	 * cannot produce a false accept: the check only ever adds a rejection on top of a
	 * signature JGit already accepted.
	 * @param signature the armored SSHSIG blob
	 * @return the refused algorithm name, or {@code null} when none was found
	 */
	private @Nullable String refusedAlgorithm(byte[] signature) {
		String armored = new String(signature, StandardCharsets.US_ASCII);
		int begin = armored.indexOf("-----BEGIN SSH SIGNATURE-----");
		if (begin < 0) {
			return null;
		}
		int end = armored.indexOf("-----END SSH SIGNATURE-----");
		if (end <= begin) {
			return null;
		}
		String body = armored.substring(begin + "-----BEGIN SSH SIGNATURE-----".length(), end).replaceAll("\\s", "");
		byte[] decoded;
		try {
			decoded = java.util.Base64.getDecoder().decode(body);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		String text = new String(decoded, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
		for (String refused : REFUSED_SIGNATURE_ALGORITHMS) {
			// The algorithm name appears twice: once in the embedded public key and once
			// in the signature structure. Requiring two occurrences avoids matching a key
			// type name that merely happens to be a prefix of the signature algorithm.
			if (text.indexOf(refused) >= 0 && text.indexOf(refused) != text.lastIndexOf(refused)) {
				return refused;
			}
		}
		return null;
	}

	private static boolean blank(@Nullable String value) {
		return value == null || value.isBlank();
	}

}
