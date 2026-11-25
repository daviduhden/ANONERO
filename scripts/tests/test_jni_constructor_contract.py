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

"""CI-only mutation coverage for the static model constructor contract guard."""

import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("jni_contract", ROOT / "scripts/check-jni-contract.py")
assert SPEC is not None and SPEC.loader is not None
JNI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(JNI)


class ConstructorContractTest(unittest.TestCase):
    def setUp(self):
        # Apply source mutations to isolated copies so each case starts with the
        # current Kotlin/JNI pair.
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        self.models = root / "io/anonero/model"
        self.models.mkdir(parents=True)
        for name in JNI.MODEL_CONSTRUCTORS:
            source = JNI.KOTLIN_ROOT / "io/anonero/model" / f"{name}.kt"
            (self.models / source.name).write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
        self.cpp = root / "adapter.cpp"
        self.cpp.write_text(JNI.CPP.read_text(encoding="utf-8"), encoding="utf-8")
        self.kotlin_patch = patch.object(JNI, "KOTLIN_ROOT", root)
        self.native_patch = patch.object(JNI, "CPP", self.cpp)
        self.kotlin_patch.start()
        self.native_patch.start()
        self.addCleanup(self.kotlin_patch.stop)
        self.addCleanup(self.native_patch.stop)

    def mutate(self, path, before, after):
        source = path.read_text(encoding="utf-8")
        self.assertIn(before, source)
        path.write_text(source.replace(before, after, 1), encoding="utf-8")

    def test_current_contracts_match(self):
        self.assertEqual(JNI.model_constructor_errors(), [])

    def test_same_descriptor_parameter_reorder_is_rejected(self):
        self.mutate(
            self.models / "CoinsInfo.kt",
            "val hash: String,\n    val pub_key: String,",
            "val pub_key: String,\n    val hash: String,",
        )
        self.assertTrue(any("order changed" in error for error in JNI.model_constructor_errors()))

    def test_primitive_boxing_requires_review(self):
        self.mutate(self.models / "CoinsInfo.kt", "val amount: Long,", "val amount: Long?,")
        self.assertTrue(any("nullable primitive" in error for error in JNI.model_constructor_errors()))

    def test_native_descriptor_drift_is_rejected(self):
        self.mutate(self.cpp, '"(JLjava/lang/String;)V"', '"(ILjava/lang/String;)V"')
        self.assertTrue(any("expected JNI constructor" in error for error in JNI.model_constructor_errors()))

    def test_unsupported_constructor_type_is_rejected(self):
        self.mutate(self.models / "CoinsInfo.kt", "val amount: Long,", "val amount: Double,")
        self.assertTrue(any("unsupported" in error for error in JNI.model_constructor_errors()))


if __name__ == "__main__":
    unittest.main()
