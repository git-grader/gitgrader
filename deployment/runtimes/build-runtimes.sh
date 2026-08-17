#!/bin/sh
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -eu

# CDPATH is cleared for this one command so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
runtime_dir=$(CDPATH= cd -- "$(dirname -- "$0")/node-24" && pwd)
image=${IMAGE_NAME:-gitgrader-node-24:local}

docker build --tag "$image" "$runtime_dir"
digest=$(docker inspect --format='{{index .RepoDigests 0}}' "$image" 2>/dev/null || true)

printf '%s\n' "Built image: $image"
printf '%s\n' "Repository digest: ${digest:-not available for a local-only tag}"
printf '%s\n' 'Runtime registration YAML:'
printf '%s\n' 'runtimeKey: node-24'
printf '%s\n' 'displayName: Node.js 24'
printf '%s\n' "image: $image"
printf '%s\n' "imageDigest: ${digest#*@}"
printf '%s\n' 'installCommand: npm ci --ignore-scripts'
printf '%s\n' 'testCommand: node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js'
printf '%s\n' 'reportFormat: TAP'
