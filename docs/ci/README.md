# CI diagnostics and failure inventory

This directory records GitHub Actions evidence, root causes, and the regression checks
that prevent the same failure from returning. It is not a claim of runtime or security
validation.

## Scope and evidence

- Repository: `daviduhden/ANONERO`, default branch `main`.
- Workflows: `Tests`, `Build and publish APKs` and `Agent Knowledge Validation`. All are
  manual-only and owner-gated.
- Historical baselines (see Latest evidence below):
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
- [Backup restore ownership and staging](backup-restore.md)
- [Seed and key JNI containment](seed-jni-boundary.md)
- [QR frame ownership and received transaction details](qr-frame-and-transaction-detail.md)
- [PIN persistence and transaction Parcel](pin-throttle-and-transaction-parcel.md)
- [LWS reconnect and wallet ownership](lws-session-reapply.md)
- [Dependency caching and Debug APK verification](gradle-cache.md)
- [Managed wallet lifetime](managed-wallet-lifetime.md)
- [UI session boundaries and animated QR lifecycle](ui-session-boundaries.md)
- [JNI review snapshots](jni-review-snapshot.md)

## Evidence limits

GitHub Actions logs show the state of a specific commit and workflow definition. They do
not prove the current working tree. Fixes described as “implemented, CI pending” have been
reviewed statically only. The local Monero submodule is uninitialized. GitHub Actions
evidence confirms native compilation and instrumented behavior only for the recorded
revisions and runner configuration. The instrumented smoke test has previously been
unstable on runners without usable KVM.


## Read-only evidence refresh, 2026-11-14

Run [43633496677](https://github.com/daviduhden/ANONERO/actions/runs/43633496677) on a2c9cfb
passed JVM, lint, native, assemble, JNI, localization and architecture jobs. Instrumentation
failed before the repository script during the runner's optional settings write, with
Broken pipe (32)/adb 224 and no reports. Do not interpret it as executed/passing device tests
or evidence for the uncommitted working tree. Current CI source uses only workflow_dispatch;
release embeds synchronized required jobs. See [iteration](../development-iteration-2026-11.md).

## Latest evidence

- [Tests 45691880945](https://github.com/daviduhden/ANONERO/actions/runs/45691880945)
  on `bf1cf25` built both ARM native archives with Clang 21 and linked both Debug
  flavors. Archived JUnit XML records 133 cases per flavor with zero failures,
  errors or skips. Android Lint and API 35 instrumentation both stopped at Maven
  Central HTTP 429 before their tasks ran. Their latest completed reports remain
  those from `2ca0edb`. See the [CI-039 cache diagnosis](gradle-cache.md). [Knowledge validation](https://github.com/daviduhden/ANONERO/actions/runs/45691880970)
  passed at `bf1cf25`.

- [Tests 45679643013](https://github.com/daviduhden/ANONERO/actions/runs/45679643013)
  on `2ca0edb` passed all nine jobs. Both debug APKs linked against the ARM
  archives. The API 35 x86_64 guest completed boot in 30.9 seconds. Each flavor
  has 130 JVM and nine instrumented cases with zero failures, errors or skips.
  Lint is zero errors and 13 warnings per flavor. The build did not exercise a
  live Maven retry. The static CI suite passed its 36 Python regressions.
- [Agent Knowledge Validation 45679643727](https://github.com/daviduhden/ANONERO/actions/runs/45679643727)
  on the same commit passed its two jobs and nine Python regressions, including
  committed-range validation.
- [Tests 45677086262](https://github.com/daviduhden/ANONERO/actions/runs/45677086262)
  on `b85919b` was cancelled overall after the JVM job hit Maven Central HTTP
  429 before test execution. Native archive creation, debug assembly and instrumentation remained
  incomplete. See [CI-039](historical-failures.md#ci-039--maven-central-throttled-gradle-configuration-on-b85919b).
- [Tests 44932222742](https://github.com/daviduhden/ANONERO/actions/runs/44932222742)
  on `81f17ef` passed all nine jobs. The API 35 emulator used hardware acceleration
  and booted in about 45 seconds. Archived XML confirms 113 JVM tests and four
  instrumented tests per flavor, with zero failures, errors or skips. The Kotlin
  logs contain no project source warnings. Android Lint recorded zero errors and
  11 warnings per flavor: eight dependency update notices and three checked
  preferences-write suggestions.
- [Agent Knowledge Validation 44932222747](https://github.com/daviduhden/ANONERO/actions/runs/44932222747)
  on the same commit passed its two jobs, including nine Python regressions.
- [Tests 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332)
  on `75939ea` passed eight jobs before the API 35 job was cancelled. Archived
  XML shows 110 JVM tests per flavor with zero failures, errors or skips. Android
  Lint recorded 24 warnings and zero errors per flavor, and Kotlin emitted five
  distinct project-owned warnings. Native ARM archives and debug APK assembly
  passed. No instrumented reports were produced.
- [Tests 43640612517](https://github.com/daviduhden/ANONERO/actions/runs/43640612517)
  on `dff2ecb` is an earlier fully green run. Archived XML records four API 35
  tests per flavor with no failures, errors or skips. The tests cover package
  context, diagnostics isolation, LWS profile isolation and transport persistence
  failure. This predates signing isolation and the current wallet hardening.
- [Agent Knowledge Validation 44927810481](https://github.com/daviduhden/ANONERO/actions/runs/44927810481)
  on `75939ea` passed the Python regression and knowledge checks. It did not
  execute Android tests.
- The [historical inventory](historical-failures.md) covers all 51 API records,
  including successful runs with warnings, incomplete logs and artifact evidence.
  No production signing, sanitizer, fuzz or device-farm execution was found.

## Verified platform coverage

| Area | Evidence at `2ca0edb` | Remaining validation |
| --- | --- | --- |
| Android floor and toolchain | API 29 manifest floor verified for both debug APKs. Compile SDK 37.2, target 37, Kotlin 2.4.21, AGP 9.5.1 and Gradle 9.9.0 are pinned. | API 29 runtime behavior was not tested in this run. |
| Native compile and link | Monero ARM archives and the JNI adapter built for `arm64-v8a` and `armeabi-v7a`. Debug ANON and NERO linked with NDK r30. | ARM device execution, JNI fault injection and dedicated CheckJNI coverage remain open. |
| Android instrumentation | API 35 `google_apis` x86_64 guest ran nine tests per debug flavor. This is a test guest, with no x86_64 production ABI. | No ARM hardware report or other API level report in the current run. |
| Wallet and transport | JVM test XML and API 35 XML cover the cases shipped in this revision, including LWS profile isolation and transport persistence failure. | Live Tor bridges, real LWS/daemon routing and full wallet2 flows require dedicated environment and device coverage. |
| Release and signing | Manual workflow configuration is present. | No release workflow run, production signing result or signed APK artifact is recorded. |
| Sanitizers and fuzzing | Archived jobs were reviewed for diagnostic patterns. | No sanitizer or fuzz suite execution is recorded, so these diagnostics have no test verdict. |

## Preventive source regressions in this audit

- [Coin selection preflight](coin-selection-preflight.md) records the wallet2
  input-selection threat review, JVM regressions and compatibility scope.
- [JNI conversion guards](../../app/src/main/cpp/AGENTS.md) prevent additional JNI
  string acquisition or result allocation while a JVM exception remains pending.
  The native source guard checks the corrected paths. A future authorized CI run
  still needs native compilation and a CheckJNI fixture for conversion failures.

## New source changes after 81f17ef

- [OpenSSL API level](openssl-android-api.md) explains W-008 and the guard against
  accidentally selecting a compiler above API 29.
- [Broadcast recovery](broadcast-recovery.md) distinguishes a transaction accepted
  by wallet2 from later refresh or persistence failures.
- JNI network and background-sync entry points map throwing wallet2 calls to the
  existing failure result. The static guard checks the new catch boundaries and
  rejection of negative initialization size. Native CI and fault-injection
  evidence are pending.

## Approved safety changes awaiting CI

The [safety iteration](../development-iteration-approved-safety.md) records the
source changes and evidence required for release signing, transport transitions,
the native listener, lock faults and review ownership. The [payment URI](payment-uri-parsing.md)
and [spend export](spend-export-files.md) records describe the JVM regressions.
Use reports from the exact candidate SHA. Prior green jobs do not validate this
worktree.

The signing gate now requires a direct successful instrumentation result for
each flavor. The release source checker rejects a removed dependency, a
condition that bypasses instrumentation, and an incorrect signing environment.
The Python regression cases run in the existing manual workflow. A real
instrumented run and the signing-gate behavior on GitHub remain to be verified
for the candidate SHA.
