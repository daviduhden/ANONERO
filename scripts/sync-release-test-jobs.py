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

"""Synchronize release's embedded manual test suite without executing jobs.

The supported format is the repository's indentation-based workflow layout, not
arbitrary YAML. test.yml owns shared jobs from unit through the end of the file.
Only checkout refs and Gradle cache policy differ in the release copy.
"""

import argparse
import difflib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
START = "  # BEGIN GENERATED TEST JOBS\n"
END = "  # END GENERATED TEST JOBS\n"
SOURCE_REF = "${{ inputs.ref || github.ref }}"
RELEASE_REF = "${{ github.sha }}"


def require_manual_dispatch(text: str, name: str) -> None:
    match = re.search(r"^on:\n(.*?)(?=^[^\s#])", text, re.MULTILINE | re.DOTALL)
    if match is None or re.findall(r"^  ([\w-]+):", match[1], re.MULTILINE) != [
        "workflow_dispatch"
    ]:
        raise ValueError(f"{name}: only workflow_dispatch is permitted")


def shared_jobs(source: str) -> tuple[str, list[str]]:
    if source.count("\n  unit:\n") != 1:
        raise ValueError("test.yml: expected one unit job starting the shared suite")
    jobs = "  unit:\n" + source.split("\n  unit:\n", 1)[1]
    names = re.findall(r"^  ([\w-]+):$", jobs, re.MULTILINE)
    required = {
        "unit", "lint", "jni-contract", "i18n", "native-language-policy",
        "native", "assemble", "instrumented",
    }
    reserved = {"authorize", "test", "build", "sign", "publish"}
    if not required.issubset(names) or len(names) != len(set(names)) or reserved.intersection(names):
        raise ValueError("test.yml: missing, duplicate or reserved shared job names")
    return jobs.rstrip() + "\n", names


def aggregate_job(names: list[str]) -> str:
    return """
  test:
    name: Required test suite
    needs: [authorize, @NEEDS@]
    # Run the aggregate even if a required job failed or was skipped; reject it below.
    if: ${{ always() && needs.authorize.result == 'success' }}
    runs-on: ubuntu-24.04
    timeout-minutes: 5
    permissions: {}
    outputs:
      native_artifact: ${{ needs.native.outputs.artifact }}
    steps:
      - name: Require all tests and the native artifact from this run
        shell: bash
        env:
          JOB_RESULTS: ${{ toJSON(needs) }}
          RUN_INSTRUMENTED: ${{ inputs.run_instrumented }}
        run: |
          set -euo pipefail
          python3 - <<'PY'
          import json
          import os

          jobs = json.loads(os.environ["JOB_RESULTS"])
          required = @NAMES@
          run_instrumented = os.environ["RUN_INSTRUMENTED"] == "true"
          for name in required:
              expected = "success"
              if name == "instrumented" and not run_instrumented:
                  expected = "skipped"
              actual = jobs.get(name, {}).get("result")
              if actual != expected:
                  raise SystemExit(f"Required job {name}: expected {expected}, got {actual}")
          artifact = jobs.get("native", {}).get("outputs", {}).get("artifact", "")
          if not artifact:
              raise SystemExit("Required native artifact output is missing")
          print("Required test suite passed in this workflow execution")
          PY
""".replace("@NEEDS@", ", ".join(names)).replace("@NAMES@", repr(["authorize", *names]))


def generated_block(source: str) -> str:
    jobs, names = shared_jobs(source)
    checkouts = len(re.findall(r"^        uses: actions/checkout@", jobs, re.MULTILINE))
    refs = jobs.count(f"          ref: {SOURCE_REF}\n")
    if not checkouts or refs != checkouts:
        raise ValueError("Unsupported shared job cache/ref format")
    jobs = jobs.replace(SOURCE_REF, RELEASE_REF)
    jobs = jobs.replace("cache-provider: basic", "cache-disabled: true")
    if (
        "cache-provider:" in jobs or SOURCE_REF in jobs
        or "cache-disabled: false" in jobs or "uses: actions/cache@" in jobs
    ):
        raise ValueError("Unsupported shared job cache/ref format")
    return START + jobs + aggregate_job(names) + END


def check_env(source: str, release: str) -> None:
    pattern = r"^  ((?:ANDROID_\w+|GRADLE_OPTS)): (.+)$"
    source_env = dict(re.findall(pattern, source.split("\njobs:\n", 1)[0], re.MULTILINE))
    release_env = dict(re.findall(pattern, release.split("\njobs:\n", 1)[0], re.MULTILINE))
    if not source_env or source_env != release_env:
        raise ValueError("Shared test/release workflow environment values differ")


def synchronize(source: str, release: str) -> str:
    require_manual_dispatch(source, "test.yml")
    require_manual_dispatch(release, "release.yml")
    check_env(source, release)
    if release.count(START) != 1 or release.count(END) != 1:
        raise ValueError("release.yml: expected exactly one generated block")
    before, remaining = release.split(START, 1)
    _, after = remaining.split(END, 1)
    return before + generated_block(source) + after


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="fail on drift, without writing")
    mode.add_argument("--write", action="store_true", help="regenerate only the release job block")
    args = parser.parse_args()
    source_path = ROOT / ".github/workflows/test.yml"
    release_path = ROOT / ".github/workflows/release.yml"
    source = source_path.read_text(encoding="utf-8")
    release = release_path.read_text(encoding="utf-8")
    try:
        updated = synchronize(source, release)
    except ValueError as error:
        parser.error(str(error))
    if args.write:
        release_path.write_text(updated, encoding="utf-8")
        print("Regenerated embedded release test jobs (no workflow executed)")
    elif updated != release:
        print("".join(difflib.unified_diff(
            release.splitlines(keepends=True), updated.splitlines(keepends=True),
            fromfile=str(release_path), tofile="expected generated jobs",
        )))
        return 1
    else:
        print("Manual workflow shared jobs are synchronized (static check only)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
