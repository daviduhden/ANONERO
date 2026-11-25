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

"""Reject unresolved unqualified coroutine cancellation catches without compiling."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def missing_cancellation_import(source: str) -> bool:
    catches = re.search(r"\bcatch\s*\([^:]+:\s*CancellationException\s*\)", source)
    imported = re.search(
        r"(?m)^import\s+(?:kotlinx\.coroutines|java\.util\.concurrent)\.CancellationException\s*$",
        source,
    )
    return catches is not None and imported is None


def main() -> int:
    failures = [
        str(path.relative_to(ROOT))
        for path in (ROOT / "app/src/main/java").rglob("*.kt")
        if missing_cancellation_import(path.read_text(encoding="utf-8"))
    ]
    if failures:
        print(
            "Unqualified cancellation catches require an explicit import:\n" + "\n".join(failures)
        )
        return 1
    print("Kotlin cancellation catch imports: OK (static pattern check only)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
