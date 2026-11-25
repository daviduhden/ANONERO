#
# Copyright (c) 2026, David Uhden Collado
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

import os
import runpy
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
attach_only = runpy.run_path(str(ROOT / "scripts/check-instrumented-retry.py"))["attach_only"]


class InstrumentedRetryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)

    def report(self, name: str, xml: str) -> Path:
        path = Path(self.temp.name) / name
        path.write_text(xml, encoding="utf-8")
        return path

    def test_explicit_attach_failure_is_retryable(self):
        attach = self.report(
            "attach.xml",
            '<testsuite tests="1" failures="1"><testcase>'
            '<failure message="Instrumentation run failed due to failed to attach"/>'
            "</testcase></testsuite>",
        )
        self.assertTrue(attach_only([attach]))

    def test_an_attach_failure_cannot_hide_another_failure(self):
        attach = self.report(
            "attach.xml",
            '<testsuite tests="1" errors="1"><testcase>'
            "<error>failed to attach</error></testcase></testsuite>",
        )
        crash = self.report(
            "crash.xml",
            '<testsuite tests="1" failures="1"><testcase>'
            "<failure>process crashed after failed to attach</failure></testcase></testsuite>",
        )
        self.assertFalse(attach_only([attach, crash]))
        skipped = self.report("skipped.xml", '<testsuite tests="1" skipped="1"/>')
        self.assertFalse(attach_only([attach, skipped]))

    def test_missing_empty_or_malformed_reports_fail_closed(self):
        self.assertFalse(attach_only([]))
        self.assertFalse(attach_only([self.report("empty.xml", '<testsuite tests="0"/>')]))
        self.assertFalse(attach_only([self.report("broken.xml", "<testsuite")]))

    def test_successful_retry_preserves_other_flavor_gradle_failure(self):
        runner = Path(self.temp.name)
        bin_path = runner / "bin"
        bin_path.mkdir()
        scripts = runner / "scripts"
        scripts.mkdir()
        shutil.copyfile(
            ROOT / "scripts/check-instrumented-retry.py",
            scripts / "check-instrumented-retry.py",
        )
        executables = {
            bin_path / "adb": """#!/bin/sh
case "$*" in
  "shell getprop sys.boot_completed") echo 1 ;;
  "shell service check package") echo "Service package: found" ;;
  "shell service check activity") echo "Service activity: found" ;;
esac
""",
            bin_path / "sleep": "#!/bin/sh\nexit 0\n",
            runner / "gradlew": """#!/bin/sh
set -eu
case "$*" in
  *connectedAnonDebugAndroidTest) flavor=anon ;;
  *connectedNeroDebugAndroidTest) flavor=nero ;;
  *) exit 99 ;;
esac
echo "$flavor" >> invocations
reports="app/build/outputs/androidTest-results/connected/flavors/$flavor"
mkdir -p "$reports"
if [ "$flavor" = nero ] && [ ! -f nero-retried ]; then
  touch nero-retried
  echo '<testsuite tests="1" failures="1"><failure>failed to attach</failure></testsuite>' \
    > "$reports/TEST-fixture.xml"
  exit 1
fi
echo '<testsuite tests="1" failures="0"/>' > "$reports/TEST-fixture.xml"
if [ "$flavor" = anon ]; then
  exit 2
fi
""",
        }
        for path, contents in executables.items():
            path.write_text(contents, encoding="utf-8")
            path.chmod(0o700)

        result = subprocess.run(
            ["bash", str(ROOT / ".github/scripts/run-instrumented.sh")],
            cwd=runner,
            env={**os.environ, "PATH": f"{bin_path}{os.pathsep}{os.environ['PATH']}"},
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        )
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(
            (runner / "invocations").read_text().splitlines(), ["anon", "nero", "nero"]
        )
        self.assertIn("Connected Gradle task for anon exited with 2", result.stdout)
