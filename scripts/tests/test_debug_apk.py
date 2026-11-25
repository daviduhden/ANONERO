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

"""CI-only regression checks for Debug JNI packaging."""

import importlib.util
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

SPEC = importlib.util.spec_from_file_location(
    "check_debug_apk", Path(__file__).resolve().parents[1] / "check-debug-apk.py"
)
assert SPEC is not None and SPEC.loader is not None
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class DebugApkTest(unittest.TestCase):
    def test_missing_abi_and_swapped_elf_are_rejected(self):
        expected = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "debug.apk"
            for layout in ({"arm64-v8a": (2, 183)}, {**expected, "arm64-v8a": (1, 40)}, expected):
                with ZipFile(apk, "w") as archive:
                    for abi, (elf_class, machine) in layout.items():
                        header = bytearray(20)
                        header[:6] = b"\x7fELF" + bytes([elf_class, 1])
                        header[16:20] = bytes([3, 0, machine, 0])
                        archive.writestr(f"lib/{abi}/libanonero.so", header)
                if layout == expected:
                    CHECK.verify(apk)
                else:
                    with self.assertRaises(ValueError):
                        CHECK.verify(apk)
