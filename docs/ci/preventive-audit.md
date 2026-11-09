# Preventive compilation and warning audit

## Status

This is a static audit of the current tree. It does not replace a successful build. The
last available run (`42686398569`) failed before producing a clean APK; native and
instrumented evidence remains blocked by the missing Monero patch series/submodule.

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
| JNI symbol names | `python3 scripts/check-jni-contract.py` | Passes names; signatures/linkage require build |
| Workflow syntax | `actionlint` (including shellcheck), `yamllint` with relaxed rules | Pass |
| Workflow security | `zizmor` | One known low self-repository finding |
| Workflow shell | Every embedded `run:` block parsed with `bash -n` | Pass |

## Build-system gaps

### Kotlin/Android

- The JVM job compiles both `anonDebug` and `neroDebug`; it is the first gate that exposed
  CI-003 through CI-009.
- `app/build.gradle.kts` uses AGP 9.5.1, Kotlin 2.4.21, compileSdk 37.2, NDK 30.0.16248370,
  CMake 4.1.2, and Gradle 9.9.0. These pins have not been validated by a successful run.
- An Android Lint job has been added for both flavors, but it has not run yet. Lint
  warnings, manifest/resource issues, and Compose diagnostics remain unverified.
- No dependency lockfile or verification metadata exists.

### C++/JNI

- `app/CMakeLists.txt` now enables `-Wall -Wextra -Wpedantic` for the `anonero` target and
  marks external include trees as SYSTEM. No successful native compile exists, so the
  resulting warnings and `-Werror` readiness are unverified.
- The JNI contract script checks symbol names only; it cannot validate JNI signatures,
  constructor descriptors, or link-time symbol availability.
- Native compilation is blocked before C++ compilation by the missing patch series.
- No AddressSanitizer, UndefinedBehaviorSanitizer, ThreadSanitizer, or LeakSanitizer run
  exists.

### Instrumented and emulator coverage

- The instrumented job is skipped whenever native fails.
- A separate `assemble` job now links the JNI library and checks the API 29 floor even when
  instrumented tests are skipped, but it has never run.
- No successful instrumented run is available, so lock/unlock, background sync, decoy,
  duress, wipe, Tor, LWS, and JNI callbacks are unverified on device.
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

## Regression checks added or prepared

1. `scripts/check-i18n.py` now rejects unescaped apostrophes and invalid backslash escapes
   in Android string resources (implemented; CI pending).
2. Kotlin import/static scan for the CI-004/CI-006/CI-008/CI-009 patterns (prepared in the
   audit; CI compilation is authoritative).
3. Android Lint job with HTML/XML report uploads and no `continue-on-error` (implemented;
   CI pending).
4. Project-owned C++ warning flags (`-Wall -Wextra -Wpedantic`) with system include trees
   (implemented; native CI blocked). `-Werror` is deferred until a reproducible native run
   is warning-free.
5. JNI signature/constructor parity checks where derivable statically (pending).
6. Gradle problems report paths in unit-test artifacts (implemented; CI pending).
7. Assemble/link job independent of `run_instrumented` (implemented; CI pending).
8. Artifact names include `run_attempt`, so a rerun cannot collide (implemented).
9. Release tags are resolved through `refs/tags/` with an existence check (implemented).
10. Sanitizer/fuzz jobs after the native build is reproducible; platform limits must be
    documented.
11. Pin the release certificate fingerprint and verify all six APKs (pending).

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
- **Implemented, CI pending:** the CI-003 through CI-013 fixes and the new workflow jobs.
- **Unverified:** Kotlin compilation, C++ compilation/link, unit tests, lint, instrumented
  tests, sanitizers, release signing.
- **Blocked:** native build inputs, release environment, branch protection, certificate
  pinning, instrumented/sanitizer execution.
