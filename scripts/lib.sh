#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0
#
# Values the scripts share. Sourced, never executed.

# The helper image the scripts use to reach a volume they cannot mount directly.
#
# Named here rather than in each script because it was written out four times and drifted
# to three different versions: Compose was watched by Dependabot and the scripts were not,
# so backup and restore quietly kept running an alpine two releases behind the one the
# deployment used. The Quality workflow checks this against compose.yaml.
#
# Pinned by digest for the same reason compose.yaml pins it: a tag is a mutable name, and
# this image exists to run `tar` against backup archives on a host that may not trust the
# registry that served it. Dependabot cannot see a value in a shell file, which is why the
# digest check is a script rather than a bot.
# shellcheck disable=SC2034  # read by the scripts that source this file
ALPINE_IMAGE="alpine:3.24.2@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6"

# Writes a value into .env, replacing an existing key in place and appending a new one.
#
# Shared rather than copied into each script for the same reason as above: the escaping
# below is subtle, and a second copy of it is a second thing to keep correct.
set_env() {
  local key=$1 value=$2
  # In a sed replacement `&` stands for the whole match and the delimiter ends the
  # replacement, so a value carrying either is silently written as something other than
  # what was asked for. Every key here is a literal, but the values are not all under
  # our control - a Docker mount root is a path on the host.
  local escaped=${value//\\/\\\\}
  escaped=${escaped//&/\\&}
  escaped=${escaped//|/\\|}
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${escaped}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}
