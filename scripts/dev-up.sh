#!/usr/bin/env bash
set -euo pipefail

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

# The image comes from buildpacks rather than a Dockerfile, so there is nothing for
# compose to build. Produce it here when it is missing instead of failing later with an
# image-not-found error that gives no hint about what to run.
image="ghcr.io/git-grader/gitgrader:${GITGRADER_VERSION:-0.1.0-SNAPSHOT}"
if ! docker image inspect "$image" >/dev/null 2>&1; then
  printf 'Building %s with buildpacks...\n' "$image"
  ./mvnw -B spring-boot:build-image -pl backend -DskipTests \
    -Dspring-boot.build-image.skip=false
fi

docker compose -f compose.yaml -f compose.dev.yaml up -d "$@"
