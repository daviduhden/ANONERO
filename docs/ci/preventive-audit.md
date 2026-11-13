# Preventive compilation and warning audit

## Status

This is a static audit followed by manual CI validation. Run `42874760135` passed every
job, including one API 35 smoke test per flavor. Run `43122308945` (`381d8ab`) passed every
code, native, and assemble gate, including the formatting/lint commits and the new JVM
regressions. Lint reported two dependency-update advisories (W-004), addressed by the
pending serialization/BouncyCastle bump. Its instrumented job still failed: the diagnostics
caught `system_server` aborting while Gradle dexed the test APKs, with guest memory and disk
healthy. The job now prebuilds app and test APKs before boot and limits connected Gradle to
two workers. Deterministic instrumented execution still requires a KVM runner or device
farm.

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
| Nullable preference values | `proxyHost`, node/LWS hosts, `getString` call sites | Manual proxy branch fixed; remaining reads have defaults |
| Nullable formatter output | Every `Formats.convertNumber` call | Both call sites have a non-null fallback |
| Nullable model collections | `TransactionInfo.transfers` consumers | Detail screen uses `orEmpty()` |
| ViewModel properties in `launch` | `this.pendingTransaction` / `this.unsignedTransaction` | Qualified with the ViewModel label |
| JNI string handling | Every `GetStringUTFChars` and Kotlin `String?` external | Converted to `ScopedUtfChars` with null checks |
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
- Android Lint runs for both flavors. It reports zero errors and, after the pending W-004
  version update, no project-code warnings; the only findings were two dependency-update
  advisories.
- No dependency lockfile or verification metadata exists.

### C++/JNI

- `app/CMakeLists.txt` enables `-Wall -Wextra -Wpedantic -Werror` for the `anonero` target
  and marks external include trees as SYSTEM. No `anonero.cpp`/`anonero.h` warning line
  appears in the `43122308945` job log; the `-Werror` flag needs the next CI run to
  validate. If it surfaces a warning, revert only `-Werror`, record the offending API, and
  do not suppress it globally.
- The JNI contract script checks exact class/method names; it cannot validate JNI
  signatures, constructor descriptors, or link-time symbol availability.
- The native compile and link run in CI: the archives build and the adapter links. The
  patch series is present and applies to the pinned commit.
- No AddressSanitizer, UndefinedBehaviorSanitizer, ThreadSanitizer, or LeakSanitizer run
  exists.

### Instrumented and emulator coverage

- The instrumented job is skipped whenever native fails; native now passes.
- A separate `assemble` job links the JNI library and checks the API 29 floor even when
  instrumented tests are skipped; it passes in current runs.
- The API 35 smoke test passed for both flavors on `42874760135`, but the KVM-less
  emulator lost `system_server` on `43108046671` and `43122308945`, producing zero-test
  reports. The job is not stable on hosted runners; a KVM runner or device farm is
  required.
- Lock/unlock, background sync, decoy, duress, wipe, Tor, LWS, and JNI callbacks still
  have no device coverage.
- The emulator matrix is API 35 x86_64 only; API 29 compile/manifest checks are separated
  in the assemble step. No ARM64 hardware run exists.
- `app/src/androidTest/` contains a single package-name smoke test; wallet, Tor, and LWS
  behavior has no automated instrumented coverage.

### Release and signing

- No release run exists.
- `release` environment does not exist; signing secrets and required reviewers are not
  configured.
- `main` is unprotected, there is no `CODEOWNERS`, and Actions still allows any action
  because SHA pinning is not enforced repository-wide.
- The release certificate identity is not pinned; signature verification currently proves
  validity, not the expected signer.
- Unsigned APKs now carry `SHA256SUMS`, the signer verifies it before signing, 16 KB
  zipalignment is checked, and the signed/published set must match `SHA256SUMS` exactly.
  None of this has run.
- Lint now covers release variants in the release build job; unverified.
- A release publish now requires `run_instrumented == true`.

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
3. Android Lint job with HTML/XML report uploads and no `continue-on-error` (zero errors;
   the W-004 dependency advisories are cleared by the pending version update).
4. Project-owned C++ warning flags (`-Wall -Wextra -Wpedantic -Werror`) with system include
   trees. Native archives build and the adapter compiled without warnings in
   `43122308945`; the next run validates the new `-Werror`.
5. JNI exact class/method symbol parity (implemented); signature and constructor
   descriptors still require a real build/link check.
6. Gradle problems report paths in unit-test artifacts (unit job passes).
7. Assemble/link job independent of `run_instrumented` (passes on `42847493558`).
8. Artifact names include `run_attempt`, so a rerun cannot collide (implemented).
9. Release tags are resolved through `refs/tags/` with an existence check (implemented).
10. Sanitizer/fuzz jobs after the native build is reproducible; platform limits must be
    documented.
11. Pin the release certificate fingerprint and verify all six APKs (pending).
12. `BackupVersionTest` fixes the accepted version encodings and rejects malformed ones
    (JVM unit test; CI authoritative).
13. `NodeDataStoreTest` fixes the empty-store default and the fail-closed
    `CorruptionException` path (JVM unit test; CI authoritative).
14. `ktlint` now enforces `property-naming`; `.editorconfig` documents the single remaining
    naming exception.

## Repository configuration still required

These are settings, not workflow code, and cannot be supplied by this audit:

1. Create the `release` environment with the owner as required reviewer and the four signing
   secrets.
2. Protect `main` and `.github/workflows/` with rulesets and `CODEOWNERS`.
3. Restrict allowed actions and require full-length commit SHA pinning.
4. Store the expected release certificate SHA-256 in the repository and compare it in the
   `sign` and `publish` jobs.

## Evidence levels

- **Verified static:** resource scan, import scan, handle/string/object-factory scans,
  workflow syntax/shell/security checks, `git diff --check`.
- **Validated in CI:** JVM unit tests, Android Lint (0/0), Kotlin compilation (0 warnings),
  JNI symbol parity, localization, architecture policy, native archives, and debug APK
  assembly/link on the pre-formatting commits.
- **Validated in `43122308945`:** the formatting/lint commits and audit fixes compile,
  pass JVM tests, lint, localization, JNI parity, native archives, and assemble/link.
- **Validated once, not stable:** the API 35 smoke test passed on `42874760135` and failed
  on `43108046671` and `43122308945` when the guest lost `system_server`.
- **Unverified:** sanitizers, release signing and publication, broader wallet/Tor/LWS
  instrumented coverage.
- **Blocked:** release environment, branch protection, certificate pinning, deterministic
  instrumented execution without KVM/device farm.
