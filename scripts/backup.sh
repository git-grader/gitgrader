#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/backup.sh [destination-directory]

Create a timestamped logical PostgreSQL dump and compressed archives of the
named GitGrader Docker volumes. The Compose project must be running. The default
destination is ./backups. Set COMPOSE_PROJECT_NAME if the project has a custom
name. See docs/backup-restore.md before relying on a backup.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac
if [[ $# -gt 1 ]]; then
  usage >&2
  exit 2
fi

destination_root="${1:-./backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
destination="${destination_root%/}/gitgrader-${timestamp}"
mkdir -p "$destination"

: "${POSTGRES_DB:=gitgrader}"
: "${POSTGRES_USER:=gitgrader}"
project="${COMPOSE_PROJECT_NAME:-$(basename "$(pwd)")}" 

docker compose exec -T database pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
  >"$destination/postgresql.dump"

for volume in database git-data grading-data templates tests artifacts; do
  resolved_volume="${project}_${volume}"
  docker run --rm -v "${resolved_volume}:/source:ro" -v "$(realpath "$destination"):/backup" \
    alpine:3.21 tar -C /source -czf "/backup/${volume}.tar.gz" .
done

sha256sum "$destination"/* >"$destination/SHA256SUMS"
printf 'Backup created: %s\n' "$destination"
