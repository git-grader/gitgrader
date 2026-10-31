/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs a compiled JUnit suite and emits TAP on standard output, the report
 * format the grading runner parses.
 *
 * <p>
 * The console launcher's built-in reporters do not emit TAP (a {@code --tap}
 * flag was never part of the standalone launcher), so the suite side launches
 * the JUnit Platform directly with a small {@link TestExecutionListener} that
 * writes the same TAP vocabulary the Python and Node runtimes emit. The plan
 * and pass/fail lines mirror the Jasmine TAP reporter the Node runtimes ship.
 *
 * <p>
 * Usage from a suite container with the compiled tests in {@code /tmp/classes}:
 *
 * <pre>{@code
 * java -cp /tmp/classes:/opt/junit-platform-console-standalone.jar TapRunner
 * }</pre>
 */
public final class TapRunner {

	private TapRunner() {
	}

	public static void main(String[] args) {
		TapReporter reporter = new TapReporter(System.out);
		Launcher launcher = LauncherFactory.create();
		launcher.registerTestExecutionListeners(reporter);
		launcher.execute(LauncherDiscoveryRequestBuilder.request()
				.selectors(DiscoverySelectors.selectClasspathRoots(Set.of(java.nio.file.Path.of("/tmp/classes"))))
				.build());
		reporter.finish();
		System.out.flush();
	}

	/** Emits one TAP outcome line per executed test, then the plan and counts. */
	static final class TapReporter implements TestExecutionListener {
		private final PrintStream out;
		private final List<String> diagnostics = new java.util.ArrayList<>();
		private int pass;
		private int count;

		TapReporter(PrintStream out) {
			this.out = out;
		}

		@Override
		public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
			if (!identifier.isTest()) {
				return;
			}
			count++;
			String name = identifier.getDisplayName();
			if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
				pass++;
				out.println("ok " + count + " - " + name);
			}
			else {
				out.println("not ok " + count + " - " + name);
				if (identifier.isTest() && result.getThrowable().isPresent()) {
					diagnostics.add(result.getThrowable().get().getClass().getSimpleName() + ": "
							+ String.valueOf(result.getThrowable().get().getMessage()));
				}
			}
		}

		void finish() {
			out.println("1.." + count);
			out.println("# tests " + count);
			out.println("# pass " + pass);
			out.println("# fail " + (count - pass));
			for (String diagnostic : diagnostics) {
				out.println("# " + diagnostic);
			}
		}
	}
}
