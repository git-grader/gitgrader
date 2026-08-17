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
# shellcheck disable=SC2034  # read by the scripts that source this file
ALPINE_IMAGE="alpine:3.24"
