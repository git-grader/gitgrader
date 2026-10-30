# Grader test-suite contract

This directory is stored outside the student project and mounted read-only at
`/opt/hidden-tests` in the suite container of a shimmed round. The submission is
graded in a separate sandbox container that never sees these hidden sources; the
suite reaches the submission over the shim socket that the two containers share.
`GeoUtilsTest` opens that channel in its `@BeforeAll`:

```java
import gitgrader.ShimClient;

private static ShimClient client;

@BeforeAll
static void connect() {
    client = ShimClient.connect(null, 0);
}
```

The client proxies the sandbox container's compiled solution, so each test calls
the submission's methods through `client.call("name", args)` instead of
importing a `SOLUTION_PATH` directly. The grader sets `SHIM_SOCKET` to the
shared socket for both containers of a shimmed round; without that wiring the
suite could not test anything, so refusing it is an infrastructure error rather
than a mark.

The runtime image bakes the compiled shim into `/opt/gitgrader-shim/classes`,
Maven and the JUnit 5 stack into its local repository, and runs with no network,
so building this suite needs no install step and no extra repository. Because
`/opt/hidden-tests` is mounted read-only, the suite command copies the project
to `/tmp` first (Maven writes `target/` next to the sources) and prints the
Surefire XML reports on standard output afterwards, which is where the backend's
parser reads them from.

`manifest.json` is required, and it is what decides which tests exist: the
grader records one result per declared test and discards any `<testcase>`
naming anything else, because a submission shares standard output with the
report and can print convincing fragments of its own. A declared test the
report never named is scored as never having run. Surefire writes the bare
method name into the `name` attribute of each `<testcase>`, so manifest
entries are method names (`testH01...`), not `ClassName.methodName`.
