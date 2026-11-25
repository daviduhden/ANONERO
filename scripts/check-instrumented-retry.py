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

"""Recognize the explicit attach failure eligible for one instrumentation retry."""

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


def attach_only(reports: list[Path]) -> bool:
    """Reject missing, ambiguous, or mixed failures."""
    seen_attach = False
    try:
        for report in reports:
            suite = ET.parse(report).getroot()
            issues = [element for element in suite.iter() if element.tag in {"error", "failure"}]
            # Verify the summary against the actual failure elements before classifying them.
            expected = int(suite.get("failures", "0")) + int(suite.get("errors", "0"))
            if (
                suite.tag != "testsuite"
                or expected != len(issues)
                or int(suite.get("skipped", "0")) != 0
                or (not issues and int(suite.get("tests", "0")) == 0)
            ):
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


if __name__ == "__main__":
    _, root, flavor = sys.argv
    if flavor not in {"anon", "nero"}:
        sys.exit(1)
    sys.exit(0 if attach_only(sorted(Path(root).glob(f"**/flavors/{flavor}/**/TEST-*.xml"))) else 1)
