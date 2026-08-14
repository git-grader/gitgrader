#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/restore.sh <backup-directory>

Restore a backup made by scripts/backup.sh into the current Compose project's
named volumes and PostgreSQL database. This destroys the current application
data. Run this only after a tested backup; the application is stopped for you.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac
if [[ $# -ne 1 || ! -d "$1" ]]; then
  usage >&2
  exit 2
fi

fail() { printf 'restore: %s\n' "$1" >&2; exit 1; }

backup="$(realpath "$1")"
for required in postgresql.dump git-data.tar.gz grading-data.tar.gz templates.tar.gz tests.tar.gz artifacts.tar.gz SHA256SUMS; do
  [[ -f "$backup/$required" ]] || fail "Missing backup file: $required"
done
(cd "$backup" && sha256sum --check SHA256SUMS)

: "${POSTGRES_DB:=gitgrader}"
: "${POSTGRES_USER:=gitgrader}"

# Stopped before anything is destroyed, not merely asked for in the usage text. The
# volumes emptied below are mounted into the application, so a running one goes on
# writing repositories and workspaces into a tree being deleted underneath it, and
# its open connections then make the dropdb below fail - after the files are gone.
docker compose stop app >/dev/null

docker compose up -d database

deadline=$((SECONDS + 120))
until docker compose exec -T database pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; do
  ((SECONDS < deadline)) || fail 'The database did not become ready within 120s.'
  sleep 1
done

# Asked of Compose rather than derived from the checkout directory: compose.yaml
# pins the project name and both `-p` and COMPOSE_PROJECT_NAME override it, so a
# name reconstructed here restores into volumes the application never reads.
project="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' \
  "$(docker compose ps -aq database | head -n1)")"
[[ -n $project ]] || fail 'Could not determine the Compose project name.'

# The database is restored below through pg_restore, against the server started
# above, and is deliberately absent here. Emptying and re-extracting its volume in
# this loop would delete a data directory while that same postmaster is running on
# it, which corrupts the cluster rather than restoring it.
for volume in git-data grading-data templates tests artifacts; do
  resolved_volume="${project}_${volume}"
  docker volume create "$resolved_volume" >/dev/null
  docker run --rm --network none -v "${resolved_volume}:/target" -v "$backup:/backup:ro" alpine:3.21 \
    sh -ec 'find /target -mindepth 1 -maxdepth 1 -exec rm -rf {} +; tar -C /target -xzf "/backup/$1.tar.gz"' _ "$volume"
done

docker compose exec -T database sh -c "dropdb -U '$POSTGRES_USER' --if-exists '$POSTGRES_DB' && createdb -U '$POSTGRES_USER' '$POSTGRES_DB'"
docker compose exec -T database pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists <"$backup/postgresql.dump"
printf 'Restore completed from %s. Start app and run scripts/verify-install.sh.\n' "$backup"
