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

# The JUnit 5 API and Surefire are resolved into the default local repository
# now, at build time, so `mvn -o` at grading time never dials out. The suite's
# pom may not name anything else; anything beyond this set makes an offline run
# fail loudly rather than silently grading nothing.
RUN mkdir -p /tmp/priming && cd /tmp/priming \
    && printf '%s\n' \
        '<?xml version="1.0" encoding="UTF-8"?>' \
        '<project xmlns="http://maven.apache.org/POM/4.0.0">' \
        '  <modelVersion>4.0.0</modelVersion>' \
        '  <groupId>org.gitgrader.runtime</groupId>' \
        '  <artifactId>java-27-warmup</artifactId>' \
        '  <version>1.0.0</version>' \
        '  <properties>' \
        '    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>' \
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
    && mvn --batch-mode --quiet dependency:go-offline \
    && rm -rf /tmp/priming

RUN mkdir /workspace && chown 65534:65534 /workspace

# The shim is compiled at build time: the sandbox runs the server from baked
# classes, the suite compiles its tests against the same baked classes, and
# neither half ever needs a compiler flag it could get wrong.
COPY java-shim/ /tmp/java-shim/
RUN mkdir -p /opt/gitgrader-shim/classes /opt/gitgrader-shim/src \
    && cp /tmp/java-shim/*.java /opt/gitgrader-shim/src/ \
    && javac --release 27 -d /opt/gitgrader-shim/classes /opt/gitgrader-shim/src/*.java \
    && rm -rf /tmp/java-shim

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`.
CMD ["java -cp /opt/gitgrader-shim/classes gitgrader.ShimServer"]
