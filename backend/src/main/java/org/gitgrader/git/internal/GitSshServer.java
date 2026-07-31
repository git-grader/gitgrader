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
import java.time.Duration;
import java.util.Optional;

import org.apache.sshd.core.CoreModuleProperties;
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
import org.springframework.context.SmartLifecycle;
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
public class GitSshServer implements SmartLifecycle {

	/** Session attribute holding the repository this connection resolved to. */
	static final org.apache.sshd.common.AttributeRepository.AttributeKey<RepositoryRecord> RESOLVED_REPOSITORY = new org.apache.sshd.common.AttributeRepository.AttributeKey<>();

	/** Stops before the grading dispatcher, so a push cannot land in a draining queue. */
	private static final int SHUTDOWN_PHASE = Integer.MAX_VALUE;

	/** How long an unauthenticated connection may stay open before it is dropped. */
	private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(30);

	/** Key attempts allowed per connection; a student offers a handful at most. */
	private static final int MAX_AUTH_REQUESTS = 6;

	/**
	 * Ceiling on established sessions, counted per user name and so instance-wide here.
	 */
	private static final int MAX_CONCURRENT_SESSIONS = 256;

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
	 * every collaborator is available. That is also why {@link #isAutoStartup()} is false
	 * - this bean implements {@link SmartLifecycle} for the ordered <em>shutdown</em>,
	 * not to be started with the rest of the lifecycle beans.
	 * @throws IOException if the port cannot be bound or the host key cannot be read
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void startOnApplicationReady() throws IOException {
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
		applyTransportLimits(this.server);
		// Password authentication is never configured: a student has no password here,
		// and leaving the mechanism enabled would only invite brute force attempts.
		this.server.setCommandFactory(new GitPackCommandFactory().withGitLocationResolver(this::resolveRepositoryRoot)
			.withGitPackConfiguration(new GitPackConfiguration() {
				@Override
				public void configureReceivePack(ServerSession session, ReceivePack pack) {
					GitSshServer.this.admissionHook.install(session, pack);
					GitSshServer.this.applyReceiveLimits(pack);
				}
			}));

		this.server.start();
		logger.info("Git SSH endpoint listening on {}:{}, advertising {}@{}:{}", this.gitProperties.listenAddress(),
				this.gitProperties.listenPort(), this.gitProperties.sshUser(), this.gitProperties.sshHost(),
				this.gitProperties.sshPort());
	}

	/**
	 * Bounds what one connection may occupy before it has proved anything.
	 *
	 * <p>
	 * Every value here was already configurable and enforced nothing. An unauthenticated
	 * client could hold a socket open indefinitely and retry keys without limit, which is
	 * free denial of service against a service whose whole ingress is this port.
	 *
	 * <p>
	 * {@code MAX_CONCURRENT_SESSIONS} is counted per user name, and every student
	 * connects as the same fixed user, so here it acts as an instance-wide ceiling on
	 * established sessions rather than a per-student one.
	 * @param sshServer the server to configure
	 */
	private void applyTransportLimits(SshServer sshServer) {
		CoreModuleProperties.IDLE_TIMEOUT.set(sshServer, this.gitProperties.idleTimeout());
		CoreModuleProperties.AUTH_TIMEOUT.set(sshServer, AUTH_TIMEOUT);
		CoreModuleProperties.MAX_AUTH_REQUESTS.set(sshServer, MAX_AUTH_REQUESTS);
		CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(sshServer, MAX_CONCURRENT_SESSIONS);
	}

	/**
	 * Bounds what one push may transfer, and refuses history rewrites.
	 *
	 * <p>
	 * JGit parses a pack before the pre-receive hook runs, so refusing an oversized push
	 * in {@code PushAdmissionHook} would happen only after its bytes had already been
	 * written. These two limits abort during receive, which is the only point where the
	 * transfer can still be stopped.
	 *
	 * <p>
	 * Non-fast-forwards are allowed by JGit unless {@code receive.denyNonFastForwards} is
	 * set, and nothing here set it. A student could therefore rewrite a branch and orphan
	 * commits that submissions still reference, leaving a re-grade unable to find them.
	 * @param pack the receive-pack about to process a push
	 */
	private void applyReceiveLimits(ReceivePack pack) {
		pack.setMaxPackSizeLimit(this.gitProperties.maxPushSize().toBytes());
		pack.setMaxObjectSizeLimit(this.gitProperties.maxFileSize().toBytes());
		pack.setAllowNonFastForwards(false);
	}

	@Override
	public void start() {
		// Startup is driven by ApplicationReadyEvent; see startOnApplicationReady.
	}

	@Override
	public boolean isAutoStartup() {
		return false;
	}

	@Override
	public boolean isRunning() {
		return this.server != null && this.server.isStarted();
	}

	@Override
	public int getPhase() {
		return SHUTDOWN_PHASE;
	}

	/**
	 * Stops the SSH endpoint before anything downstream of it winds down.
	 *
	 * <p>
	 * Runs in a higher phase than the grading dispatcher on purpose: a push admitted
	 * while the queue is draining would be recorded and then immediately handed back, and
	 * the student would be told their work was accepted by a process that is going away.
	 * Closing the port first makes "not accepting new work" true before the drain starts.
	 */
	@Override
	public void stop() {
		try {
			if (this.server != null && this.server.isStarted()) {
				this.server.stop();
				logger.info("Git SSH endpoint stopped");
			}
		}
		catch (IOException ex) {
			logger.warn("Git SSH endpoint did not shut down cleanly", ex);
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
