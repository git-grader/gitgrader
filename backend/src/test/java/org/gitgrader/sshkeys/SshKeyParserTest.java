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

import java.time.Duration;
import java.util.Set;

import org.gitgrader.configuration.GitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link SshKeyParser}.
 *
 * <p>
 * Every key in this test was produced by a real {@code ssh-keygen} run, and the expected
 * fingerprints are the ones {@code ssh-keygen -lf} printed for those exact keys. That
 * matters: a parser that agrees with itself but disagrees with OpenSSH would authenticate
 * nobody, and a hand-written fixture would not have caught that.
 */
class SshKeyParserTest {

	private final SshKeyParser parser = new SshKeyParser(properties());

	private static GitProperties properties() {
		return new GitProperties(true, "localhost", 2222, "0.0.0.0", 2222, "git", "/tmp/hostkey.ser",
				"/tmp/repositories", DataSize.ofMegabytes(50), DataSize.ofMegabytes(10), 2000,
				Set.of("ssh-ed25519", "ecdsa-sha2-nistp256", "ssh-rsa"), true, Duration.ofMinutes(10));
	}

	@Nested
	@DisplayName("private key material")
	class PrivateKeyDetection {

		@Test
		@DisplayName("rejects an OpenSSH private key before anything else happens")
		void rejectsOpenSshPrivateKey() {
			assertThatExceptionOfType(SshKeyRejectedException.class)
				.isThrownBy(() -> SshKeyParserTest.this.parser.parse(Fixtures.PRIVATE_KEY))
				.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED));
		}

		@Test
		@DisplayName("rejects a private key even when surrounded by other text")
		void rejectsEmbeddedPrivateKey() {
			String sneaky = "here is my key\n" + Fixtures.PRIVATE_KEY + "\nthanks";
			assertThatExceptionOfType(SshKeyRejectedException.class)
				.isThrownBy(() -> SshKeyParserTest.this.parser.parse(sneaky))
				.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED));
		}

		@Test
		@DisplayName("rejects a PuTTY private key file")
		void rejectsPuttyKey() {
			assertThatExceptionOfType(SshKeyRejectedException.class).isThrownBy(
					() -> SshKeyParserTest.this.parser.parse("PuTTY-User-Key-File-3: ssh-ed25519\nEncryption: none"))
				.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED));
		}

		@Test
		@DisplayName("never echoes the submitted material in the exception")
		void neverEchoesSecretMaterial() {
			// The public registration form is the single most likely place for a private
			// key
			// to be pasted. If the exception carried the input, it would reach the log
			// the
			// moment anything upstream logged the failure.
			SshKeyRejectedException thrown = catchRejection(Fixtures.PRIVATE_KEY);
			assertThat(thrown.getMessage()).doesNotContain("PRIVATE KEY");
			assertThat(thrown.publicMessage()).doesNotContain("BEGIN");
			assertThat(thrown.publicMessage()).contains("Never upload or share a private key");
		}

	}

	@Nested
	@DisplayName("accepted keys")
	class AcceptedKeys {

		@Test
		@DisplayName("parses Ed25519 and reproduces the fingerprint ssh-keygen reports")
		void parsesEd25519() {
			SshPublicKey key = SshKeyParserTest.this.parser.parse(Fixtures.ED25519);

			assertThat(key.keyType()).isEqualTo("ssh-ed25519");
			assertThat(key.fingerprint()).isEqualTo(Fixtures.ED25519_FINGERPRINT);
			assertThat(key.comment()).isEqualTo("student@example.org");
			assertThat(key.isPreferredType()).isTrue();
		}

		@Test
		@DisplayName("parses a 4096-bit RSA key, because authorized_keys names it ssh-rsa")
		void parsesStrongRsa() {
			// Regression guard. An earlier revision banned the blob type "ssh-rsa"
			// believing
			// it meant SHA-1 signatures. It does not: it is how EVERY RSA public key is
			// written, so that rule rejected all RSA keys including fresh 4096-bit ones.
			SshPublicKey key = SshKeyParserTest.this.parser.parse(Fixtures.RSA_4096);

			assertThat(key.keyType()).isEqualTo("ssh-rsa");
			assertThat(key.keyBits()).isEqualTo(4096);
			assertThat(key.fingerprint()).isEqualTo(Fixtures.RSA_4096_FINGERPRINT);
			assertThat(key.isPreferredType()).isFalse();
		}

		@Test
		@DisplayName("parses an ECDSA P-256 key")
		void parsesEcdsa() {
			SshPublicKey key = SshKeyParserTest.this.parser.parse(Fixtures.ECDSA_P256);

			assertThat(key.keyType()).isEqualTo("ecdsa-sha2-nistp256");
			assertThat(key.fingerprint()).isEqualTo(Fixtures.ECDSA_FINGERPRINT);
		}

		@Test
		@DisplayName("tolerates surrounding whitespace and a trailing newline")
		void tolerantOfWhitespace() {
			SshPublicKey key = SshKeyParserTest.this.parser.parse("   \n  " + Fixtures.ED25519 + "  \n\n");

			assertThat(key.fingerprint()).isEqualTo(Fixtures.ED25519_FINGERPRINT);
		}

		@Test
		@DisplayName("normalises away the comment so the same key stores identically")
		void normalisesComment() {
			String withoutComment = Fixtures.ED25519.substring(0, Fixtures.ED25519.lastIndexOf(' '));

			SshPublicKey a = SshKeyParserTest.this.parser.parse(Fixtures.ED25519);
			SshPublicKey b = SshKeyParserTest.this.parser.parse(withoutComment + " different@example.org");

			assertThat(a.encoded()).isEqualTo(b.encoded());
			assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
		}

		@Test
		@DisplayName("shortens a fingerprint for display without changing the real one")
		void shortFingerprintIsDisplayOnly() {
			SshPublicKey key = SshKeyParserTest.this.parser.parse(Fixtures.ED25519);

			assertThat(key.shortFingerprint()).startsWith("SHA256:").endsWith("...");
			assertThat(key.shortFingerprint()).isNotEqualTo(key.fingerprint());
		}

	}

	@Nested
	@DisplayName("rejected keys")
	class RejectedKeys {

		@Test
		@DisplayName("rejects an RSA key below the configured modulus floor")
		void rejectsShortRsa() {
			assertThat(catchRejection(Fixtures.RSA_2048).reason()).isEqualTo(SshKeyRejectionReason.KEY_TOO_SHORT);
		}

		@Test
		@DisplayName("rejects two keys pasted at once")
		void rejectsMultipleKeys() {
			assertThat(catchRejection(Fixtures.ED25519 + "\n" + Fixtures.ECDSA_P256).reason())
				.isEqualTo(SshKeyRejectionReason.MULTIPLE_KEYS);
		}

		@Test
		@DisplayName("rejects text that is not a key at all")
		void rejectsGarbage() {
			assertThat(catchRejection("hello world").reason()).isEqualTo(SshKeyRejectionReason.MALFORMED);
		}

		@Test
		@DisplayName("rejects a truncated key body")
		void rejectsTruncatedKey() {
			assertThat(catchRejection("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI").reason())
				.isIn(SshKeyRejectionReason.MALFORMED, SshKeyRejectionReason.UNSUPPORTED_KEY_TYPE);
		}

		@Test
		@DisplayName("rejects blank input")
		void rejectsBlank() {
			assertThat(catchRejection("   \n  ").reason()).isEqualTo(SshKeyRejectionReason.EMPTY);
		}

		@Test
		@DisplayName("rejects a key type that is not in the allow-list")
		void rejectsDisallowedType() {
			SshKeyParser restricted = new SshKeyParser(new GitProperties(true, "localhost", 2222, "0.0.0.0", 2222,
					"git", "/tmp/hostkey.ser", "/tmp/repositories", DataSize.ofMegabytes(50), DataSize.ofMegabytes(10),
					2000, Set.of("ssh-ed25519"), true, Duration.ofMinutes(10)));

			assertThatExceptionOfType(SshKeyRejectedException.class)
				.isThrownBy(() -> restricted.parse(Fixtures.ECDSA_P256))
				.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.UNSUPPORTED_KEY_TYPE));
			assertThatCode(() -> restricted.parse(Fixtures.ED25519)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("rejects an oversized payload without attempting to parse it")
		void rejectsOversizedInput() {
			assertThat(catchRejection("ssh-ed25519 " + "A".repeat(20_000)).reason())
				.isEqualTo(SshKeyRejectionReason.MALFORMED);
		}

	}

	@Nested
	@DisplayName("rejection messages")
	class RejectionMessages {

		@Test
		@DisplayName("never disclose which account already holds a duplicate key")
		void duplicateMessageIsNotAnOracle() {
			// On a public form, naming the owner would turn the endpoint into a lookup
			// for
			// "is this key registered, and to whom".
			String message = SshKeyRejectionReason.DUPLICATE_FINGERPRINT.publicMessage();

			assertThat(message).doesNotContainIgnoringCase("already registered to");
			assertThat(message).doesNotContainIgnoringCase("student");
		}

		@Test
		@DisplayName("flag the security relevant reasons")
		void marksSecurityRelevantReasons() {
			assertThat(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED.isSecurityRelevant()).isTrue();
			assertThat(SshKeyRejectionReason.DUPLICATE_FINGERPRINT.isSecurityRelevant()).isTrue();
			assertThat(SshKeyRejectionReason.MALFORMED.isSecurityRelevant()).isFalse();
		}

		@Test
		@DisplayName("are present and actionable for every reason")
		void everyReasonHasAMessage() {
			for (SshKeyRejectionReason reason : SshKeyRejectionReason.values()) {
				assertThat(reason.publicMessage()).isNotBlank().hasSizeGreaterThan(20);
			}
		}

	}

	private SshKeyRejectedException catchRejection(String input) {
		try {
			this.parser.parse(input);
		}
		catch (SshKeyRejectedException ex) {
			return ex;
		}
		throw new AssertionError("Expected the parser to reject this input, but it was accepted");
	}

	/**
	 * Key material generated with ssh-keygen; fingerprints verified with ssh-keygen -lf.
	 */
	static final class Fixtures {

		static final String ED25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPkLKDHNOKp7Nnxq7eGkNcYPKi3n2uFF9aKYc41rUW0c student@example.org";

		static final String ED25519_FINGERPRINT = "SHA256:P1N7AkIDG5MM+0K2XzEELxU1Zwa44rUmD7TwSnkCqdA";

		static final String RSA_4096 = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDHfGWO1Mj9CsCs2CZUpSp00D1cdhel30oi3ug87zF2ZfUpzgFsGPYxT9ePSGc6zjYoREx3YkWO7yeo8Z4h375yPTBacidSwrFY20mUbyhMVsQW50M7kerHEAqqmgbk0y14cn2aN1ZF9YR+J2GoV9KhfvIjsob4NokiQvyE8l8FIyFaREewQYwPhdG4+K0HxT1Cyy+L6MWu+fQkZwR2jTjlkz/8NltIjaMbLrWd4eq4WqAiTSPXNW2WQyJ8DwwKXMhC4wr+9MP1oAtiB7QG0uZLCWBbetr+RoCx3AAY5yeHsSLMYrkmlupvgVJYpHkXUEhgHjksT2gUWjDgeZ0BvirORnt12RQ6LbThgJGQ4OGEEyXwyKIfScCYVYKhNoqryNXZHEXolh8dUw3OXjUfXMyW2Ey2OiZvSZY9E2Qr+c1M7ShTUoQvUjGhv8mK+BArElBdRk7eSZWntu1bmPApbjC77GpKltrwtVa6i3sTk30WbFxpJrKPn08QYpiIzGfDr+nMXoQEuoQ3uNrhp/k99W7mHCfC7ENl6Y2K3Z7EbDTepL053Mlq+/WDdNNzfdbddGYc/lQUOOG8FNeeffB6kO745WdfqqN3srgYwimt09Sr7QwsAfY+P4zpjLA4nN7ax6rUtXimxS0WWVDlzar0k+3RJB31SUzavTOanzDTWI39Kw== rsa4096@example.org";

		static final String RSA_4096_FINGERPRINT = "SHA256:Hzmq5df4J9DWaLOB4kvkL0yrgufjG9IF9fvbyt4FkCQ";

		static final String RSA_2048 = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCzRkrMbEzy7qn8SpjGS5d87+TkogKcqjH0h3Pf5OKvYvGMGqCEZz2A0lNVxDa40iad0tjykKDQ9upVNPdGo6CoNCvRxkygUYUq4Z87qkfWekh4wHYttV03nBIFioIxYUhHvVG1G1Gn564Lt0SX6e0PdpcB0REkMf30FTIJRkvEC5ME2gh+0sFTrAly7nxSAc/AK3iqG15HtSr4Q7Z/Az27zEVdsx0ZKLU7SYOW8SMzmjPvp/Q5BJxFgdXBr/BFArAlA9NBZfEv9y318Q54mjWewOr424QhG2dSTyFNtUHLzFK9+oww9/aKl9QGvZBU4GSMD2XDZW4KoV5c0Y1FLD9j rsa2048@example.org";

		static final String ECDSA_P256 = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBJXMAFhYR8qzHW2FjLzjxNHpCFqslh1i8X7lnjcMeuPmpPzuI7MEON303aZ/YtYvrN/ZqHao3i95Hgrx07N0QQs= ecdsa@example.org";

		static final String ECDSA_FINGERPRINT = "SHA256:ORKBq34n05pJNqdvWHNi/XyyuWcMxovqWfLTAuwgmaE";

		static final String PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\nQyNTUxOQAAACD5CygxzTiqezZ8au3hpDXGDyot59rhRfWimHONa1FtHAAAAJhWiEV4VohF\n-----END OPENSSH PRIVATE KEY-----";

		private Fixtures() {
		}

	}

}
