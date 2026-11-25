#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

log=$(mktemp)
trap 'rm -f -- "$log"' EXIT
if ./gradlew --console=plain "$@" 2>&1 | tee "$log"; then
    exit 0
else
    statuses=("${PIPESTATUS[@]}")
fi

# Retry one Maven Central throttle before tasks start. A dependency failure
# during task execution can coexist with compiler or test failures.
if ((statuses[1] != 0)); then
    exit "${statuses[1]}"
fi
if grep -q '^> Task ' "$log" ||
    ! grep -Fq "A problem occurred configuring root project 'anonero'." "$log" ||
    ! grep -Fq "Could not resolve all artifacts for configuration 'classpath'." "$log" ||
    ! awk '
        /Could not (GET|HEAD) .*https:\/\/repo\.maven\.apache\.org\/maven2\// {
            if (getline && /Received status code 429 from server:/) throttled = 1
        }
        END { exit !throttled }
    ' "$log"; then
    exit "${statuses[0]}"
fi

echo '::warning::Maven Central throttled Gradle configuration. Retrying once in 60 seconds.'
sleep 60
./gradlew --console=plain "$@"
