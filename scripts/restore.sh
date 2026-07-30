#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/restore.sh <backup-directory>

Restore a backup made by scripts/backup.sh into the current Compose project's
named volumes and PostgreSQL database. This destroys the current application
data. Stop the application first and run this only after a tested backup.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac
if [[ $# -ne 1 || ! -d "$1" ]]; then
  usage >&2
  exit 2
fi

backup="$(realpath "$1")"
for required in postgresql.dump database.tar.gz git-data.tar.gz grading-data.tar.gz templates.tar.gz tests.tar.gz artifacts.tar.gz SHA256SUMS; do
  [[ -f "$backup/$required" ]] || { printf 'Missing backup file: %s\n' "$required" >&2; exit 1; }
done
(cd "$backup" && sha256sum --check SHA256SUMS)

: "${POSTGRES_DB:=gitgrader}"
: "${POSTGRES_USER:=gitgrader}"
project="${COMPOSE_PROJECT_NAME:-$(basename "$(pwd)")}" 

docker compose up -d database
until docker compose exec -T database pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null; do sleep 1; done

for volume in database git-data grading-data templates tests artifacts; do
  resolved_volume="${project}_${volume}"
  docker volume create "$resolved_volume" >/dev/null
  docker run --rm -v "${resolved_volume}:/target" -v "$backup:/backup:ro" alpine:3.21 \
    sh -c "rm -rf /target/* /target/.[!.]* /target/..?* 2>/dev/null || true; tar -C /target -xzf /backup/${volume}.tar.gz"
done

docker compose exec -T database sh -c "dropdb -U '$POSTGRES_USER' --if-exists '$POSTGRES_DB' && createdb -U '$POSTGRES_USER' '$POSTGRES_DB'"
docker compose exec -T database pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists <"$backup/postgresql.dump"
printf 'Restore completed from %s. Start app and run scripts/verify-install.sh.\n' "$backup"
