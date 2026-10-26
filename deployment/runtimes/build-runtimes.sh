#!/bin/sh
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -eu

# CDPATH is cleared for this command so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
image_prefix=${IMAGE_NAME_PREFIX:-gitgrader-}
image_tag=${IMAGE_TAG:-local}

for runtime_dir in "$script_dir"/node-* "$script_dir"/python-* "$script_dir"/java-*; do
	[ -d "$runtime_dir" ] || continue
	[ -f "$runtime_dir/Dockerfile" ] || continue
	key=${runtime_dir##*/}
	case $key in
		# The shim directories are shared sources, not runtimes; each also has
		# no Dockerfile, but the case keeps the loop's intent explicit.
		node-shim|python-shim|java-shim) continue ;;
	esac
	major=${key#*-}
	image=${image_prefix}${key}:${image_tag}

	printf '%s\n' "Building $key from $runtime_dir"
	# The Dockerfile bakes the shared shim, so the build context is the
	# parent directory that contains both the runtime and the shim, not the
	# runtime directory alone.
	docker build --tag "$image" -f "$runtime_dir/Dockerfile" "$script_dir"
	digest=$(docker inspect --format='{{index .RepoDigests 0}}' "$image" 2>/dev/null || true)

	printf '%s\n' "Built image: $image"
	printf '%s\n' "Repository digest: ${digest:-not available for a local-only tag}"
	printf '%s' 'Runtime registration YAML:'
	printf '%s\n' ''
	printf '%s\n' "runtimeKey: $key"
	case $key in
		node-*)
			major=${key#node-}
			printf '%s\n' "displayName: Node.js $major"
			printf '%s\n' 'installCommand: (none; Jasmine is baked in and the sandbox runs without network)'
			printf '%s\n' 'testCommand: cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs'
			printf '%s\n' 'reportFormat: TAP'
			printf '%s\n' 'jasmineVersion: 7.0.0 (baked into the runtime image)'
			;;
		python-*)
			major=${key#python-}
			printf '%s\n' "displayName: Python $major"
			printf '%s\n' 'installCommand: (none; pytest is baked in and the sandbox runs without network)'
			printf '%s\n' 'testCommand: cd /opt/hidden-tests && python -m pytest -p tap_reporter --gitgrader-tap -p no:cacheprovider -q'
			printf '%s\n' 'reportFormat: TAP'
			printf '%s\n' 'pytestVersion: 9.0.2 (baked into the runtime image)'
			;;
		java-*)
			major=${key#java-}
			printf '%s\n' "displayName: Java $major"
			printf '%s\n' 'installCommand: (none; Maven and the JUnit 5 stack are baked in and the sandbox runs without network)'
			printf '%s\n' 'testCommand: cd /opt/hidden-tests && mvn --offline --batch-mode test'
			printf '%s\n' 'reportFormat: JUNIT_XML'
			printf '%s\n' 'junitVersion: 5.11.4, surefire 3.5.2 (baked into the runtime image)'
			;;
	esac
	printf '%s\n' "image: $image"
	printf '%s\n' "imageDigest: ${digest#*@}"
	printf '%s\n' 'shimKind: node-ipc (the shim round runs the sandbox and the suite apart)'
	printf '\n'
done
