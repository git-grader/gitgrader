# Grader test-suite contract

This directory is stored outside the student project and mounted read-only at
`/opt/hidden-tests` in the suite container of a shimmed round. The submission is
graded in a separate sandbox container that never sees these hidden sources;
the suite reaches the submission over the shim socket that the two containers
share. `SolutionTest.java` opens that channel as its first action:

```java
private static ShimClient shim;

@BeforeAll
static void openShim() {
	shim = ShimClient.connect();
}
```

`ShimClient` lives in `/opt/gitgrader-shim` (baked into the Java runtime image),
and each `shim.call("method", args...)` invokes one of the submission's
`public static` methods in the sandbox and returns its result. The grader sets
`SHIM_SOCKET` to the shared socket for both containers of a shimmed round;
without that wiring the suite could not test anything, so refusing it is an
infrastructure error rather than a mark.

`manifest.json` is required, and it is what decides which tests exist: the
grader records one result per declared test and discards output naming anything
else, because a submission shares standard output with the reporter and can
print convincing test lines of its own. The suite is compiled against the baked
JUnit console jar and run through `TapRunner` (also baked into the image), which
emits one `ok N - <display name>` / `not ok N - <display name>` line per test;
each `@DisplayName` must equal its manifest entry's `name` exactly, so keep the
two in step.
