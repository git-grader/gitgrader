#!/bin/sh
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -eu

# CDPATH is cleared for this command so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
image_prefix=${IMAGE_NAME_PREFIX:-gitgrader-}
image_tag=${IMAGE_TAG:-local}

# Build the image for every runtime whose directory contains a Dockerfile. The
# build context is always the `deployment/runtimes` directory that contains all
# runtimes, so a Dockerfile can `COPY <runtime>-shim/ /opt/gitgrader-shim/` to
# bake the shared shim regardless of which runtime is building.
for runtime_dir in "$script_dir"/*-*/; do
	[ -d "$runtime_dir" ] || continue
	[ -f "$runtime_dir/Dockerfile" ] || continue
	key=${runtime_dir%/}
	key=${key##*/}
	case "$key" in
	node-*)
		runtime_type=node
		;;
	python-*)
		runtime_type=python
		;;
	java-*)
		runtime_type=java
		;;
	*)
		continue
		;;
	esac
	image=${image_prefix}${key}:${image_tag}

	printf '%s\n' "Building $key from $runtime_dir"
	docker build --tag "$image" -f "$runtime_dir/Dockerfile" "$script_dir"
	digest=$(docker inspect --format='{{index .RepoDigests 0}}' "$image" 2>/dev/null || true)

	printf '%s\n' "Built image: $image"
	printf '%s\n' "Repository digest: ${digest:-not available for a local-only tag}"
	printf '%s' 'Runtime registration YAML:'
	printf '%s\n' ''
	printf '%s\n' "runtimeKey: $key"
	case "$runtime_type" in
	node)
		major=${key#node-}
		display_name="Node.js $major"
		install_command='(none; Jasmine is baked in and the sandbox runs without network)'
		test_command='cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs'
		shim_kind='node-ipc'
		shim_command=''
		extra_info="jasmineVersion: 7.0.0 (baked into the runtime image)"
		;;
	python)
		display_name="Python 3.14"
		install_command='(none; pytest is baked in and the sandbox runs without network)'
		test_command='cd /opt/hidden-tests && python3 /opt/gitgrader-shim/tap_runner.py'
		shim_kind='python-ipc'
		shim_command='python3 /opt/gitgrader-shim/server.py'
		extra_info="pytestVersion: 8.4.2 (baked into the runtime image); hidden suites report TAP via /opt/gitgrader-shim/tap_runner.py"
		;;
	java)
		major=${key#java-}
		display_name="Java $major"
		install_command='(none; junit-platform-console-standalone is baked in and the sandbox runs without network)'
		test_command='cd /opt/hidden-tests && javac -cp /opt/junit-platform-console-standalone.jar:/opt/gitgrader-shim -d /tmp/classes *.java && java -cp /tmp/classes:/opt/gitgrader-shim:/opt/junit-platform-console-standalone.jar TapRunner'
		shim_kind='java-ipc'
		shim_command='java -cp /opt/gitgrader-shim Server'
		extra_info="eclipse-temurin ${major} (JDK version pinned in the runtime Dockerfile); junit-platform-console-standalone 6.1.3 (baked into the runtime image)"
		;;
	esac
	printf '%s\n' "displayName: $display_name"
	printf '%s\n' "image: $image"
	printf '%s\n' "imageDigest: ${digest#*@}"
	printf '%s\n' "installCommand: $install_command"
	printf '%s\n' "testCommand: $test_command"
	printf '%s\n' 'reportFormat: TAP'
	printf '%s\n' "shimKind: $shim_kind"
	if [ -n "$shim_command" ]; then
		printf '%s\n' "shimCommand: $shim_command"
	fi
	printf '%s\n' "$extra_info"
	printf '\n'
done
