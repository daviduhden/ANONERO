# Preventive compilation and warning audit

## Status

This is a static audit followed by manual CI validation. Run `43528256395` (`0cf9db0`)
passed every job: 62 JVM tests per flavor, lint 0/0, `-Werror` with no adapter warning,
serialization/BouncyCastle, exact JNI parity, native archives, assemble/link, and one
instrumented smoke test per flavor with zero failures. The emulator booted in 919 s. Run
`43521054641` had failed before the script after a 630 s boot, so the hosted emulator
remains non-deterministic; deterministic execution still requires a KVM runner or device
farm.

## Latest log and artifact review

[Tests 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332)
passed the non-instrumented jobs and was cancelled while the API 35 emulator was
booting with TCG. The [historical inventory](historical-failures.md) records the
KVM evidence, prior cancellation and recurrent attach failures. The
`instrumented` orchestration now distinguishes accessibility of an existing
KVM device from software startup and retains the required checks for both
flavors. These changes are pending an authorized runtime check.

The lint XML from the latest run contains **24 warnings per flavor**, even
though the lint job succeeded. The JVM XML contains **110 passing tests per
flavor**. Treat build and test outcomes separately from warning counts.

| Finding | Audit result | Validation |
| --- | --- | --- |
| Five project Kotlin warnings, W-007 | Removed only redundant safe calls on receivers proven non-null in `NetworkSettings.kt` | Both flavors must compile again |
| 12 unused proxy-setting resources, W-009 | Removed 12 unused strings across English, Spanish, Russian and Simplified Chinese after checking references. The transport editor remains the active path | Verify resource checks and absence in new per-flavor lint XML |
| Four `UseKtx` warnings, W-009 | The website action uses `toUri()`. The three explicit preference editor calls must retain checked `commit()` results. The unit-returning extension cannot replace them | Verify URI site and verify commit-failure coverage after any persistence change |
| Eight dependency update advisories, W-009 | Lifecycle, Compose BOM and CameraX advisories are dependency metadata. Review compatibility before upgrading pins | Both flavor lint reports and dependency resolution |
| Android API macro redefinition, W-008 | The OpenSSL Configure invocation must keep the API used by its NDK compiler selection. Removing the macro option alone risks picking the highest API. The local OpenSSL configuration patch separates the API selector and checks the generated compiler settings | Both ABI archive builds and API 29 link/runtime evidence |
| Runner and dependency warnings, W-005/W-006 | Node action deprecation, SDK XML version skew, emulator host warnings and vendored C++/C builds are distinct from app adapter and Kotlin diagnostics | Upstream updates and separate source attribution |

Older compiler warnings were also compared with current call sites. This
includes deprecated Activity result overrides, old Koin preview setup,
redundant nullable syntax in Send/NodeSettings/BackupHelper and a redundant
conversion in SeedSettings. Their past appearance does not imply reproduction
in the current checkout. The latest captured Kotlin source warnings came from
the five NetworkSettings call sites.

## Warning policy

- Project-owned Kotlin, C++, JNI, Gradle, and resource warnings must be treated as defects:
  find the root cause, search for the same pattern, fix it, and add a regression check.
- Do not hide warnings with `@Suppress`, casts, disabled diagnostics, `continue-on-error`,
  `|| true`, or removed checks unless the suppression is narrow and documented.
- External dependencies may require upstream fixes; do not impose project `-Werror` on
  vendored libraries indiscriminately.
- A build with no tests is not a successful test run. A test run with sanitizer errors is
  not successful even if the process exits zero.

## Patterns reviewed after CI-003 through CI-013

| Pattern | Search | Result |
| --- | --- | --- |
| Android string escaping | All `values*/` XML for `\u`, backslashes, unescaped apostrophes | One unescaped apostrophe found and fixed; no invalid escapes |
| Coroutine imports | All `CancellationException`, `NonCancellable`, `Job`, `withContext`, `Dispatchers` users | Imports present after the fix |
| Coroutine cancellation | Every `catch (e: Exception)` in `app/src/main/java` | Four suspend/`launch` paths swallowed cancellation; fixed |
| KTX preference edits | Every `edit(commit = true)` file | All import `androidx.core.content.edit` |
| Nullable preference values | `proxyHost`, node/LWS hosts, `getString` call sites | Manual proxy branch fixed; `AnonWalletHandler` normalizes host/username/password with `orEmpty()` before non-null helper calls |
| Koin `inject` imports | Every `by inject(...)` call site | `ReviewTransaction` now imports `org.koin.java.KoinJavaComponent.inject`; other ViewModels/services were already covered |
| Refactor-local removal | `wallet`/`address` references after moving loads to IO | `SignVerify` uses separate `primaryAddress` state instead of the removed local |
| Native singleton in model construction | `Node()` and other JVM test fixtures | CI-034: empty `Node()` now uses `AnonConfig.getNetworkType()`, not `WalletManager`/`libanonero` |
| Nullable formatter output | Every `Formats.convertNumber` call | Both call sites have a non-null fallback |
| Nullable model collections | `TransactionInfo.transfers` consumers | Detail screen uses `orEmpty()` |
| ViewModel properties in `launch` | `this.pendingTransaction` / `this.unsignedTransaction` | Qualified with the ViewModel label |
| JNI string handling | Every `GetStringUTFChars` and Kotlin `String?` external | Converted to `ScopedUtfChars` with null checks |
| JNI raw string cleanup | Remaining array/`ArrayList` readers | Converted to `ScopedUtfChars` + `ScopedLocalRef`; no raw release pair remains |
| Main-thread wallet reads | `WalletManager.instance` call sites in `ui/` | `HomeGraph` uses cached `WalletState.walletStatus`; sign/seed/Send/Settings paths moved to IO |
| Backup node input | Restored `NodeBackup`/`Node` fields | `BackupNodePolicy` rejects schemes, credentials, paths, embedded ports, bad IPv6, and invalid ports |
| New coroutine cancellation | `catch (Exception)` in changed UI paths | `CancellationException` is rethrown before generic handling |
| Native error logging | `LOGE`/`LOGD` with `errorString()` | Seed/store/login/sign paths log the operation only, not wallet2 text |
| JNI handle lifetime | Every `getHandle` call and `closeJ` | Handle invalidated on close; remaining exports guard `nullptr` |
| JNI object factories | Every `GetMethodID`/`NewObject` in the adapter | Checked; failures return `nullptr` instead of aborting |
| Restore-height tests | Tests vs `MAX_RESTORE_HEIGHT` | Stale assertion corrected |
| Resource parity/placeholders | `python3 scripts/check-i18n.py` | Passes |
| JNI symbol names | `python3 scripts/check-jni-contract.py` | Exact `Java_package_Class_method` matching; signatures/linkage still require build |
| Workflow syntax | `actionlint` (including shellcheck), `yamllint` with relaxed rules | Pass |
| Workflow security | `zizmor` | 0 findings; one inline `self-repository` ignore because actionlint 1.x lacks `$/` |
| Workflow shell | Every embedded `run:` block parsed with `bash -n` | Pass |
| Formatting/lint | `ktlint`, `clang-format`, `ruff`, `shfmt`, `shellcheck` | Pass locally; no CI gate yet |
| Instrumented health | boot/package/activity gate and crash diagnostics | Validated on `43122308945`: gate and diagnostics fired; test-APK prebuild fix pending |

## Build-system gaps

### Kotlin/Android

- The JVM job compiles both `anonDebug` and `neroDebug`; it is the first gate that exposed
  CI-003 through CI-009.
- `app/build.gradle.kts` uses AGP 9.5.1, Kotlin 2.4.21, compileSdk 37.2, NDK 30.0.16248370,
  CMake 4.1.2, and Gradle 9.9.0. Run `43122308945` compiled both flavors, passed JVM tests
  and Android Lint, and assembled/linked the APKs with these pins.
- Android Lint runs for both flavors. Older zero-warning reports are specific to their
  runs and dependency metadata. W-009 describes the latest reports.
- No dependency lockfile or verification metadata exists.

### C++/JNI

- `app/CMakeLists.txt` enables `-Wall -Wextra -Wpedantic -Werror` for the `anonero` target
  and marks external include trees as SYSTEM. No `anonero.cpp`/`anonero.h` warning line
  appears in the `43122308945` job log. The latest ARM build and link jobs also passed
  with the adapter's `-Werror` configuration. Investigate any recurrence at the call site
  or compiler configuration.
- The JNI contract script checks exact class/method names and four reviewed model
  constructor contracts. Native linkage and remaining export signatures require
  build and device evidence.
- The native compile and link run in CI: the archives build and the adapter links. The
  patch series is present and applies to the pinned commit.
- `0008-enforce-tls-for-https-daemon-address.patch` forces epee SSL support for
  `https://` endpoints. Without it, wallet2's default autodetect retries without TLS after
  a failed handshake, which would silently expose a clearnet LWS view key. The patch needs
  a native CI run and a device test against a TLS-only endpoint; a successful patch
  application is not proof of TLS behavior.
- No AddressSanitizer, UndefinedBehaviorSanitizer, ThreadSanitizer, or LeakSanitizer run
  exists.

### Instrumented and emulator coverage

- The instrumented job is skipped whenever native fails; native now passes.
- A separate `assemble` job links the JNI library and checks the API 29 floor even when
  instrumented tests are skipped; it passes in current runs.
- The API 35 smoke test passed for both flavors on `42874760135` and `43528256395`, but
  the KVM-less emulator lost `system_server` on `43108046671` and `43122308945`, and on
  `43521054641` it lost the `input`/`overlay` services immediately after boot. The job is
  not stable on hosted runners; a KVM runner or device farm is required.
- Lock/unlock, background sync, decoy, duress, wipe, Tor, LWS, and JNI callbacks still
  have no device coverage.
- The emulator matrix is API 35 x86_64 only; API 29 compile/manifest checks are separated
  in the assemble step. No ARM64 hardware run exists.
- The package-name smoke test is joined by diagnostics isolation, transport
  persistence and LWS profile isolation tests. Those checks do not exercise
  live wallet, Tor or LWS network behavior.

### Release and signing

- Release has no recorded run. Its build, artifact verification, isolated signing,
  and publication therefore remain unverified.
- GitHub's read-only environment API lists `android-anon-release`,
  `android-nero-release` and the publication-only `release` environment. Each
  requires reviewers and restricts deployments to `main`. The signing
  environments expose the expected certificate variable names, with the workflow
  rejecting missing or mismatched values. This audit did not retrieve secret values.
- The release build checks APK counts, version, package IDs, ABI/ELF alignment and
  debuggability. The signers verify aligned inputs, pin independently by flavor,
  and verify each signed APK. Both flavor sets and their checksums are required
  before publication. The APK check expects six distinct APKs and two canonical
  universal copies. See [Android release signing](../android-release-signing.md).
- Release lint and mandatory instrumentation before publication are defined in
  source. Neither has been exercised by the Release workflow.
- The read-only repository API reports `main` unprotected and permits all Actions
  without enforcing full SHA pinning. The workflow files themselves use pinned
  actions. The reviewed tree has no `CODEOWNERS`.

## Preventive fixes from the post-format audit

These issues were found by static review after the formatting commits; none had a CI run
of its own.

- **Node store corruption was fail-open.** `NodeListSerializer.readFrom` caught every
  exception and returned `emptyList()`. A corrupt `nodes_store` therefore looked empty, and
  the next `updateData` replaced the user's saved nodes. It now treats an empty file as the
  default, throws `CorruptionException` for unreadable content, keeps the file untouched,
  and returns `false` from add/remove so the settings screen can report
  `node_storage_unavailable`. `getAll()` (used by backup export) surfaces corruption
  instead of producing an archive without nodes. `LogsDataStore` deliberately keeps the
  empty fallback because logs are capped, cleared on unlock, and must never break logging.
- **Backup version parsing was fail-open.** `VersionAsIntSerializer` converted any
  unrecognised version encoding to `0`, which selects the legacy PIN key. It now accepts
  integers, numeric strings, and `"3.0"`, and throws `SerializationException` otherwise, so
  a malformed version fails the restore instead of using the wrong key.
- **Airgapped import state was publicly mutable.** `QRScannerVM._currentImportState` and
  `_loaderState` were public `var`s; the import state machine is now private and only the
  read-only `loaderState` is exposed.
- **Onboarding accepted an invalid node URL.** `NodeSetup` marked the field as invalid but
  still persisted a null host and advanced to the next screen. It now returns before
  persisting or navigating, and the previously empty catch logs a redacted warning.
- **Naming rules were disabled without a remaining reason.** `property-naming` is enabled
  again after renaming mutable caches/constants and converting fixed `var` log levels to
  `const val`. `backing-property-naming` stays disabled because the codebase uses `_` for
  private state without a public counterpart; the comment in `.editorconfig` now says so.
- **Recursive clang-format could reformat Monero.** `external-libs/.clang-format` now sets
  `DisableFormat: true`, and the root configuration uses `Standard: c++17` and keeps the
  `CoinsInfo` constructor descriptor on one greppable line.

## Regression checks added or prepared

1. `scripts/check-i18n.py` now rejects unescaped apostrophes and invalid backslash escapes
   in Android string resources (passes in CI).
2. Kotlin import/static scan for the CI-004/CI-006/CI-008/CI-009 patterns (prepared in the
   audit; CI compilation is authoritative).
3. Android Lint job with HTML/XML report uploads and no `continue-on-error` (0 errors and
   0 warnings on historical run `43521054641`; see W-009 for latest counts).
4. Project-owned C++ warning flags (`-Wall -Wextra -Wpedantic -Werror`) with system include
   trees. Native archives build and `43521054641` has no adapter warning under `-Werror`.
5. JNI exact class/method symbol parity (implemented); signature and constructor
   descriptors still require a real build/link check.
6. Gradle problems report paths in unit-test artifacts (unit job passes).
7. Assemble/link job independent of `run_instrumented` (passes on `42847493558`).
8. Artifact names include `run_attempt`, so a rerun cannot collide (implemented).
9. Release tags are resolved through `refs/tags/` with an existence check (implemented).
10. Sanitizer/fuzz jobs after the native build is reproducible; platform limits must be
    documented.
11. Verify both configured flavor pins with an authorized Release build (pending runtime).
12. `BackupVersionTest` fixes the accepted version encodings and rejects malformed ones
    (JVM unit test; CI authoritative).
13. `NodeDataStoreTest` fixes the empty-store default and the fail-closed
    `CorruptionException` path (JVM unit test; CI authoritative).
14. `ktlint` now enforces `property-naming`; `.editorconfig` documents the single remaining
    naming exception.

## Repository configuration still required

Protect `main` and workflow changes with rulesets and appropriate review ownership.
Restrict allowed actions and enforce full-length SHA pinning in repository settings.
These are distinct from the configured signing and publication environments.

## Evidence levels

- **Verified static:** resource scan, import scan, handle/string/object-factory scans,
  workflow syntax/shell/security checks, `git diff --check`.
- **Validated in CI:** JVM unit tests, lint with zero errors, JNI symbol parity,
  localization, architecture policy, native archives and debug APK linking. The
  latest successful job logs and XML include Kotlin/lint warnings above.
- **Validated in `43528256395`:** all jobs, including `-Werror`, the
  serialization/BouncyCastle update, lint 0/0, exact JNI parity, localization, native
  archives, assemble/link, 62 JVM tests per flavor, and one instrumented smoke test per
  flavor with zero failures.
- **Not stable:** the API 35 smoke test passed on `42874760135` and `43528256395` but
  failed on `43108046671`, `43122308945`, and `43521054641` when the KVM-less guest lost
  `system_server` or the post-boot adb services.
- **Unverified:** sanitizers, release signing and publication, broader wallet/Tor/LWS
  instrumented coverage.
- **Pending:** authorized Release execution, branch protection, and reproducible
  instrumented runs with usable KVM or a device farm.

### INST-001 — Inconsistent instrumentation verdict and overwritten retry evidence

The [API 35 job](https://github.com/daviduhden/ANONERO/actions/runs/45706621403/job/139546185283)
at `b09f9dc4953465a294521db8fda89a27cb66198d` emitted aggregate
`testsuites` XML with nested suites and one logcat per case. Ten cases ran per
flavor. This successful job supplies the report format. It had no observed
sanitizer failure. Static review found that the retry classifier accepted
only a single suite, while two independent final checks trusted summary counts.
An error element with zero reported errors or a sanitizer report in guest output
could escape both final checks. A retry could overwrite the first XML.

Both final gates now validate the same XML hierarchy, actual testcase and issue
counts, both flavor exit codes and existing per-case logcat. A sanitizer
diagnostic in captured XML or guest logs prevents success or retry. The original
attempt's XML, exit codes and per-test logs are archived before retry. Python
regressions cover aggregate XML, inconsistent counters, missing cases and
sanitizer output. The checker accepted the archived XML and logs for this SHA.
Execution of the modified runner and regression suite still requires manual CI.
Diagnostics outside the captured buffers and uninstrumented code remain uncovered.

The JVM job also requires passing JUnit XML for each Debug flavor. On the
archived `b09f9dc` results the shared parser counted 143 cases in 27 suites
per flavor. JVM reports do not include Android's guest exit-code sidecars.
