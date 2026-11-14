#!/usr/bin/env python3
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

"""CI-only coverage for discoverable feature translation catalogs."""

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-i18n.py"
SPEC = importlib.util.spec_from_file_location("i18n_contract", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
I18N = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(I18N)


class CatalogDiscoveryTest(unittest.TestCase):
    def test_feature_file_is_discovered_without_editing_checker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            primary = root / "strings.xml"
            feature = root / "strings_feature.xml"
            primary.write_text('<resources><string name="base">Base</string></resources>', encoding="utf-8")
            feature.write_text('<resources><string name="new_feature">Feature</string></resources>', encoding="utf-8")
            catalogs = I18N.string_catalogs(root)
            self.assertEqual(catalogs, [primary, feature])
            strings, _ = I18N.parse_catalog(catalogs)
            self.assertEqual(strings["new_feature"], "Feature")

    def test_missing_primary_catalog_cannot_silently_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(SystemExit, "missing resource file"):
                I18N.parse_catalog(I18N.string_catalogs(Path(directory)))
