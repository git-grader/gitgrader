# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Java shim is baked at /opt/gitgrader-shim, outside /workspace; never mount
# the test suite inside the student tree.
#
# Maven and the JUnit 5 stack are installed at build time so the offline
# grading run never needs the network: `dependency:go-offline` resolves the
# plugin-and-dependency set the hidden suite needs into the image's local
# repository, and the suite command compiles and runs with `mvn --offline`,
# writing the JUnit XML the backend parses.
FROM bellsoft/liberica-openjdk-debian:27@sha256:c8b4fe2c477ee36b7666c4a036c8b224cc265371a9992fbf69e356ca3002096f

# Maven is installed here rather than inherited, so the pinned JDK digest stays
# the only moving input. Maven's own plugins land in the image's local
# repository during this step.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

# The JUnit 5 API, Surefire and every lifecycle plugin the suite's build runs
# are resolved into the shared local repository now, at build time, so `mvn -o`
# at grading time never dials out. `dependency:go-offline` alone is not
# enough: it skips the lifecycle-default plugins (resources et al.), whose
# versions come from the super-POM and whose own dependencies are only
# resolved when a build actually runs them. So the warmup runs a real
# `mvn test` on a project shaped exactly like an operator suite: the same
# pinned compiler and Surefire versions and one dummy JUnit test. A suite pom
# may not name anything beyond this set; anything else makes an offline run
# fail loudly rather than silently grading nothing.
RUN mkdir -p /tmp/priming/src/test/java && cd /tmp/priming \
    && printf '%s\n' \
        '<?xml version="1.0" encoding="UTF-8"?>' \
        '<project xmlns="http://maven.apache.org/POM/4.0.0">' \
        '  <modelVersion>4.0.0</modelVersion>' \
        '  <groupId>org.gitgrader.runtime</groupId>' \
        '  <artifactId>java-27-warmup</artifactId>' \
        '  <version>1.0.0</version>' \
        '  <properties>' \
        '    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>' \
        '    <maven.test.failure.ignore>true</maven.test.failure.ignore>' \
        '  </properties>' \
        '  <dependencies>' \
        '    <dependency>' \
        '      <groupId>org.junit.jupiter</groupId>' \
        '      <artifactId>junit-jupiter-api</artifactId>' \
        '      <version>5.11.4</version>' \
        '      <scope>test</scope>' \
        '    </dependency>' \
        '    <dependency>' \
        '      <groupId>org.junit.jupiter</groupId>' \
        '      <artifactId>junit-jupiter-engine</artifactId>' \
        '      <version>5.11.4</version>' \
        '      <scope>test</scope>' \
        '    </dependency>' \
        '  </dependencies>' \
        '  <build>' \
        '    <plugins>' \
        '      <plugin>' \
        '        <groupId>org.apache.maven.plugins</groupId>' \
        '        <artifactId>maven-compiler-plugin</artifactId>' \
        '        <version>3.13.0</version>' \
        '      </plugin>' \
        '      <plugin>' \
        '        <groupId>org.apache.maven.plugins</groupId>' \
        '        <artifactId>maven-surefire-plugin</artifactId>' \
        '        <version>3.5.2</version>' \
        '      </plugin>' \
        '    </plugins></build>' \
        '</project>' \
        > pom.xml \
    && printf '%s\n' \
        'import org.junit.jupiter.api.Test;' \
        'class WarmupTest {' \
        '    @Test' \
        '    void passes() {' \
        '    }' \
        '}' \
        > src/test/java/WarmupTest.java \
    && mvn --batch-mode --quiet -Dmaven.repo.local=/opt/maven-repository test \
    && rm -rf /tmp/priming

# Maven's local repository is primed into a shared, world-readable location
# rather than root's home: the grading containers run as an unprivileged user
# whose home does not exist, so the default ~/.m2 would be an empty offline
# repository and every build would fail. MAVEN_OPTS bakes the redirection so
# no command has to remember it.
ENV MAVEN_OPTS="-Dmaven.repo.local=/opt/maven-repository"

RUN mkdir /workspace && chown 65534:65534 /workspace

# The shim is compiled at build time: the sandbox runs the server from baked
# classes, the suite compiles its tests against the same baked classes, and
# neither half ever needs a compiler flag it could get wrong. The classes are
# also installed into the primed local repository as a plain jar, so a hidden
# suite's pom resolves the client like any other offline dependency.
COPY java-shim/ /tmp/java-shim/
RUN mkdir -p /opt/gitgrader-shim/classes /opt/gitgrader-shim/src \
    && cp /tmp/java-shim/*.java /opt/gitgrader-shim/src/ \
    && javac --release 27 -d /opt/gitgrader-shim/classes /opt/gitgrader-shim/src/*.java \
    && rm -rf /tmp/java-shim \
    && mkdir -p /opt/maven-repository/gitgrader/gitgrader-shim/1.0.0 \
    && jar --create --file /opt/maven-repository/gitgrader/gitgrader-shim/1.0.0/gitgrader-shim-1.0.0.jar \
        -C /opt/gitgrader-shim/classes gitgrader

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`.
CMD ["java -cp /opt/gitgrader-shim/classes gitgrader.ShimServer"]
