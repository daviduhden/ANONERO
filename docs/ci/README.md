# CI diagnostics and failure inventory

This directory records GitHub Actions evidence, root causes, and the regression checks
that prevent the same failure from returning. It is not a claim of runtime or security
validation.

## Scope and evidence

- Repository: `daviduhden/ANONERO`, default branch `main`.
- Workflows: `Tests` (`test.yml`) and `Build and publish APKs` (`release.yml`); both are
  manual-only and owner-gated.
- Current evidence:
  - `43528256395` (`0cf9db0`) — all jobs pass, including instrumented; anon and nero each
    ran one smoke test with zero failures. Boot took 919 s; lint is 0/0, 62 JVM tests per
    flavor, and the adapter compiles under `-Werror`.
  - `42874760135` (`c228e36`) — all jobs pass; anon and nero each ran one instrumented
    smoke test with zero failures.
  - `43521054641` (`feb985e`) — all code, native, assemble, lint, and JNI gates pass
    (62 JVM tests per flavor, lint 0/0, `-Werror` clean). Instrumented fails after a 630 s
    boot when the runner's post-boot `input`/`overlay` adb calls hit broken pipes; the test
    script never starts and no reports are produced.
  - `43122308945` (`381d8ab`) — the post-format audit fixes compile and pass unit tests,
    lint, localization, JNI parity, native archives, and assemble. Instrumented still fails:
    `system_server` aborted while Gradle dexed the test APKs, and both reports had
    `tests=0`. Guest memory and disk were not exhausted.
  - `43108046671` (`02b97b8`) — all jobs pass except instrumented; the KVM-less emulator
    lost `system_server` and both reports had `tests=0`.
  - `a487cf5`…`32d8c1a` — formatting/lint commits; no CI run exists for them.
  - The prebuild/worker-limit fix for the test APKs is committed; `43521054641` validated
    the prebuild step, and `43528256395` then passed both flavors.
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
uninitialized locally; CI builds the archives and APKs, while the instrumented smoke test
is unstable on the KVM-less runner.
