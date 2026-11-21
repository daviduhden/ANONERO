# CI diagnostics and failure inventory

This directory records GitHub Actions evidence, root causes, and the regression checks
that prevent the same failure from returning. It is not a claim of runtime or security
validation.

## Scope and evidence

- Repository: `daviduhden/ANONERO`, default branch `main`.
- Workflows: `Tests`, `Build and publish APKs` and `Agent Knowledge Validation`. All are
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


## Read-only evidence refresh, 2026-11-14

Run [43633496677](https://github.com/daviduhden/ANONERO/actions/runs/43633496677) on a2c9cfb
passed JVM, lint, native, assemble, JNI, localization and architecture jobs. Instrumentation
failed before the repository script during the runner's optional settings write, with
Broken pipe (32)/adb 224 and no reports. Do not interpret it as executed/passing device tests
or evidence for the uncommitted working tree. Current CI source uses only workflow_dispatch;
release embeds synchronized required jobs. See [iteration](../development-iteration-2026-11.md).

## Latest evidence

- [Tests 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332)
  on `75939ea` passed eight jobs before the API 35 job was cancelled. Archived
  XML shows 110 JVM tests per flavor with zero failures, errors or skips. Android
  Lint recorded 24 warnings and zero errors per flavor, and Kotlin emitted five
  distinct project-owned warnings. Native ARM archives and debug APK assembly
  passed. No instrumented reports were produced.
- [Tests 43640612517](https://github.com/daviduhden/ANONERO/actions/runs/43640612517)
  on `dff2ecb` is the latest fully green run. Archived XML records four API 35
  tests per flavor with no failures, errors or skips. The tests cover package
  context, diagnostics isolation, LWS profile isolation and transport persistence
  failure. This predates signing isolation and the current wallet hardening.
- [Agent Knowledge Validation 44927810481](https://github.com/daviduhden/ANONERO/actions/runs/44927810481)
  on `75939ea` passed the Python regression and knowledge checks. It did not
  execute Android tests.
- The [historical inventory](historical-failures.md) covers all 39 API records,
  including successful runs with warnings, incomplete logs and artifact evidence.
  No production signing, sanitizer, fuzz or device-farm execution was found.

## Preventive source regressions in this audit

- [Coin selection preflight](coin-selection-preflight.md) records the wallet2
  input-selection threat review, JVM regressions and compatibility scope.
- [JNI conversion guards](../../app/src/main/cpp/AGENTS.md) prevent additional JNI
  string acquisition or result allocation while a JVM exception remains pending.
  The native source guard checks the corrected paths. A future authorized CI run
  still needs native compilation and a CheckJNI fixture for conversion failures.
