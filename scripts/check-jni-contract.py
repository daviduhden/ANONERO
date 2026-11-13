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

The class path is resolved from brace nesting, so nested classes use the JNI
``Outer_00024Inner`` form and companion-object members map to the outer class. A top-level
external declaration, a missing package, or a duplicate symbol fails closed.
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


def strip_non_code(text: str) -> str:
    """Blank comments and string/char literals while preserving offsets."""
    out = list(text)
    i = 0
    while i < len(text):
        if text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                out[i] = " "
                i += 1
        elif text.startswith("/*", i):
            out[i] = out[i + 1] = " "
            i += 2
            while i < len(text):
                if text.startswith("*/", i):
                    out[i] = out[i + 1] = " "
                    i += 2
                    break
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        elif text.startswith('"""', i):
            out[i] = out[i + 1] = out[i + 2] = " "
            i += 3
            while i < len(text):
                if text.startswith('"""', i):
                    out[i] = out[i + 1] = out[i + 2] = " "
                    i += 3
                    break
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        elif text[i] in {'"', "'"}:
            quote = text[i]
            out[i] = " "
            i += 1
            while i < len(text):
                if text[i] == "\\":
                    out[i] = " "
                    if i + 1 < len(text) and text[i + 1] != "\n":
                        out[i + 1] = " "
                    i += 2
                elif text[i] == quote:
                    out[i] = " "
                    i += 1
                    break
                else:
                    if text[i] != "\n":
                        out[i] = " "
                    i += 1
        else:
            i += 1
    return "".join(out)


def kotlin_external_symbols() -> tuple[dict[str, str], list[str]]:
    """Map each expected JNI symbol to a readable Kotlin declaration."""
    symbols: dict[str, str] = {}
    errors: list[str] = []

    for path in KOTLIN_ROOT.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        code = strip_non_code(text)
        externals = list(
            re.finditer(r"\bexternal\s+(?:fun\s+([A-Za-z_]\w*)|get\b)", code)
        )
        if not externals:
            continue

        package = re.search(r"^\s*package\s+([\w.]+)", code, re.MULTILINE)
        if package is None:
            errors.append(f"{path.relative_to(ROOT)}: external declaration without package")
            continue
        package_path = "_".join(
            escape_jni(part) for part in package.group(1).split(".")
        )

        events: list[tuple[int, str, re.Match[str]]] = []
        for declaration in re.finditer(
            r"\b(?:class|object|interface)\s+([A-Za-z_]\w*)", code
        ):
            events.append((declaration.start(), "class", declaration))
        for companion in re.finditer(r"\bcompanion\s+object\s*\{", code):
            events.append((companion.start(), "companion", companion))
        for external in externals:
            events.append((external.start(), "external", external))
        events.sort(key=lambda event: event[0])

        stack: list[tuple[int, list[str]]] = []
        depth = 0
        cursor = 0
        for position, kind, match in events:
            depth += code[cursor:position].count("{") - code[cursor:position].count("}")
            cursor = position

            if kind in {"class", "companion"}:
                line_start = code.rfind("\n", 0, position) + 1
                qualifier = code[line_start:position]
                while stack and stack[-1][0] >= depth:
                    stack.pop()
                parent = stack[-1][1] if stack else []
                if kind == "companion" or re.search(r"\bcompanion\s+$", qualifier):
                    # @JvmStatic companion members use the outer class symbol.
                    path_segments = parent
                else:
                    path_segments = parent + [match.group(1)]
                stack.append((depth, path_segments))
                continue

            while stack and stack[-1][0] >= depth:
                stack.pop()
            if not stack:
                errors.append(
                    f"{path.relative_to(ROOT)}: external declaration outside a class"
                )
                continue

            class_path = "_00024".join(escape_jni(part) for part in stack[-1][1])
            prefix = f"Java_{package_path}_{class_path}_"
            if match.group(1) is None:
                properties = list(
                    re.finditer(r"\bval\s+([A-Za-z_]\w*)\s*:", text[: match.start()])
                )
                if not properties:
                    errors.append(
                        f"{path.relative_to(ROOT)}: external get without a val"
                    )
                    continue
                prop = properties[-1].group(1)
                method = "get" + prop[0].upper() + prop[1:]
            else:
                method = match.group(1)

            symbol = prefix + escape_jni(method)
            location = f"{path.relative_to(ROOT)}:{'.'.join(stack[-1][1])}.{method}"
            if symbol in symbols:
                errors.append(f"duplicate JNI symbol or overload: {symbol}")
                continue
            symbols[symbol] = location

    return symbols, errors


def native_exports() -> set[str]:
    text = CPP.read_text(encoding="utf-8")
    return set(
        re.findall(r"JNICALL\s+(Java_io_anonero_[A-Za-z0-9_]+)", text)
    )


def main() -> int:
    external, errors = kotlin_external_symbols()
    exports = native_exports()
    missing = sorted(set(external) - exports)
    orphans = sorted(exports - set(external))
    unexpected = [orphan for orphan in orphans if orphan not in KNOWN_ORPHANS]
    known = [orphan for orphan in orphans if orphan in KNOWN_ORPHANS]

    print(f"Kotlin external symbols: {len(external)}")
    print(f"C++ JNI exports:         {len(exports)}")
    print(f"Known orphan exports:    {len(known)}")
    if errors:
        print("\nInvalid JNI declarations:")
        for error in errors:
            print(f"  - {error}")
    if missing:
        print("\nMissing native exports for Kotlin external symbols:")
        for symbol in missing:
            print(f"  - {symbol} ({external[symbol]})")
    if orphans:
        print("\nNative exports without a Kotlin declaration:")
        for symbol in orphans:
            marker = "known" if symbol in KNOWN_ORPHANS else "UNEXPECTED"
            print(f"  - {symbol} ({marker})")
    if errors or missing or unexpected:
        return 1
    print("JNI symbol parity: OK (signatures still require a real build/link check)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
