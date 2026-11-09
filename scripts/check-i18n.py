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

"""Static localization checks for ANONERO.

This script never executes project code. It validates the Android resource
catalogs and cross-references Kotlin resource usage:

* every base string/plural has an entry in each supported locale;
* no locale adds keys that the base does not define;
* no duplicated keys inside one resource configuration;
* format placeholders (%1$s, %2$02d, ...) match the base string;
* each plural defines the quantities its language requires;
* every R.string/R.plurals reference resolves to a base resource;
* XML is well formed and UTF-8;
* a conservative scan for hardcoded user-facing Compose/notification text.

Run from the repository root: python3 scripts/check-i18n.py
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

BASE_FILES = [RES / "values/strings.xml", RES / "values/strings_i18n.xml"]
LOCALES = {
    "es-ES": [RES / "values-es-rES/strings.xml", RES / "values-es-rES/strings_i18n.xml"],
    "ru-RU": [RES / "values-ru-rRU/strings.xml"],
    "zh-Hans": [RES / "values-b+zh+Hans/strings.xml"],
}
REQUIRED_QUANTITIES = {
    "es-ES": {"one", "other"},
    "ru-RU": {"one", "few", "many", "other"},
    "zh-Hans": {"other"},
}

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
KOTLIN_UI_PATTERNS = [
    re.compile(r'\bText\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
    re.compile(r'contentDescription\s*=\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
    re.compile(r'toastState\.show\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
    re.compile(r'setContentTitle\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
    re.compile(r'setContentText\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
]
# Technical literals that are intentionally not translated. Keep this list
# narrow: a value added here is a reviewed exception, not a generic escape.
HARDCODED_ALLOWLIST = {
    "",  # layout placeholder
    "UTF-8",
    "test",
    "Test",
    "lws.example.onion",  # LWS host format example, not UI prose
}


def fail(message: str) -> None:
    print(f"::error::{message}")


def parse_catalog(paths: list[Path]) -> tuple[dict[str, str], dict[str, list[str]]]:
    strings: dict[str, str] = {}
    plurals: dict[str, list[str]] = {}
    for path in paths:
        if not path.is_file():
            raise SystemExit(f"missing resource file: {path}")
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            raise SystemExit(f"invalid XML in {path}: {exc}") from exc
        for element in root:
            name = element.get("name")
            if not name:
                continue
            if element.tag == "string":
                if name in strings:
                    raise SystemExit(f"duplicate string '{name}' in {path}")
                strings[name] = "".join(element.itertext())
            elif element.tag == "plurals":
                if name in plurals:
                    raise SystemExit(f"duplicate plurals '{name}' in {path}")
                quantities = [
                    item.get("quantity", "") for item in element.findall("item")
                ]
                plurals[name] = quantities
                for item in element.findall("item"):
                    strings[f"{name}[{item.get('quantity')}]"] = "".join(
                        item.itertext()
                    )
    return strings, plurals


def placeholders(text: str) -> set[str]:
    return set(PLACEHOLDER.findall(text))


def android_escape_errors(path: Path) -> list[str]:
    """AAPT rejects unescaped apostrophes and unknown backslash escapes in string text."""
    errors: list[str] = []
    text = path.read_text(encoding="utf-8")
    element = re.compile(r"<(?:string|item)\b[^>]*>(.*?)</(?:string|item)>", re.S)
    for match in element.finditer(text):
        body = match.group(1)
        line = text.count("\n", 0, match.start(1)) + 1
        in_quotes = False
        i = 0
        while i < len(body):
            char = body[i]
            if char == "\\":
                nxt = body[i + 1:i + 2]
                if nxt in "nt'\"\\@?":
                    i += 2
                    continue
                if nxt == "u" and re.fullmatch(r"[0-9A-Fa-f]{4}", body[i + 2:i + 6] or ""):
                    i += 6
                    continue
                errors.append(f"{path.relative_to(ROOT)}:{line}: invalid string escape")
                i += 1
                continue
            if char == '"':
                in_quotes = not in_quotes
            elif char == "'" and not in_quotes:
                errors.append(f"{path.relative_to(ROOT)}:{line}: unescaped apostrophe")
            i += 1
    return errors


def main() -> int:
    errors = 0
    base_strings, base_plurals = parse_catalog(BASE_FILES)
    base_keys = set(base_strings)
    base_names = {
        key for key in base_strings if not key.endswith("]") and "[" not in key
    }

    for locale, files in LOCALES.items():
        strings, plurals = parse_catalog(files)
        names = {key for key in strings if "[" not in key}
        missing = base_names - names
        extra = names - base_names
        if missing:
            fail(f"{locale}: missing {len(missing)} base strings: {sorted(missing)}")
            errors += 1
        if extra:
            fail(f"{locale}: keys not present in base: {sorted(extra)}")
            errors += 1
        for name in base_plurals:
            if name not in plurals:
                fail(f"{locale}: missing plural '{name}'")
                errors += 1
        for name, quantities in plurals.items():
            missing_quantities = REQUIRED_QUANTITIES[locale] - set(quantities)
            if missing_quantities:
                fail(
                    f"{locale}: plural '{name}' is missing quantities "
                    f"{sorted(missing_quantities)}"
                )
                errors += 1
        for name in sorted(base_keys & set(strings)):
            if placeholders(base_strings[name]) != placeholders(strings[name]):
                fail(
                    f"{locale}: placeholder mismatch in '{name}': "
                    f"{sorted(placeholders(base_strings[name]))} != "
                    f"{sorted(placeholders(strings[name]))}"
                )
                errors += 1

    declared = base_names | set(base_plurals)
    reference = re.compile(r"\bR\.(string|plurals)\.([A-Za-z0-9_]+)")
    kotlin_files = list((ROOT / "app/src/main/java").rglob("*.kt"))
    for path in kotlin_files:
        text = path.read_text(encoding="utf-8")
        for kind, name in reference.findall(text):
            if name not in declared:
                fail(f"{path.relative_to(ROOT)}: unresolved R.{kind}.{name}")
                errors += 1

    for path in kotlin_files:
        for line_number, line in enumerate(
            path.read_text(encoding="utf-8").splitlines(), start=1
        ):
            for pattern in KOTLIN_UI_PATTERNS:
                match = pattern.search(line)
                if not match:
                    continue
                value = match.group(1)
                if value in HARDCODED_ALLOWLIST:
                    continue
                # Dynamic interpolation is a valid use of string resources and
                # technical formatting; only plain literals are flagged.
                if "$" in value:
                    continue
                if not any(character.isalpha() for character in value):
                    continue
                fail(
                    f"{path.relative_to(ROOT)}:{line_number}: hardcoded UI text "
                    f"'{value}'"
                )
                errors += 1

    for path in sorted(RES.glob("values*/*.xml")):
        for message in android_escape_errors(path):
            fail(message)
            errors += 1

    if errors:
        print(f"i18n check failed with {errors} problem(s)")
        return 1
    print(
        "i18n check passed: "
        f"{len(base_names)} strings, {len(base_plurals)} plurals, "
        f"{len(LOCALES)} locales"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
