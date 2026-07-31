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

# The grading runner talks to the Docker socket and the application runs
# unprivileged, so it has to join the group that owns the socket. The number
# differs per machine, which is why it cannot ship as a default.
socket_gid="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo '')"
[[ -n "$socket_gid" ]] || fail 'Could not read /var/run/docker.sock. Is Docker running?'
if grep -q '^DOCKER_GID=' .env; then
  sed -i "s/^DOCKER_GID=.*/DOCKER_GID=${socket_gid}/" .env
else
  printf 'DOCKER_GID=%s\n' "$socket_gid" >> .env
fi
printf '    Docker socket group is %s.\n' "$socket_gid"

# --demo starts a directory to sign in against, so it has to be switched on. The
# example configuration ships it off, which is right for a real deployment and wrong
# here: without this the only authentication left is a local account list that is
# empty, and every sign-in is refused.
if [[ "$WITH_DEMO" == true ]]; then
  if grep -q '^SECURITY_LDAP_ENABLED=' .env; then
    sed -i 's/^SECURITY_LDAP_ENABLED=.*/SECURITY_LDAP_ENABLED=true/' .env
  else
    printf 'SECURITY_LDAP_ENABLED=true\n' >> .env
  fi
  printf '    Demo directory enabled for sign-in.\n'
fi

# Read the version the compose file will ask for, so a rebuild is only done when
# that exact image is genuinely absent.
version="$(grep -E '^GITGRADER_VERSION=' .env | cut -d= -f2- || true)"
version="${version:-0.1.0}"
image="ghcr.io/git-grader/gitgrader:${version}"

step "Making sure ${image} exists"
if docker image inspect "$image" >/dev/null 2>&1; then
  printf '    Already built.\n'
else
  command -v java >/dev/null || fail "The image is not built and Java is missing, so it cannot be built here. Install JDK 25, or pull a published image."
  printf '    Building it. The first build downloads a lot and takes a few minutes.\n'
  # Buildpacks produce the image; there is no Dockerfile to hand to Docker.
  ./mvnw -B spring-boot:build-image -pl backend -DskipTests \
    -Dspring-boot.build-image.imageName="$image"
fi

step 'Starting GitGrader'
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
  # have to belong to that user. Asking the image avoids a number that goes stale.
  owner="$(docker image inspect "$image" --format '{{.Config.User}}')"
  owner="${owner:-1002:1001}"

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
