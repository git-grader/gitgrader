/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-process self-test for the Java grading shim, runnable with a bare JDK
 * through the single-file source launcher:
 *
 * <pre>{@code java deployment/runtimes/java-shim/ShimSelfTest.java}</pre>
 *
 * <p>
 * It stands a shim server in front of a hand-written solution and drives it
 * with the shim client over a real Unix socket, then exits nonzero if anything
 * misbehaves. No JUnit and no Docker are needed; the container build and the
 * repository's shim-test gate both run it.
 */
public final class ShimSelfTest {

	private static final List<String> FAILURES = new ArrayList<>();
	private static int count;

	private ShimSelfTest() {
	}

	private static void check(String name, boolean ok, String detail) {
		count++;
		if (ok) {
			System.out.println("ok " + count + " - " + name);
		}
		else {
			FAILURES.add(name);
			System.out.println("not ok " + count + " - " + name + (detail == null ? "" : (" :: " + detail)));
		}
	}

	public static void main(String[] args) throws Exception {
		Path temp = Files.createTempDirectory("gitgrader-shim-selftest-");
		Path solution = writeSolution(temp);
		Path socket = temp.resolve("runner.sock");

		Server server;
		try {
			server = Server.start(socket, solution, 1_500);
		}
		catch (IOException problem) {
			check("server starts in front of a well-formed solution", false, problem.getMessage());
			finish();
			return;
		}
		ShimClient client = ShimClient.connect(socket.toString(), 2_000);

		check("server starts in front of a well-formed solution", true, null);
		try {
			Object sum = client.call("add", 2, 3);
			check("calls an exported static method", Integer.valueOf(5).equals(sum), "sum was " + sum);
		}
		catch (RuntimeException problem) {
			check("calls an exported static method", false, problem.getMessage());
		}
		try {
			Object joined = client.call("join", (Object) new String[] { "a", "b" });
			check("converts wire arrays to String[]", "ab".equals(joined), "joined was " + joined);
		}
		catch (RuntimeException problem) {
			check("converts wire arrays to String[]", false, problem.getMessage());
		}
		try {
			Object bytes = client.call("echo", "hi".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			check("carries byte[] through the bytes marker",
					java.util.Arrays.equals("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8), (byte[]) bytes),
					"bytes were " + (bytes == null ? "null" : new String((byte[]) bytes)));
		}
		catch (RuntimeException problem) {
			check("carries byte[] through the bytes marker", false, problem.getMessage());
		}
		try {
			client.call("doesNotExist", 1);
			check("reports a missing export", false, "no error was raised");
		}
		catch (ShimClient.ShimException problem) {
			check("reports a missing export", Phases.NO_SUCH_MEMBER.equals(problem.phase()), problem.phase());
		}
		try {
			client.call("boom");
			check("reports a thrown exception without the stack", false, "no error was raised");
		}
		catch (ShimClient.ShimException problem) {
			check("reports a thrown exception without the stack",
					Phases.INVOCATION_ERROR.equals(problem.phase()) && !problem.getMessage().contains("ShimSelfTest"),
					"phase=" + problem.phase());
		}
		try {
			client.call("unrepresentable");
			check("refuses an unrepresentable result", false, "no error was raised");
		}
		catch (ShimClient.ShimException problem) {
			check("refuses an unrepresentable result", Phases.NON_SERIALIZABLE_RESULT.equals(problem.phase()),
					problem.phase());
		}
		try {
			client.call("slow");
			check("reports a hung call as a timeout", false, "no error was raised");
		}
		catch (ShimClient.ShimException problem) {
			check("reports a hung call as a timeout", Phases.INVOCATION_TIMEOUT.equals(problem.phase()), problem.phase());
		}

		client.close();
		server.close();
		finish();
	}

	private static Path writeSolution(Path temp) throws IOException {
		Path solution = temp.resolve("Solution.java");
		String source = """
				public class Solution {
					public static int add(int a, int b) {
						return a + b;
					}
					public static String join(String[] parts) {
						StringBuilder out = new StringBuilder();
						for (String part : parts) {
							out.append(part);
						}
						return out.toString();
					}
					public static byte[] echo(byte[] value) {
						return value;
					}
					public static String[] words() {
						return new String[] { "x", "y" };
					}
					public static int boom() {
						throw new IllegalStateException("saw a ghost");
					}
					public static double unrepresentable() {
						return Double.NaN;
					}
					public static int slow() throws InterruptedException {
						Thread.sleep(60_000);
						return 1;
					}
				}
				""";
		Files.writeString(solution, source);
		return solution;
	}

	private static void finish() {
		System.out.println("1.." + count);
		if (!FAILURES.isEmpty()) {
			System.out.println("# failing: " + FAILURES);
			System.exit(1);
		}
		System.exit(0);
	}
}
