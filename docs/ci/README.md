# CI diagnostics and failure inventory

This directory records GitHub Actions evidence, root causes, and the regression checks
that prevent the same failure from returning. It is not a claim of runtime or security
validation.

## Scope and evidence

- Repository: `daviduhden/ANONERO`, default branch `main`.
- Workflows: `Tests` (`test.yml`) and `Build and publish APKs` (`release.yml`); both are
  manual-only and owner-gated.
- Accessible runs at the time of the audit:
  - `42686398569` — `workflow_dispatch`, `52d67d4`, failure.
  - `42552892586` — historical `push`, `ed8b097`, failure.
  - `42547686992` — historical `push`, `c385630`, failure.
- No successful run, release run, instrumented run, sanitizer run, lint run, or emulator
  run is available. Absence of evidence is recorded as unverified, not as success.
- Logs were read with `gh run view --log` and `--log-failed`. No workflow was executed,
  rerun, or cancelled during this audit.

## Documentation map

- [`historical-failures.md`](historical-failures.md) — run-by-run inventory and root
  causes.
- [`preventive-audit.md`](preventive-audit.md) — current static audit, warning policy,
  and pending CI evidence.

## Evidence limits

GitHub Actions logs show the state of a specific commit and workflow definition. They do
not prove the current working tree. Fixes described as “implemented, CI pending” have been
reviewed statically only. Native and instrumented behavior remains unverified while the
Monero submodule and patch series are absent.
