#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0
#
# Brings up a working GitGrader on this machine.
set -euo pipefail

cd "$(dirname "$0")/.."

WITH_DEMO=false

usage() {
  cat <<'EOF'
Usage: scripts/install.sh [--demo]

Installs and starts GitGrader.

  --demo   also load the sample course, so there is something to grade

Run it again at any time; every step is skipped when it is already done.
EOF
}

for arg in "$@"; do
  case "$arg" in
    --demo) WITH_DEMO=true ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n\n' "$arg" >&2; usage >&2; exit 2 ;;
  esac
done

step() { printf '\n==> %s\n' "$1"; }
fail() { printf '\nInstallation stopped: %s\n' "$1" >&2; exit 1; }

step 'Checking what is installed'
command -v docker >/dev/null || fail 'Docker is not installed. See https://docs.docker.com/engine/install/'
docker info >/dev/null 2>&1 || fail 'Docker is installed but not usable by this user. Add yourself to the docker group, or run with sudo.'
docker compose version >/dev/null 2>&1 || fail 'The Docker Compose plugin is missing. See https://docs.docker.com/compose/install/'
printf '    Docker and Compose are ready.\n'

step 'Preparing configuration'
if [[ ! -f .env ]]; then
  cp .env.example .env
  printf '    Wrote .env from .env.example.\n'
else
  printf '    Keeping the .env you already have.\n'
fi
# .env holds the database and directory passwords, and both `cp` and an editor
# create it readable by every account on the host. Narrowed whether this run wrote
# it or an earlier one did, because the earlier one left it at 0644 too.
chmod 600 .env

# Values that describe this machine or this checkout, not a preference. Each one is
# wrong as a shipped default, so the installer settles it and says what it chose.
set_env() {
  if grep -q "^$1=" .env; then
    sed -i "s|^$1=.*|$1=$2|" .env
  else
    printf '%s=%s\n' "$1" "$2" >> .env
  fi
}

# The grading runner talks to the Docker socket and the application runs
# unprivileged, so it has to join the group that owns the socket. The number
# differs per machine, which is why it cannot ship as a default.
socket_gid="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo '')"
[[ -n "$socket_gid" ]] || fail 'Could not read /var/run/docker.sock. Is Docker running?'
set_env DOCKER_GID "$socket_gid"
printf '    Docker socket group is %s.\n' "$socket_gid"

# --demo starts a directory to sign in against, so it has to be switched on. The
# example configuration ships it off, which is right for a real deployment and wrong
# here: without this the only authentication left is a local account list that is
# empty, and every sign-in is refused.
#
# The session cookie goes the same way. It ships restricted to HTTPS, which is what a
# real deployment needs and what the documentation promises, but this instance is
# reached over plain HTTP on localhost - so the browser would hold the cookie back and
# no sign-in could complete. Relaxing it belongs to the demo, not to the file every
# deployment is built from.
if [[ "$WITH_DEMO" == true ]]; then
  set_env SECURITY_LDAP_ENABLED true
  set_env SECURITY_SESSION_SECURE_COOKIE false
  printf '    Demo directory enabled for sign-in.\n'
  printf '    Session cookie relaxed to HTTP for this demo; do not carry that .env into a deployment.\n'
fi

# The version is the one this checkout builds, read from the POM and written into .env
# so that compose asks for exactly what was produced here.
#
# Taking it from .env instead is what let the two disagree. A source tree is
# 0.1.0-SNAPSHOT while the example configuration named 0.1.0, so `./mvnw
# spring-boot:build-image` made an image compose would never start, and this script
# papered over that by tagging a working-tree build with a released version number -
# which then shadows the real 0.1.0 for every later pull, on this machine, silently.
project_version() {
  # The project's own <version>, which is the one that follows its <artifactId>. The
  # first <version> in the file belongs to the parent and is Spring Boot's.
  awk '/<artifactId>gitgrader<\/artifactId>/ { seen = 1; next }
       seen && match($0, /<version>[^<]+<\/version>/) {
         print substr($0, RSTART + 9, RLENGTH - 19); exit
       }' backend/pom.xml
}

version="$(project_version)"
[[ -n "$version" ]] || fail 'Could not read the project version from backend/pom.xml.'
set_env GITGRADER_VERSION "$version"
image="ghcr.io/git-grader/gitgrader:${version}"
printf '    This checkout is version %s.\n' "$version"

# A tag is not a version of the source: it stays the same while the working tree moves
# on, so "an image with that name exists" says nothing about whether it holds the code
# sitting here. Deciding on the name alone is what makes a fix appear not to work - this
# script reports success, compose starts the image it already had, and the endpoint that
# was repaired is still a 404. Fingerprinting what goes into the image instead rebuilds
# exactly when the source moved, and not merely because install was run twice.
#
# Not the commit hash: an uncommitted fix is still a fix, and whoever is testing one
# would otherwise be told the image was current.
IMAGE_SOURCES=(
  pom.xml backend/pom.xml backend/src
  frontend/src frontend/public frontend/index.html
  frontend/package.json frontend/package-lock.json
  frontend/tsconfig.json frontend/vite.config.ts
)
stamp='backend/target/image-source.sha256'

source_fingerprint() {
  { find "${IMAGE_SOURCES[@]}" -type f -print0 2>/dev/null || true; } |
    LC_ALL=C sort -z |
    xargs -0 sha256sum |
    sha256sum |
    cut -d' ' -f1
}

fingerprint="$(source_fingerprint)"

step "Making sure ${image} matches this checkout"
if ! docker image inspect "$image" >/dev/null 2>&1; then
  rebuild='It is not built yet.'
elif [[ ! -f "$stamp" ]]; then
  rebuild='It was not built from this checkout.'
elif [[ "$(cat "$stamp")" != "$fingerprint" ]]; then
  rebuild='The source has changed since it was built.'
fi

if [[ -z "${rebuild:-}" ]]; then
  printf '    Already built from this source.\n'
elif command -v java >/dev/null; then
  printf '    %s Building it. The first build downloads a lot and takes a few minutes.\n' "$rebuild"
  # Buildpacks produce the image; there is no Dockerfile to hand to Docker.
  ./mvnw -B spring-boot:build-image -pl backend -DskipTests \
    -Dspring-boot.build-image.imageName="$image"
  mkdir -p "$(dirname "$stamp")"
  printf '%s\n' "$fingerprint" > "$stamp"
elif docker image inspect "$image" >/dev/null 2>&1; then
  # Refusing to start would be worse than starting: a published image is a supported
  # way to run this. But saying nothing is what left the last person debugging code
  # that was never running, so say plainly which one is about to start.
  printf '    %s Java is missing, so it cannot be rebuilt here.\n' "$rebuild"
  printf '    Starting the image that is already on this machine. If you are testing a\n'
  printf '    change, install JDK 25 and run this again, or it will not be included.\n'
else
  fail "The image is not built and Java is missing, so it cannot be built here. Install JDK 25, or pull a published image."
fi

step 'Starting GitGrader'
# The data volumes are chowned to the uid the application runs as before it starts, and
# that uid comes from whatever base the image was built on. Reading it back from the
# image keeps the two in step; a shipped default only holds until a rebuild moves it,
# and when it moves the application cannot write to its own volumes.
image_user="$(docker image inspect "$image" --format '{{.Config.User}}')"
if [[ "$image_user" == *:* ]]; then
  set_env APP_UID "${image_user%%:*}"
  set_env APP_GID "${image_user##*:}"
  printf '    Data volumes will be owned by %s.\n' "$image_user"
fi

# The sample course needs somebody to sign in as, and the directory that provides
# that is part of the development overlay.
COMPOSE=(docker compose -f compose.yaml)
if [[ "$WITH_DEMO" == true ]]; then
  COMPOSE+=(-f compose.dev.yaml)
fi
"${COMPOSE[@]}" up -d

http_port="$(grep -E '^HTTP_PORT=' .env | cut -d= -f2- || true)"
http_port="${http_port:-8080}"
ssh_port="$(grep -E '^SSH_PORT=' .env | cut -d= -f2- || true)"
ssh_port="${ssh_port:-2222}"
base="http://localhost:${http_port}"

step 'Waiting for it to answer'
for _ in $(seq 1 60); do
  if curl -sf -o /dev/null "${base}/actuator/health/readiness" 2>/dev/null; then
    ready=true
    break
  fi
  sleep 5
done
[[ "${ready:-false}" == true ]] || fail "It did not become ready. Look at: docker compose logs app"
printf '    Ready.\n'

if [[ "$WITH_DEMO" == true ]]; then
  step 'Loading the sample course'
  example='examples/assignments/assignment-01-string-utils'

  "${COMPOSE[@]}" exec -T database psql -U gitgrader -d gitgrader -q < examples/seed-data.sql
  printf '    Course, assignments and runtime recorded.\n'

  # The template and the hidden tests live on volumes the application reads, and
  # it reads them as the unprivileged user baked into the image, so the files
  # have to belong to that user, which was read back from the image above.
  owner="${image_user:-1002:1001}"

  app="$("${COMPOSE[@]}" ps -q app)"
  volume_at() {
    docker inspect "$app" --format "{{range .Mounts}}{{if eq .Destination \"$1\"}}{{.Name}}{{end}}{{end}}"
  }
  templates_volume="$(volume_at /data/templates)"
  tests_volume="$(volume_at /data/tests)"
  [[ -n "$templates_volume" && -n "$tests_volume" ]] || fail 'Could not find the storage volumes on the running container.'

  tar -C "$example" -cf - template hidden-tests |
    docker run --rm -i \
      -v "${templates_volume}:/t" -v "${tests_volume}:/s" \
      alpine:3.20 sh -c "
        set -e
        mkdir -p /tmp/x && tar -C /tmp/x -xf -
        mkdir -p '/t/${example}' '/s/${example}'
        cp -r /tmp/x/template     '/t/${example}/'
        cp -r /tmp/x/hidden-tests '/s/${example}/'
        chown -R ${owner} /t /s"
  printf '    Starter project and hidden tests staged.\n'
fi

cat <<EOF

==> GitGrader is running

  Web        ${base}
  Git (SSH)  localhost:${ssh_port}

EOF

if [[ "$WITH_DEMO" == true ]]; then
  cat <<'EOF'
The sample course is loaded. To try the whole path a student takes, follow
docs/manual-testing.md, which starts from here.

Sign in with the development directory: instructor / password

EOF
else
  cat <<'EOF'
There is no course yet, and nobody can sign in until you point the application
at your directory: set SECURITY_LDAP_ENABLED=true and the SECURITY_LDAP_* values
in .env, then start it again. docs/installation.md walks through it.

To look around first without any of that, run this again with "--demo".

EOF
fi

printf 'Before anyone else uses this, read docs/installation.md: the passwords in\n'
printf '.env are the example ones, and the application expects to sit behind HTTPS.\n\n'
printf 'Stop it with: %s down\n' "${COMPOSE[*]}" 
