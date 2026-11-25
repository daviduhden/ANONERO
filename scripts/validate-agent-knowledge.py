#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Read-only agent knowledge checks; Python 3.11+ and Git, no application execution."""

import argparse
import fnmatch
import json
import re
import subprocess
import sys
import tomllib
from collections import Counter
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit

NAMES = {
    "AGENTS.md",
    "AGENT.md",
    "SKILL.md",
    "CLAUDE.md",
    "GEMINI.md",
    "copilot-instructions.md",
    ".cursorrules",
    ".windsurfrules",
    "SKILLS.md",
}
INFRA = ("scripts/validate-agent-knowledge.py", "scripts/tests/test_agent_knowledge.py")
CONFIG = ".agent-knowledge-validator.toml"


def git(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, check=False)
    if result.returncode:
        raise ValueError(result.stderr.decode("utf-8", errors="replace").strip())
    return result.stdout.decode("utf-8")


def instruction(path, config):
    return (
        PurePosixPath(path).name in NAMES
        or path.endswith((".agent.md", ".instructions.md", ".mdc"))
        or any(fnmatch.fnmatchcase(path, g) for g in config.get("instruction_globs", []))
    )


def load_config(text):
    if not text:
        return {}
    data = tomllib.loads(text)
    if set(data) != {"validator"} or not isinstance(data["validator"], dict):
        raise ValueError("configuration requires only [validator]")
    config = data["validator"]
    supported = {
        "contract_version",
        "exclude_paths",
        "instruction_globs",
        "minimum_changed_lines",
        "required_headings",
        "required_phrases",
    }
    if set(config) - supported or config.get("contract_version") != "1.0.0":
        raise ValueError("unsupported configuration key or contract version")
    minimum = config.get("minimum_changed_lines", 1)
    if type(minimum) is not int or not 1 <= minimum <= 1000:
        raise ValueError("minimum_changed_lines must be 1..1000")
    for key in ("exclude_paths", "instruction_globs", "required_headings", "required_phrases"):
        value = config.get(key, {} if key.startswith("required_") else [])
        if key.startswith("required_"):
            if not isinstance(value, dict):
                raise ValueError(f"{key} must be a table")
            paths = list(value)
            values = list(value.values())
        else:
            paths, values = value, [value]
        if not isinstance(paths, list) or any(
            not isinstance(p, str) or not p or p.startswith("/") or ".." in PurePosixPath(p).parts
            for p in paths
        ):
            raise ValueError(f"{key} requires repository-relative paths")
        if any(
            not isinstance(v, list) or any(not isinstance(s, str) or not s for s in v)
            for v in values
        ):
            raise ValueError(f"{key} requires lists of nonempty strings")
    return config


def normalized(text):
    # Compare a multiset of content lines. Moving a paragraph or changing its
    # spacing alone supplies no evidence of a knowledge update.
    lines = []
    for line in text.splitlines():
        line = line.strip()
        if (
            not line
            or line.startswith(("#", "<!--", "-->"))
            or re.match(r"(?i)^(updated?|generated|last[ -]updated|date|updated_at)\s*[:=]", line)
        ):
            continue
        line = " ".join(line.split())
        if re.search(r"[a-zA-Z0-9]", line):
            lines.append(line)
    return Counter(lines)


def substantive(old, new, minimum=1):
    a, b = normalized(old), normalized(new)
    changed = list(((a - b) + (b - a)).elements())
    return (
        len(changed) >= minimum and sum(len(re.sub(r"[^a-zA-Z0-9]", "", s)) for s in changed) >= 24
    )


def heading(text, name):
    return any(
        re.sub(r"^#+\s+|\s+#+$", "", line).strip() == name
        for line in text.splitlines()
        if line.startswith("#")
    )


def validate(root, args):
    checks = []

    def add(rule, status, message, files=(), remediation=None):
        checks.append(
            dict(
                rule_id=rule,
                status=status,
                message=message,
                files=list(files),
                remediation=remediation,
            )
        )

    if args.scope == "range":
        # Read instructions and project changes from the same committed snapshot.
        base, head = [
            git(root, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}").strip()
            for ref in (args.base, args.head)
        ]
        baseline = git(root, "merge-base", base, head).strip()
        paths = git(root, "ls-tree", "-r", "--name-only", "-z", head).split("\0")
        changed = git(
            root, "diff", "--no-ext-diff", "--no-renames", "--name-only", "-z", baseline, head, "--"
        ).split("\0")

        def read(path):
            return git(root, "show", f"{head}:{path}")
    else:
        baseline = git(root, "rev-parse", "--verify", "HEAD^{commit}").strip()
        paths = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split(
            "\0"
        )
        changed = git(
            root, "diff", "--no-ext-diff", "--no-renames", "--name-only", "-z", baseline, "--"
        ).split("\0")
        changed += git(root, "ls-files", "--others", "--exclude-standard", "-z").split("\0")
        paths = [p for p in paths if p and (root / p).exists()]

        def read(path):
            target = (root / path).resolve()
            if not target.is_relative_to(root):
                raise ValueError(f"instruction path escapes repository: {path}")
            return target.read_text(encoding="utf-8")

    paths = sorted(set(paths) - {""})
    changed = sorted(set(changed) - {""})
    config = load_config(read(CONFIG) if CONFIG in paths else "")
    texts = {p: read(p) for p in paths if instruction(p, config)}
    updates, cosmetic = [], []
    for path in changed:
        if not instruction(path, config) or path not in texts:
            continue
        old_result = subprocess.run(
            ["git", "-C", str(root), "show", f"{baseline}:{path}"], capture_output=True, check=False
        )
        # A missing baseline file is an addition; other Git failures must surface.
        old_paths = git(root, "ls-tree", "-r", "--name-only", "-z", baseline, "--", path).split(
            "\0"
        )
        if old_result.returncode and path in old_paths:
            raise ValueError(f"cannot read baseline instruction: {path}")
        old = old_result.stdout.decode("utf-8") if not old_result.returncode else ""
        (
            updates
            if substantive(old, texts[path], config.get("minimum_changed_lines", 1))
            else cosmetic
        ).append(path)
    project = [
        p
        for p in changed
        if not instruction(p, config)
        and p not in INFRA
        and not any(fnmatch.fnmatchcase(p, g) for g in config.get("exclude_paths", []))
    ]
    add(
        "AK001",
        "FAIL" if project and not updates else "PASS",
        "Project changes require substantive maintained knowledge."
        if project and not updates
        else "Knowledge synchronization satisfied.",
        updates or project,
        "Update the relevant AGENTS.md or SKILL.md with reusable guidance."
        if project and not updates
        else None,
    )
    structure = [
        p
        for p in {
            "AGENTS.md",
            *config.get("required_headings", {}),
            *config.get("required_phrases", {}),
        }
        if p not in texts
    ]
    links = []
    for path, text in texts.items():
        required = list(config.get("required_headings", {}).get(path, []))
        if path == "AGENTS.md":
            required.append("Continuous Agent & Skills Maintenance")
        if PurePosixPath(path).name == "SKILL.md":
            required.append("Maintenance & Continuous Learning")
            front = re.match(r"\A---\n(.*?)\n---(?:\n|$)", text, re.DOTALL)
            fields = (
                dict(re.findall(r"^(name|description):\s*(.+)$", front[1], re.MULTILINE))
                if front
                else {}
            )
            if (
                not fields.get("description", "").strip("\"' ")
                or fields.get("name", "").strip("\"' ") != PurePosixPath(path).parent.name
            ):
                structure.append(path)
        if (
            not text.strip()
            or any(not heading(text, h) for h in required)
            or any(
                " ".join(s.split()) not in " ".join(text.split())
                for s in config.get("required_phrases", {}).get(path, [])
            )
        ):
            structure.append(path)
        # Inline Markdown links and reference definitions; fragments are not checked.
        targets = re.findall(r"\]\(\s*(<[^>]+>|[^\s)]+)", text)
        targets += re.findall(r"(?m)^\s{0,3}\[[^\]]+\]:\s*(<[^>]+>|\S+)", text)
        for target in targets:
            url = urlsplit(target.strip("<>"))
            if url.scheme or url.netloc or not url.path:
                continue
            local = unquote(url.path)
            resolved = (PurePosixPath(path).parent / local).as_posix()
            # Resolve '..' lexically, independent of the checked-out worktree.
            parts = []
            escaped = local.startswith("/")
            for part in PurePosixPath(resolved).parts:
                if part == "..":
                    if parts:
                        parts.pop()
                    else:
                        escaped = True
                elif part != ".":
                    parts.append(part)
            dest = "/".join(parts)
            if escaped or not any(p == dest or p.startswith(dest + "/") for p in paths):
                links.append(path)
    add(
        "AK002",
        "FAIL" if structure else "PASS",
        "Instruction structure and maintenance sections.",
        sorted(set(structure)),
    )
    add(
        "AK003",
        "FAIL" if links else "PASS",
        "Repository Markdown references resolve.",
        sorted(set(links)),
    )
    add(
        "AK004",
        "FAIL" if cosmetic else "WARN" if updates else "PASS",
        "Cosmetic updates do not count; substantive updates need human relevance review.",
        cosmetic or updates,
    )
    add("AK005", "PASS", "Configuration and Git snapshot loaded.")
    duplicates, seen, directives = set(), {}, {}
    for path, text in texts.items():
        if text in seen:
            duplicates.update((path, seen[text]))
        seen[text] = path
        for line in text.splitlines():
            match = re.fullmatch(
                r"(.+?)\s+(must|shall)\s+(not\s+)?(.+)", line.strip(), re.IGNORECASE
            )
            if match:
                key = (match[1].lower(), match[2].lower(), match[4].lower())
                negated = bool(match[3])
                if (key, not negated) in directives:
                    duplicates.update((path, directives[key, not negated]))
                directives[key, negated] = path
    add(
        "AK006",
        "WARN" if duplicates else "PASS",
        "Exact duplicates or opposed modal directives require review.",
        sorted(duplicates),
    )
    return checks


def report(checks, output):
    errors = [c for c in checks if c["status"] == "FAIL"]
    warnings = [c for c in checks if c["status"] == "WARN"]
    result = dict(
        schema_version="1.0",
        contract_version="1.0.0",
        status="FAIL" if errors else "PASS",
        summary=dict(errors=len(errors), warnings=len(warnings)),
        checks=checks,
        errors=errors,
        warnings=warnings,
    )
    if output == "json":
        print(json.dumps(result, ensure_ascii=True))
    else:
        for c in checks:
            print(f"[{c['status']}] {c['rule_id']} - {c['message']}")
            for path in c["files"]:
                print(f"  {path}")
            if c["remediation"]:
                print(f"  Remediation: {c['remediation']}")
        print(
            f"Agent Knowledge Validation\nStatus: {result['status']}\nErrors: {len(errors)}\nWarnings: {len(warnings)}"
        )
    return int(bool(errors))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", choices=("worktree", "range"), default="worktree")
    parser.add_argument("--base")
    parser.add_argument("--head")
    parser.add_argument("--format", choices=("text", "json"), default="text")
    args = parser.parse_args()
    try:
        if args.scope == "range" and not (args.base and args.head):
            raise ValueError("range requires both --base and --head")
        if args.scope == "worktree" and (args.base or args.head):
            raise ValueError("--base/--head require --scope range")
        root = Path(git(Path.cwd(), "rev-parse", "--show-toplevel").strip()).resolve()
        return report(validate(root, args), args.format)
    except (OSError, ValueError) as exc:
        report(
            [
                dict(
                    rule_id="AK005",
                    status="FAIL",
                    message=str(exc),
                    files=[],
                    remediation="Check arguments, configuration and Git state.",
                )
            ],
            args.format,
        )
        return 2


if __name__ == "__main__":
    sys.exit(main())
