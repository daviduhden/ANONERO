#!/usr/bin/env bash
#
# Copyright (c) 2026, David Uhden Collado
# Copyright (c) 2022-2026, r4v3r23
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Runs the connected tests on the emulator started by android-emulator-runner.
#
# The runner executes the `script` input line by line, so shell loops and
# multi-line conditionals cannot live there. Keep the orchestration here.
set -euo pipefail

adb wait-for-device
# The KVM-less software emulator is still settling right after boot. The first
# instrumentation attach can time out; give ART/package work time to finish.
sleep 60

set +e
./gradlew --no-daemon --continue --stacktrace -PanonDisableSplits=true \
    :app:connectedAnonDebugAndroidTest \
    :app:connectedNeroDebugAndroidTest
status=$?

# Retry only a flavor whose report says "failed to attach". Real assertion,
# crash, or test failures are not retried; the XML validation step is final.
for flavor in anon nero; do
    report=$(find app/build/outputs/androidTest-results \
        -path "*flavors/${flavor}/*" -name 'TEST-*.xml' | head -n 1)
    if [ -n "$report" ] && grep -q "failed to attach" "$report"; then
        case "$flavor" in
            anon) task=:app:connectedAnonDebugAndroidTest ;;
            nero) task=:app:connectedNeroDebugAndroidTest ;;
        esac
        echo "Retrying ${flavor} after an instrumentation attach timeout"
        ./gradlew --no-daemon --continue --stacktrace -PanonDisableSplits=true "$task"
        status=$?
    fi
done

exit "$status"
