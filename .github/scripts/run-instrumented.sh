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

mkdir -p ci-diagnostics

core_services_ok() {
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || return 1
    adb shell service check package 2>/dev/null | grep -q "Service package: found" || return 1
    adb shell service check activity 2>/dev/null | grep -q "Service activity: found" || return 1
}

wait_for_core_services() {
    local deadline=$((SECONDS + 300))
    while ((SECONDS < deadline)); do
        if core_services_ok; then
            return 0
        fi
        sleep 5
    done
    return 1
}

capture_diagnostics() {
    mkdir -p ci-diagnostics
    {
        echo "== host =="
        date -u || true
        free -m || true
        df -h / || true
        nproc || true
        uptime || true
        echo "== guest =="
        adb shell getprop sys.boot_completed 2>&1 || true
        adb shell cat /proc/meminfo 2>&1 || true
        adb shell df -h /data 2>&1 || true
        adb shell service list 2>&1 | grep -E 'package|activity' || true
        adb logcat -d -b crash -v threadtime 2>&1 || true
    } >ci-diagnostics/instrumented-diagnostics.txt 2>&1
}

reports_ok() {
    python3 - <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

files = sorted(
    glob.glob("app/build/outputs/androidTest-results/**/TEST-*.xml", recursive=True)
)
if not files:
    print("::error::no instrumented test reports were produced")
    sys.exit(1)

flavors = set()
bad = False
for path in files:
    root = ET.parse(path).getroot()
    tests = int(root.get("tests", "0"))
    failures = int(root.get("failures", "0"))
    errors = int(root.get("errors", "0"))
    skipped = int(root.get("skipped", "0"))
    print(f"{path}: tests={tests} failures={failures} errors={errors} skipped={skipped}")
    if "flavors/" in path:
        flavors.add(path.split("flavors/", 1)[1].split("/", 1)[0])
    if failures or errors or skipped or tests == 0:
        bad = True
    for testcase in root.iter("testcase"):
        for failure in testcase.findall("failure"):
            text = (failure.text or "").lower()
            if any(
                marker in text
                for marker in (
                    "failed to attach",
                    "failed to install",
                    "testrunfailed",
                    "process crashed",
                )
            ):
                bad = True

if flavors != {"anon", "nero"}:
    print(f"::error::expected anon and nero reports, got {sorted(flavors)}")
    bad = True
sys.exit(1 if bad else 0)
PY
}

adb wait-for-device
if ! wait_for_core_services; then
    capture_diagnostics
    echo "::error::device lost system_server after boot"
    exit 1
fi
# The KVM-less software emulator is still settling right after boot. The first
# instrumentation attach can time out; give ART/package work time to finish.
sleep 30

set +e
./gradlew --no-daemon --continue --stacktrace -PanonDisableSplits=true \
    :app:connectedAnonDebugAndroidTest \
    :app:connectedNeroDebugAndroidTest
status=$?
set -e

if ! core_services_ok; then
    capture_diagnostics
    echo "::error::device lost package/activity services during instrumented tests"
    exit 1
fi

# Retry only a flavor whose report says "failed to attach". Installation or
# system_server failures are not retried; the XML and exit-code checks are final.
for flavor in anon nero; do
    report=$(find app/build/outputs/androidTest-results \
        -path "*flavors/${flavor}/*" -name 'TEST-*.xml' | head -n 1)
    if [ -n "$report" ] && {
        grep -q "failed to attach" "$report" ||
            grep -q 'tests="0"' "$report" ||
            grep -q "Failed to install" "$report"
    } && core_services_ok; then
        case "$flavor" in
            anon) task=:app:connectedAnonDebugAndroidTest ;;
            nero) task=:app:connectedNeroDebugAndroidTest ;;
        esac
        echo "Retrying ${flavor} after an instrumentation attach timeout"
        set +e
        ./gradlew --no-daemon --continue --stacktrace -PanonDisableSplits=true "$task"
        retry_status=$?
        set -e
        if [ "$retry_status" -eq 0 ]; then
            # The retried flavor passed; reports_ok is still the final authority for
            # the other flavor and for any non-attach failure.
            status=0
        else
            status=$retry_status
        fi
    fi
done

if ! reports_ok; then
    capture_diagnostics
    exit 1
fi

exit "$status"
