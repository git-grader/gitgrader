#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# Every path below is relative to the repository, not to wherever this was called from,
# so the script has to stand where it expects to. Without this, running it by its full
# path wrote a fresh .env into the caller's directory and then failed on ./mvnw.
cd "$(dirname "$0")/.."

# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/dev-up.sh [docker-compose arguments...]

Start the source-built development stack using compose.yaml and compose.dev.yaml.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

if [[ ! -f .env ]]; then
  cp .env.example .env
  printf 'Created .env from .env.example; review passwords before sharing this environment.\n'
fi

# The shipped .env carries DOCKER_GID=999, a placeholder: the group that owns the
# socket differs per host, which is why compose.yaml makes it required rather than
# defaulted. Left as shipped, the stack comes up, readiness passes, and every
# submission then fails with a permission error long after startup looked fine.
# scripts/install.sh settles this for a real install; without it here the documented
# quickstart inherits the one value guaranteed to be wrong on this machine.
socket_gid="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || true)"
if [[ -n "$socket_gid" ]]; then
  set_env DOCKER_GID "$socket_gid"
  printf 'Docker socket group is %s.\n' "$socket_gid"
else
  printf 'Could not read /var/run/docker.sock; leaving DOCKER_GID as configured.\n' >&2
fi

# The image comes from buildpacks rather than a Dockerfile, so there is nothing for
# compose to build. Produce it here when it is missing instead of failing later with an
# image-not-found error that gives no hint about what to run.
#
# The version is read from backend/pom.xml rather than written out here, and handed to
# the build as -Dspring-boot.build-image.imageName. Left to its own default the build
# tags the image ${project.version} from the pom, so with GITGRADER_VERSION set this
# inspected one tag and built another - and the hardcoded literal was a second copy of a
# version that scripts/install.sh had already stopped duplicating. Same reading, same
# awk, as install.sh.
version="$(
  awk '/<artifactId>gitgrader<\/artifactId>/ { seen = 1; next }
       seen && match($0, /<version>[^<]+<\/version>/) {
         print substr($0, RSTART + 9, RLENGTH - 19); exit
       }' backend/pom.xml
)"
[[ -n $version ]] || {
  printf 'dev-up: could not read the project version from backend/pom.xml.\n' >&2
  exit 1
}
image="ghcr.io/git-grader/gitgrader:${GITGRADER_VERSION:-$version}"
if ! docker image inspect "$image" >/dev/null 2>&1; then
  printf 'Building %s with buildpacks...\n' "$image"
  ./mvnw -B spring-boot:build-image -pl backend -DskipTests \
    -Dspring-boot.build-image.skip=false \
    -Dspring-boot.build-image.imageName="$image"
fi

docker compose -f compose.yaml -f compose.dev.yaml up -d "$@"
