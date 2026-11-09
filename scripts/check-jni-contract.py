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

"""Static JNI symbol parity check for the project-owned adapter.

This intentionally does not compile or load the library. It verifies that every
Kotlin ``external fun``/``external get`` has a matching Java_io_anonero_* export
and that only the known legacy exports are unmatched. Signatures are not checked;
the JNI/FFI document explains why linkage and signatures still require CI.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "app" / "src" / "main" / "java"
CPP = ROOT / "app" / "src" / "main" / "cpp" / "anonero.cpp"

# Exports that are intentionally retained but have no Kotlin declaration.
KNOWN_ORPHANS = {
    "Java_io_anonero_model_Wallet_setSeedLanguage",
    "Java_io_anonero_model_Wallet_getStatusJ",
    "Java_io_anonero_model_Wallet_isSynchronizedJ",
}


def kotlin_external_names() -> set[str]:
    names: set[str] = set()
    for path in KOTLIN_ROOT.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        names.update(re.findall(r"\bexternal\s+fun\s+([A-Za-z_]\w*)", text))
        # Kotlin generates getX()/setX() for a property; the adapter exports getX.
        for marker in re.finditer(r"\bexternal\s+get\b", text):
            prefix = text[: marker.start()]
            properties = list(
                re.finditer(r"\bval\s+([A-Za-z_]\w*)\s*:", prefix)
            )
            if properties:
                prop = properties[-1].group(1)
                names.add("get" + prop[0].upper() + prop[1:])
    return names


def native_exports() -> set[str]:
    text = CPP.read_text(encoding="utf-8")
    return set(re.findall(r"Java_io_anonero_[A-Za-z0-9_]+", text))


def has_export(method: str, exports: set[str]) -> bool:
    escaped = method.replace("_", "_1")
    return any(
        export.endswith("_" + method) or export.endswith("_" + escaped)
        for export in exports
    )


def main() -> int:
    external = kotlin_external_names()
    exports = native_exports()
    missing = sorted(name for name in external if not has_export(name, exports))
    orphans = sorted(export for export in exports if not has_export_any(export, external))
    unexpected = [orphan for orphan in orphans if orphan not in KNOWN_ORPHANS]

    print(f"Kotlin external names: {len(external)}")
    print(f"C++ JNI exports:       {len(exports)}")
    print(f"Known orphan exports:  {len(KNOWN_ORPHANS)}")
    if missing:
        print("\nMissing native exports for Kotlin external names:")
        for name in missing:
            print(f"  - {name}")
    if orphans:
        print("\nNative exports without a Kotlin declaration:")
        for name in orphans:
            marker = "known" if name in KNOWN_ORPHANS else "UNEXPECTED"
            print(f"  - {name} ({marker})")
    if missing or unexpected:
        return 1
    print("JNI symbol parity: OK (signatures still require a real build/link check)")
    return 0


def has_export_any(export: str, external: set[str]) -> bool:
    return any(
        export.endswith("_" + name) or export.endswith("_" + name.replace("_", "_1"))
        for name in external
    )


if __name__ == "__main__":
    sys.exit(main())
