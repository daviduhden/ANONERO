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

"""CI-only regressions for the manual release-suite source synchronizer."""

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "sync_release_test_jobs", ROOT / "scripts/sync-release-test-jobs.py"
)
assert SPEC is not None and SPEC.loader is not None
SYNC = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SYNC)


class ReleaseSuiteSynchronizationTest(unittest.TestCase):
    def setUp(self):
        # Start each mutation from the workflows that CI will actually load.
        self.source = (ROOT / ".github/workflows/test.yml").read_text(encoding="utf-8")
        self.release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

    def test_callable_entry_point_is_rejected(self):
        for source, release in (
            (self.source.replace("  workflow_dispatch:", "  workflow_call:"), self.release),
            (self.source, self.release.replace("  workflow_dispatch:", "  workflow_call:")),
        ):
            with self.assertRaisesRegex(ValueError, "only workflow_dispatch"):
                SYNC.synchronize(source, release)

    def test_environment_drift_is_rejected(self):
        release = self.release.replace("ANDROID_MIN_SDK: '29'", "ANDROID_MIN_SDK: '30'")
        with self.assertRaisesRegex(ValueError, "environment values differ"):
            SYNC.synchronize(self.source, release)

    def test_missing_required_job_is_rejected(self):
        source = self.source.replace("\n  native:\n", "\n  omitted-native:\n")
        with self.assertRaisesRegex(ValueError, "missing, duplicate or reserved"):
            SYNC.synchronize(source, self.release)

    def test_unsupported_cache_policy_is_rejected(self):
        source = self.source.replace("cache-jdk: false", "cache-jdk: true")
        with self.assertRaisesRegex(ValueError, "cache/ref format"):
            SYNC.synchronize(source, self.release)

    def test_lint_waits_for_unit_cache_and_keeps_independent_verdict(self):
        lint = self.source.split("\n  lint:\n", 1)[1].split("    runs-on:", 1)[0]
        self.assertIn("    needs: [authorize, unit]\n", lint)
        self.assertIn("    if: ${{ !cancelled() && needs.authorize.result == 'success' }}\n", lint)

    def test_release_excludes_gradle_dependency_caches(self):
        block = SYNC.generated_block(self.source)
        self.assertNotIn("cache: gradle", block)
        self.assertNotIn("cache-path:", block)
        self.assertEqual(
            block.count("cache-disabled: true"), block.count("uses: gradle/actions/setup-gradle@")
        )

    def test_unbound_checkout_reference_is_rejected(self):
        source = self.source.replace(SYNC.SOURCE_REF, "${{ github.ref }}")
        with self.assertRaisesRegex(ValueError, "cache/ref format"):
            SYNC.synchronize(source, self.release)

    def test_release_job_drift_is_restored_from_authoritative_suite(self):
        release = self.release.replace("    name: JNI symbol contract", "    name: Drifted JNI job")
        self.assertNotEqual(release, self.release)
        self.assertEqual(SYNC.synchronize(self.source, release), self.release)

    def test_removed_generated_boundary_is_rejected(self):
        release = self.release.replace(SYNC.END, "")
        with self.assertRaisesRegex(ValueError, "exactly one generated block"):
            SYNC.synchronize(self.source, release)

    def test_signing_rejects_missing_instrumented_dependency(self):
        release = self.release.replace("[authorize, build, instrumented]", "[authorize, build]", 1)
        with self.assertRaisesRegex(ValueError, "sign-anon.*needs"):
            SYNC.synchronize(self.source, release)

    def test_signing_rejects_conditions_that_bypass_instrumentation(self):
        release = self.release.replace(
            "inputs.run_instrumented == true && needs.instrumented.result == 'success'",
            "always()",
        )
        with self.assertRaisesRegex(ValueError, "sign-anon.*if"):
            SYNC.synchronize(self.source, release)

    def test_signing_rejects_a_shared_production_environment(self):
        release = self.release.replace(
            "environment: android-nero-release", "environment: android-anon-release"
        )
        with self.assertRaisesRegex(ValueError, "sign-nero.*environment"):
            SYNC.synchronize(self.source, release)
