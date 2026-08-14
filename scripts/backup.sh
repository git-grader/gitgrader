#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/backup.sh [destination-directory]

Create a timestamped logical PostgreSQL dump and compressed archives of the
named GitGrader Docker volumes. The Compose project must be running. The default
destination is ./backups. See docs/backup-restore.md before relying on a backup.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac
if [[ $# -gt 1 ]]; then
  usage >&2
  exit 2
fi

fail() { printf 'backup: %s\n' "$1" >&2; exit 1; }

# Asks Compose what it called this project rather than deriving a name here.
# compose.yaml pins one, and both `-p` and COMPOSE_PROJECT_NAME override it, so a
# name reconstructed from the checkout directory is wrong in three separate ways.
# The label Compose stamped on its own container is the one answer never guessed.
compose_project() {
  local container
  container="$(docker compose ps -aq database 2>/dev/null | head -n1)"
  [[ -n $container ]] || fail 'The Compose project is not running. Start it before taking a backup.'
  docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$container"
}

destination_root="${1:-./backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
destination="${destination_root%/}/gitgrader-${timestamp}"
mkdir -p "$destination"
destination="$(realpath "$destination")"

: "${POSTGRES_DB:=gitgrader}"
: "${POSTGRES_USER:=gitgrader}"
project="$(compose_project)"

docker compose exec -T database pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
  >"$destination/postgresql.dump"

# The database is captured by the logical dump above and by nothing else. Tarring
# its volume as well would copy a data directory out from under a running
# postmaster: the archive is torn across checkpoints, `pg_restore` cannot use it,
# and restoring it is what corrupts a cluster. Every other volume is a plain file
# tree that a live copy represents accurately.
for volume in git-data grading-data templates tests artifacts; do
  resolved_volume="${project}_${volume}"
  # Checked rather than assumed. Docker answers a mount of a volume that does not
  # exist by creating it empty, so a wrong name does not fail here: it writes a
  # well-formed archive of nothing, and the loss is discovered at restore.
  docker volume inspect "$resolved_volume" >/dev/null 2>&1 \
    || fail "No such volume: ${resolved_volume}. Refusing to write an empty archive."
  docker run --rm --network none -v "${resolved_volume}:/source:ro" -v "${destination}:/backup" \
    alpine:3.21 tar -C /source -czf "/backup/${volume}.tar.gz" .
done

# Recorded as bare file names. Listing them by the path they were written to bakes
# the caller's working directory into the manifest, and the restore verifies from
# inside the backup directory, where that path resolves to nothing at all - so
# every backup taken to the default destination failed its own checksum check.
(cd "$destination" && sha256sum -- postgresql.dump *.tar.gz >SHA256SUMS)
printf 'Backup created: %s\n' "$destination"
