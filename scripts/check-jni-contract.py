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
Kotlin ``external fun``/``external get`` has the exact Java_io_anonero_<package>_<Class>_<method>
export and that only the known legacy exports are unmatched. Signatures are not checked;
the JNI/FFI document explains why linkage and signatures still require CI.

The class name is the first top-level class/object/interface in the Kotlin file. All
current external declarations follow that layout; a future nested-class declaration fails
closed as a missing export instead of being matched by suffix.
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


def escape_jni(name: str) -> str:
    return name.replace("_", "_1")


def kotlin_external_symbols() -> dict[str, str]:
    """Map each expected JNI symbol to a readable Kotlin declaration."""
    symbols: dict[str, str] = {}
    for path in KOTLIN_ROOT.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        package = re.search(r"^\s*package\s+([\w.]+)", text, re.MULTILINE)
        klass = re.search(
            r"^\s*(?:open\s+|abstract\s+|sealed\s+|data\s+|enum\s+|annotation\s+)*"
            r"(?:class|object|interface)\s+([A-Za-z_]\w*)",
            text,
            re.MULTILINE,
        )
        if package is None or klass is None:
            continue
        package_path = "_".join(
            escape_jni(part) for part in package.group(1).split(".")
        )
        prefix = "Java_" + package_path + "_" + escape_jni(klass.group(1)) + "_"

        methods = [m.group(1) for m in re.finditer(r"\bexternal\s+fun\s+([A-Za-z_]\w*)", text)]
        # Kotlin generates getX() for an external property; the adapter exports getX.
        for marker in re.finditer(r"\bexternal\s+get\b", text):
            properties = list(re.finditer(r"\bval\s+([A-Za-z_]\w*)\s*:", text[: marker.start()]))
            if properties:
                prop = properties[-1].group(1)
                methods.append("get" + prop[0].upper() + prop[1:])

        for method in methods:
            symbol = prefix + escape_jni(method)
            symbols[symbol] = f"{path.relative_to(ROOT)}:{klass.group(1)}.{method}"
    return symbols


def native_exports() -> set[str]:
    text = CPP.read_text(encoding="utf-8")
    return set(re.findall(r"Java_io_anonero_[A-Za-z0-9_]+", text))


def main() -> int:
    external = kotlin_external_symbols()
    exports = native_exports()
    missing = sorted(set(external) - exports)
    orphans = sorted(exports - set(external))
    unexpected = [orphan for orphan in orphans if orphan not in KNOWN_ORPHANS]

    print(f"Kotlin external symbols: {len(external)}")
    print(f"C++ JNI exports:         {len(exports)}")
    print(f"Known orphan exports:    {len(KNOWN_ORPHANS)}")
    if missing:
        print("\nMissing native exports for Kotlin external symbols:")
        for symbol in missing:
            print(f"  - {symbol} ({external[symbol]})")
    if orphans:
        print("\nNative exports without a Kotlin declaration:")
        for symbol in orphans:
            marker = "known" if symbol in KNOWN_ORPHANS else "UNEXPECTED"
            print(f"  - {symbol} ({marker})")
    if missing or unexpected:
        return 1
    print("JNI symbol parity: OK (signatures still require a real build/link check)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
