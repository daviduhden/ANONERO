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
attach_only = runpy.run_path(str(ROOT / "scripts/check-test-reports.py"))["attach_only"]


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

    def test_nested_aggregate_and_sanitizer_in_output(self):
        case = "<testcase><failure>failed to attach</failure></testcase>"
        suite = f'<testsuite tests="1" failures="1">{case}</testsuite>'
        aggregate = f'<testsuites tests="1" failures="1">{suite}</testsuites>'
        self.assertTrue(attach_only([self.report("nested.xml", aggregate)]))
        for output in (
            "ERROR: AddressSanitizer: heap-use-after-free",
            "SUMMARY: ThreadSanitizer: data race",
            "wallet.cpp:12:3: runtime error: signed integer overflow",
        ):
            mixed = aggregate.replace(
                "</testsuite>", f"<system-err>{output}</system-err></testsuite>"
            )
            self.assertFalse(attach_only([self.report("mixed.xml", mixed)]))

    def test_final_verdict_rejects_missing_cases_and_contradictory_summaries(self):
        verify = runpy.run_path(str(ROOT / "scripts/check-test-reports.py"))["verify_results"]
        root = Path(self.temp.name)
        valid = '<testsuite tests="1"><testcase/></testsuite>'
        for flavor in ("anon", "nero"):
            directory = root / "flavors" / flavor
            directory.mkdir(parents=True)
            (directory / "test-result-exit-code.txt").write_text("0")
            (directory / "TEST-result.xml").write_text(valid)
        verify(root)
        for case in (
            "",
            "<testcase><error/></testcase>",
            "<testcase><skipped/></testcase>",
            "<testcase/><system-out>ERROR: LeakSanitizer: detected memory leaks</system-out>",
        ):
            (directory / "TEST-result.xml").write_text(valid.replace("<testcase/>", case))
            with self.assertRaises(ValueError):
                verify(root)

    def test_success_summary_cannot_mask_guest_sanitizer(self):
        check_logs = runpy.run_path(str(ROOT / "scripts/check-test-reports.py"))["check_guest_logs"]
        root = Path(self.temp.name)
        device = root / "flavors" / "anon" / "device"
        device.mkdir(parents=True)
        (device / "logcat-case.txt").write_text("WARNING: ThreadSanitizer: data race")
        with self.assertRaises(ValueError):
            check_logs(root, "anon")
        check_logs(root, "nero")

    def test_unit_result_requires_both_variant_reports(self):
        verify = runpy.run_path(str(ROOT / "scripts/check-test-reports.py"))["verify_unit_results"]
        root = Path(self.temp.name)
        for flavor in ("Anon", "Nero"):
            variant = root / f"test{flavor}DebugUnitTest"
            variant.mkdir()
            (variant / "TEST-case.xml").write_text('<testsuite tests="1"><testcase/></testsuite>')
            if flavor == "Anon":
                with self.assertRaises(ValueError):
                    verify(root)
        verify(root)

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
            ROOT / "scripts/check-test-reports.py",
            scripts / "check-test-reports.py",
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
  echo '<testsuite tests="1" failures="1"><testcase><failure>failed to attach</failure></testcase></testsuite>' \
    > "$reports/TEST-fixture.xml"
  exit 1
fi
echo 0 > "$reports/test-result-exit-code.txt"
echo '<testsuite tests="1" failures="0"><testcase/></testsuite>' > "$reports/TEST-fixture.xml"
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
