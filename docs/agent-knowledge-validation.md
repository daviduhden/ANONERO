# Agent Knowledge Validation

ANONERO adapts the sibling repositories' Perl validator to Python 3.11+ and Git,
using only the standard library. The CLI, AK001–AK006 identifiers, report fields,
contract version `1.0.0`, schema `1.0`, and exit codes follow the shared convention.
This is a Python adaptation, not a claim of complete Perl implementation parity.

```sh
python3 scripts/validate-agent-knowledge.py --scope worktree
python3 scripts/validate-agent-knowledge.py --scope range --base BASE --head HEAD
python3 scripts/validate-agent-knowledge.py --scope worktree --format json
```

Run from anywhere inside the worktree. Worktree compares HEAD with current tracked
files and includes untracked non-ignored files, deletions, both sides of renames,
and staged and unstaged net changes. Range compares the merge base with HEAD_REF
and reads instructions and configuration from HEAD_REF, not the local checkout.
An unborn branch or unavailable revision is an execution error. No files are edited.

| Rule | Check |
| --- | --- |
| AK001 | Project changes require a substantive instruction or skill update. Only the validator script and its regression module are exempt. |
| AK002 | Nonempty instruction files, required maintenance headings, skill name matching its directory and nonempty description. Frontmatter is inspected as simple single-line fields, not parsed as full YAML. |
| AK003 | Relative inline Markdown links and reference definitions resolve inside the snapshot. URL schemes and fragments are skipped. Inline code paths and Markdown anchor validity are outside scope. |
| AK004 | Reject cosmetic updates and request human review of substantive updates. Whitespace, headings, date metadata and unchanged normalized lines do not count. At least 24 alphanumeric changed characters and one effective line are required. Deleted instructions do not count as updates. |
| AK005 | Invalid configuration, Git state or execution. |
| AK006 | Warn about exact duplicate files and single-line directly opposed must/shall directives. This is not semantic contradiction analysis. |

Recognized names include AGENTS.md, AGENT.md, SKILL.md, SKILLS.md, CLAUDE.md,
GEMINI.md, copilot-instructions.md, .cursorrules, .windsurfrules and files ending
in .agent.md, .instructions.md or .mdc. Root AGENTS.md requires
`Continuous Agent & Skills Maintenance`; skills require
`Maintenance & Continuous Learning`.

Exit codes: 0 for no policy errors (warnings allowed), 1 for policy failure,
2 for execution/configuration errors. Text output includes `[STATUS] AKnnn` and
summary counts. JSON contains `schema_version`, `contract_version`, `status`,
`summary` (errors/warnings counts), `checks`, `errors`, and `warnings`. Each check
contains `rule_id`, `status`, `message`, `files`, and `remediation` (null if absent).
Argument syntax errors use argparse's stderr diagnostic and exit 2.

## Optional configuration

`.agent-knowledge-validator.toml` supports only `[validator]` with mandatory
`contract_version = "1.0.0"` and these optional keys:

- `exclude_paths`: list of relative fnmatch globs exempt from AK001 only. Document
  any exclusion in AGENTS.md. No repository exclusions are currently configured.
- `instruction_globs`: list of additional relative instruction globs.
- `minimum_changed_lines`: integer from 1 to 1000.
- `required_headings` and `required_phrases`: tables mapping relative instruction
  paths to lists of required strings.

Unknown keys, absolute paths and parent traversal are rejected. Unlike the Perl
implementation, strict_text, skill_reference_prefixes and inline_path_checks are
not implemented and are rejected rather than silently ignored. Python fnmatch
semantics apply (asterisks can cross path separators). Configuration cannot disable
AK002–AK006. No configuration can execute commands.

## Maintenance and evidence

Review the relevant instructions after each implementation change. Preserve lessons
about failure causes, boundaries and verification; do not add filler to pass the gate.
Update this contract and the CI-only regression module when behavior changes.

Regression coverage lives in `scripts/tests/test_agent_knowledge.py` and can be run
in authorized CI with `python3 -m unittest discover -s scripts/tests -p 'test_agent_knowledge.py'`.
No workflow has been added or dispatched by this change. Local execution of the
validator is static analysis; regression tests remain CI-only under AGENTS.md §5.

The tool reads repository instructions and local Git objects; it makes no network
requests and handles no wallet data. Its output includes repository paths and policy
text. Textual checks do not prove instruction relevance, runtime correctness,
anonymity, privacy or security. Multiline directives, complex Markdown and YAML,
submodule contents and general semantic contradictions require human review.
