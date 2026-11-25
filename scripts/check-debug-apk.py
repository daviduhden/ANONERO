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

"""Verify the two project JNI libraries packaged in an unsplit Debug APK."""

import struct
import sys
from pathlib import Path
from zipfile import ZipFile

# ELF class and e_machine for the production Android ABIs.
EXPECTED = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}


def verify(apk: Path) -> None:
    with ZipFile(apk) as archive:
        members = [
            entry for entry in archive.infolist() if entry.filename.endswith("/libanonero.so")
        ]
        if sorted(entry.filename for entry in members) != sorted(
            f"lib/{abi}/libanonero.so" for abi in EXPECTED
        ):
            raise ValueError("Expected one JNI library for each supported ABI")
        for entry in members:
            abi = entry.filename.split("/")[1]
            with archive.open(entry) as library:
                header = library.read(20)
            if len(header) != 20 or header[:4] != b"\x7fELF" or header[5] != 1:
                raise ValueError(f"Invalid little-endian ELF header for {abi}")
            file_type, machine = struct.unpack_from("<HH", header, 16)
            if file_type != 3 or (header[4], machine) != EXPECTED[abi]:
                raise ValueError(f"Unexpected ELF type or architecture for {abi}")


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
