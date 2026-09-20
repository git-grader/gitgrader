#!/bin/sh
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -eu

# CDPATH is cleared for this command so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
image_prefix=${IMAGE_NAME_PREFIX:-gitgrader-}
image_tag=${IMAGE_TAG:-local}

for runtime_dir in "$script_dir"/node-*; do
	[ -d "$runtime_dir" ] || continue
	key=${runtime_dir##*/}
	major=${key#node-}
	image=${image_prefix}${key}:${image_tag}

	printf '%s\n' "Building $key from $runtime_dir"
	# The Dockerfile bakes the shared node-shim, so the build context is the
	# parent directory that contains both the runtime and the shim, not the
	# runtime directory alone.
	docker build --tag "$image" -f "$runtime_dir/Dockerfile" "$script_dir"
	digest=$(docker inspect --format='{{index .RepoDigests 0}}' "$image" 2>/dev/null || true)

	printf '%s\n' "Built image: $image"
	printf '%s\n' "Repository digest: ${digest:-not available for a local-only tag}"
	printf '%s' 'Runtime registration YAML:'
	printf '%s\n' ''
	printf '%s\n' "runtimeKey: $key"
	printf '%s\n' "displayName: Node.js $major"
	printf '%s\n' "image: $image"
	printf '%s\n' "imageDigest: ${digest#*@}"
	printf '%s\n' 'installCommand: npm ci --ignore-scripts'
	printf '%s\n' 'testCommand: node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js'
	printf '%s\n' 'reportFormat: TAP'
	printf '\n'
done