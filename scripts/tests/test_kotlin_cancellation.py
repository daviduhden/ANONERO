#!/usr/bin/env python3
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

"""CI-only regressions for cancellation import diagnosis."""

import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-kotlin-cancellation.py"
SPEC = importlib.util.spec_from_file_location("cancellation_contract", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)


class CancellationImportTest(unittest.TestCase):
    def test_unqualified_catch_needs_import(self):
        self.assertTrue(
            POLICY.missing_cancellation_import("catch (cancelled: CancellationException) {}")
        )

    def test_canonical_import_and_qualified_catch_are_supported(self):
        self.assertFalse(
            POLICY.missing_cancellation_import(
                "import kotlinx.coroutines.CancellationException\ncatch (e: CancellationException) {}"
            )
        )
        self.assertFalse(
            POLICY.missing_cancellation_import(
                "catch (e: kotlinx.coroutines.CancellationException) {}"
            )
        )

    def test_unrelated_exception_does_not_require_import(self):
        self.assertFalse(POLICY.missing_cancellation_import("catch (e: Exception) {}"))


if __name__ == "__main__":
    unittest.main()
