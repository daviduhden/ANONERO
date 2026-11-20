#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""CI-only regression checks for the agent knowledge validator."""

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "validate-agent-knowledge.py"
SPEC = importlib.util.spec_from_file_location("knowledge", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

AGENT = "# Guidance\n\n## Continuous Agent & Skills Maintenance\n\nKeep guidance current.\n"
LESSON = "\nReject malformed proxy configuration before attempting any connection.\n"


class KnowledgeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q")
        self.write("AGENTS.md", AGENT)
        self.write("src.txt", "baseline\n")
        self.commit()

    def git(self, *args):
        return subprocess.run(
            ["git", "-C", str(self.root), *args], check=True, capture_output=True, text=True
        ).stdout.strip()

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    def commit(self):
        # Fixture commits execute only in CI, never in the real repository.
        self.git("add", ".")
        self.git(
            "-c",
            "user.name=Fixture",
            "-c",
            "user.email=fixture@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-qm",
            "fixture",
        )
        return self.git("rev-parse", "HEAD")

    def run_check(self, *args):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--format", "json", *args],
            cwd=self.root,
            capture_output=True,
            text=True,
            check=False,
        )
        return result.returncode, json.loads(result.stdout)

    def test_clean_and_untracked_change(self):
        self.assertEqual(self.run_check()[0], 0)
        self.write("new file.txt", "new source")
        self.assertEqual(self.run_check()[0], 1)
        self.write("AGENTS.md", AGENT + LESSON)
        code, report = self.run_check()
        self.assertEqual(code, 0)
        self.assertIn("AK004", [c["rule_id"] for c in report["warnings"]])

    def test_cosmetic_change(self):
        self.write("AGENTS.md", AGENT.replace("Keep guidance", "Keep  guidance"))
        self.assertIn("AK004", [c["rule_id"] for c in self.run_check()[1]["errors"]])

    def test_range_reads_head_not_worktree(self):
        base = self.git("rev-parse", "HEAD")
        self.write("src.txt", "changed\n")
        head = self.commit()
        self.write("AGENTS.md", AGENT + LESSON)
        self.assertEqual(self.run_check("--scope", "range", "--base", base, "--head", head)[0], 1)

    def test_staged_and_unstaged(self):
        self.write("src.txt", "changed")
        self.git("add", "src.txt")
        self.write("AGENTS.md", AGENT + LESSON)
        self.assertEqual(self.run_check()[0], 0)

    def test_rename_and_deletion(self):
        self.git("mv", "src.txt", "renamed.txt")
        self.assertEqual(self.run_check()[0], 1)
        (self.root / "renamed.txt").unlink()
        self.assertEqual(self.run_check()[0], 1)

    def test_broken_reference_and_malformed_skill(self):
        self.write("AGENTS.md", AGENT + "\nSee [missing](missing.md).\n")
        self.write(".agents/skills/example/SKILL.md", "# Missing frontmatter\n")
        errors = [c["rule_id"] for c in self.run_check()[1]["errors"]]
        self.assertIn("AK002", errors)
        self.assertIn("AK003", errors)

    def test_execution_errors_and_json(self):
        self.assertEqual(
            self.run_check("--scope", "range", "--base", "missing", "--head", "HEAD")[0], 2
        )
        self.write(".agent-knowledge-validator.toml", '[validator]\ncontract_version="9"\n')
        code, report = self.run_check()
        self.assertEqual(code, 2)
        self.assertEqual(report["schema_version"], "1.0")
        self.assertEqual(report["errors"][0]["rule_id"], "AK005")

    def test_duplicate_and_conflicting_directives(self):
        self.write(
            "AGENTS.md",
            AGENT + "\nOperators must retain results.\nOperators must not retain results.\n",
        )
        self.assertIn("AK006", [c["rule_id"] for c in self.run_check()[1]["warnings"]])

    def test_configuration_rejects_unsupported_keys(self):
        for text in (
            '[validator]\ncontract_version="1.0.0"\nunknown=true\n',
            '[validator]\ncontract_version="1.0.0"\nexclude_paths=["../outside"]\n',
        ):
            with self.assertRaises(ValueError):
                MODULE.load_config(text)


if __name__ == "__main__":
    unittest.main()
