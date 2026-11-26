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

"""Validate JVM and instrumented JUnit evidence and classify Android attach failures."""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Attach text can accompany a more serious failure in the same report.
NON_RETRYABLE = (
    "failed to install",
    "process crashed",
    "broken pipe",
    "can't find service",
    "failure calling service package",
)


SANITIZER = re.compile(
    r"(?:ERROR|SUMMARY|WARNING|FATAL):\s*(?:Address|Leak|Thread|Memory|UndefinedBehavior|HWAddress)Sanitizer"
    r"|(?:Address|HWAddress|UndefinedBehavior|Memory)Sanitizer:DEADLYSIGNAL"
    r"|:\d+(?::\d+)?: runtime error:",
    re.IGNORECASE,
)


def validated_report(path: Path) -> tuple[int, list[ET.Element]]:
    root = ET.parse(path).getroot()
    if root.tag not in {"testsuite", "testsuites"}:
        raise ValueError("Unexpected JUnit root")
    for node in root.iter():
        if node.tag in {"system-out", "system-err", "failure", "error"}:
            diagnostic = " ".join([*node.attrib.values(), *node.itertext()])
            if SANITIZER.search(diagnostic):
                raise ValueError("Sanitizer diagnostic in JUnit report")
    for suite in root.iter():
        if suite.tag not in {"testsuite", "testsuites"}:
            continue
        for counter, element in (
            ("tests", "testcase"),
            ("failures", "failure"),
            ("errors", "error"),
            ("skipped", "skipped"),
        ):
            count = sum(1 for _ in suite.iter(element))
            if int(suite.get(counter, "0")) != count or (element == "skipped" and count):
                raise ValueError("Inconsistent or skipped JUnit results")
    return int(root.get("tests", "0")), [e for e in root.iter() if e.tag in {"error", "failure"}]


def attach_only(reports: list[Path]) -> bool:
    """Reject missing, ambiguous, or mixed failures."""
    seen_attach = False
    try:
        for report in reports:
            tests, issues = validated_report(report)
            if not issues and tests == 0:
                return False
            for issue in issues:
                message = f"{issue.get('message', '')} {''.join(issue.itertext())}".casefold()
                if "failed to attach" not in message or any(
                    marker in message for marker in NON_RETRYABLE
                ):
                    return False
                seen_attach = True
    except (OSError, ValueError, ET.ParseError):
        return False
    return seen_attach


def check_guest_logs(root: Path, flavor: str) -> None:
    for log in root.glob(f"**/flavors/{flavor}/**/logcat-*.txt"):
        with log.open(encoding="utf-8", errors="replace") as lines:
            if any(SANITIZER.search(line) for line in lines):
                raise ValueError(f"Sanitizer diagnostic in {flavor} device logs")


def require_passing_suites(reports: list[Path], flavor: str) -> None:
    if not reports:
        raise ValueError(f"Missing JUnit XML for {flavor}")
    total = 0
    for report in reports:
        tests, issues = validated_report(report)
        if tests <= 0 or issues:
            raise ValueError(f"Unsuccessful JUnit suite: {report.name}")
        total += tests
    print(f"{flavor}: {total} passing cases in {len(reports)} JUnit reports")


def verify_unit_results(root: Path) -> None:
    for flavor in ("anon", "nero"):
        directory = root / f"test{flavor.capitalize()}DebugUnitTest"
        require_passing_suites(sorted(directory.glob("TEST-*.xml")), flavor)


def verify_results(root: Path) -> None:
    reports = set(root.rglob("TEST-*.xml"))
    for flavor in ("anon", "nero"):
        check_guest_logs(root, flavor)
        directories = sorted(root.glob(f"**/flavors/{flavor}"))
        if not directories:
            raise ValueError(f"Missing instrumented results for {flavor}")
        for directory in directories:
            selected = sorted(directory.rglob("TEST-*.xml"))
            codes = sorted(directory.rglob("test-result-exit-code.txt"))
            if not codes or any(code.read_text().strip() != "0" for code in codes):
                raise ValueError(f"Missing or unsuccessful device exit for {flavor}")
            require_passing_suites(selected, flavor)
            reports.difference_update(selected)
    if reports:
        raise ValueError("Unexpected instrumentation report outside the two flavor directories")


if __name__ == "__main__":
    _, directory, mode = sys.argv
    root = Path(directory)
    if mode == "--verify":
        verify_results(root)
    elif mode == "--unit":
        verify_unit_results(root)
    elif mode in {"anon", "nero"}:
        check_guest_logs(root, mode)
        sys.exit(0 if attach_only(sorted(root.glob(f"**/flavors/{mode}/**/TEST-*.xml"))) else 1)
    else:
        sys.exit(1)
