# CI diagnostics and failure inventory

This directory records GitHub Actions evidence, root causes, and the regression checks
that prevent the same failure from returning. It is not a claim of runtime or security
validation.

## Scope and evidence

- Repository: `daviduhden/ANONERO`, default branch `main`.
- Workflows: `Tests` (`test.yml`) and `Build and publish APKs` (`release.yml`); both are
  manual-only and owner-gated.
- Current evidence:
  - `42874760135` (`c228e36`) — all jobs pass; anon and nero each ran one instrumented
    smoke test with zero failures.
  - `43122308945` (`381d8ab`) — the post-format audit fixes compile and pass unit tests,
    lint, localization, JNI parity, native archives, and assemble. Instrumented still fails:
    `system_server` aborted while Gradle dexed the test APKs, and both reports had
    `tests=0`. Guest memory and disk were not exhausted.
  - `43108046671` (`02b97b8`) — all jobs pass except instrumented; the KVM-less emulator
    lost `system_server` and both reports had `tests=0`.
  - `a487cf5`…`32d8c1a` — formatting/lint commits; no CI run exists for them.
  - The prebuild/worker-limit fix for the test APKs is in the working tree and has no CI
    run yet.
  - No release, sanitizer, fuzz, or device-farm run exists. Absence of evidence is
    recorded as unverified, not as success.
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
reviewed statically only. Native and instrumented behavior remains unverified: the patch
series is checked in and applies to the pinned commit, but the local Monero submodule is
uninitialized locally; CI builds the archives, APKs, and smoke tests.
