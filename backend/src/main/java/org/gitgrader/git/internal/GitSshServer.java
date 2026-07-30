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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jakarta.annotation.PreDestroy;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.apache.sshd.git.pack.GitPackConfiguration;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.transport.ReceivePack;
import org.gitgrader.configuration.GitProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.git.internal.StudentKeyAuthenticator.AuthenticatedStudent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The embedded SSH endpoint students clone from and push to.
 *
 * <p>
 * Running the SSH server inside the application, rather than delegating to the host's
 * {@code sshd} with an {@code AuthorizedKeysCommand}, is what lets the whole platform
 * ship as a single container with no OS user accounts and no host configuration. It also
 * means the authenticated identity is already in memory when the push is admitted,
 * instead of being passed back through an environment variable.
 *
 * <p>
 * Authorization happens in {@link #resolveRepositoryRoot}, before any Git machinery
 * starts: the requested path must resolve to a registered repository that belongs to the
 * connecting student. A request that does not is refused with a message that reveals
 * nothing about whether the repository exists.
 */
@Component
@ConditionalOnProperty(name = "git.enabled", havingValue = "true", matchIfMissing = true)
public class GitSshServer {

	/** Session attribute holding the repository this connection resolved to. */
	static final org.apache.sshd.common.AttributeRepository.AttributeKey<RepositoryRecord> RESOLVED_REPOSITORY = new org.apache.sshd.common.AttributeRepository.AttributeKey<>();

	private static final Logger logger = LoggerFactory.getLogger(GitSshServer.class);

	/** Substring identifying a push, as opposed to a clone or fetch. */
	private static final String RECEIVE_PACK_COMMAND = "receive-pack";

	private final GitProperties gitProperties;

	private final StorageProperties storage;

	private final StudentKeyAuthenticator authenticator;

	private final GitRepositoryService repositoryService;

	private final PushAdmissionHook admissionHook;

	private SshServer server;

	public GitSshServer(GitProperties gitProperties, StorageProperties storage, StudentKeyAuthenticator authenticator,
			GitRepositoryService repositoryService, PushAdmissionHook admissionHook) {
		this.gitProperties = gitProperties;
		this.storage = storage;
		this.authenticator = authenticator;
		this.repositoryService = repositoryService;
		this.admissionHook = admissionHook;
	}

	/**
	 * Starts the SSH endpoint once the application context is ready.
	 *
	 * <p>
	 * Bound to {@code ApplicationReadyEvent} rather than to bean initialisation so that a
	 * client can never reach a half-initialised context: by this point Flyway has run and
	 * every collaborator is available.
	 * @throws IOException if the port cannot be bound or the host key cannot be read
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void start() throws IOException {
		Files.createDirectories(this.storage.repositories());
		Path hostKey = Path.of(this.gitProperties.hostKeyPath()).toAbsolutePath();
		Path hostKeyDirectory = hostKey.getParent();
		if (hostKeyDirectory == null) {
			throw new IOException("git.host-key-path has no parent directory: " + hostKey);
		}
		Files.createDirectories(hostKeyDirectory);

		this.server = SshServer.setUpDefaultServer();
		this.server.setHost(this.gitProperties.listenAddress());
		this.server.setPort(this.gitProperties.listenPort());
		// Generated on first start and then reused, so returning students do not get a
		// host key warning after every restart.
		this.server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey));
		this.server.setPublickeyAuthenticator(this.authenticator);
		// Password authentication is never configured: a student has no password here,
		// and leaving the mechanism enabled would only invite brute force attempts.
		this.server.setCommandFactory(new GitPackCommandFactory().withGitLocationResolver(this::resolveRepositoryRoot)
			.withGitPackConfiguration(new GitPackConfiguration() {
				@Override
				public void configureReceivePack(ServerSession session, ReceivePack pack) {
					GitSshServer.this.admissionHook.install(session, pack);
				}
			}));

		this.server.start();
		logger.info("Git SSH endpoint listening on {}:{}, advertising {}@{}:{}", this.gitProperties.listenAddress(),
				this.gitProperties.listenPort(), this.gitProperties.sshUser(), this.gitProperties.sshHost(),
				this.gitProperties.sshPort());
	}

	/**
	 * Stops the SSH endpoint.
	 * @throws IOException if the server cannot be shut down cleanly
	 */
	@PreDestroy
	public void stop() throws IOException {
		if (this.server != null && this.server.isStarted()) {
			this.server.stop();
			logger.info("Git SSH endpoint stopped");
		}
	}

	/**
	 * Authorizes the request and returns the root the Git command resolves against.
	 *
	 * <p>
	 * The requested path is never turned into a filesystem path here. It is looked up in
	 * the repository table by exact match, and only a hit that belongs to the connecting
	 * student is allowed to proceed. A miss and a mismatch produce the same message, so
	 * the endpoint cannot be used to discover which repositories exist.
	 * @param command the git command being run
	 * @param args the command arguments, whose last element is the repository path
	 * @param session the authenticated SSH session
	 * @param fileSystem the session's file system view
	 * @return the directory the git command resolves the repository against
	 * @throws IOException when the request is not authorized
	 */
	@SuppressWarnings("PMD.UnusedFormalParameter") // signature fixed by
													// GitLocationResolver
	private Path resolveRepositoryRoot(String command, String[] args, ServerSession session,
			java.nio.file.FileSystem fileSystem) throws IOException {
		AuthenticatedStudent student = session.getAttribute(StudentKeyAuthenticator.AUTHENTICATED_STUDENT);
		if (student == null) {
			throw new IOException("Not authenticated.");
		}
		if (args == null || args.length == 0) {
			throw new IOException("No repository was requested.");
		}

		RepositoryRecord record = authorize(command, args[args.length - 1], student);
		session.setAttribute(RESOLVED_REPOSITORY, record);
		return this.storage.repositories();
	}

	/**
	 * Resolves a requested path to a repository the connecting student may use.
	 *
	 * <p>
	 * A miss and an ownership mismatch raise the same message on purpose. Distinguishing
	 * them would turn the endpoint into a directory of which students and assignments
	 * exist, which is reachable by anyone holding any registered key.
	 * @param command the git command being run
	 * @param requested the repository path from the SSH command line
	 * @param student the authenticated student
	 * @return the authorized repository
	 * @throws IOException when the request is not authorized
	 */
	private RepositoryRecord authorize(String command, String requested, AuthenticatedStudent student)
			throws IOException {
		Optional<RepositoryRecord> record = this.repositoryService.resolve(requested);
		if (record.isEmpty() || !record.get().studentId().equals(student.studentId())) {
			logger.info("Refused {} for student {} on path {}", command, student.studentId(), requested);
			throw new IOException("Repository not found, or you do not have access to it.");
		}
		if (command.contains(RECEIVE_PACK_COMMAND) && !this.repositoryService.acceptsPushes(record.get())) {
			throw new IOException("This repository is not accepting pushes.");
		}
		return record.get();
	}

}
