# Development progress

Baseline: `ed8b097`, followed by the hardening commits through `52d67d4`. This file
records progress; it is not a release note and does not claim CI validation.

## Completed in the current cycle

### Funds and lifecycle correctness

- Failed wallet creation, seed restore, and view-only creation now remove the partial MAIN
  profile, wrapped secret, and credentials. A later attempt is not blocked by
  `RestoreTarget.requireEmpty`.
- `WalletManager` rejects a zero native handle instead of constructing a wrapper that would
  dereference null on `status`/`store`; failed creation/status/password setup does not
  return a managed half-valid wallet.
- Opening a wallet closes any previously managed wallet first, and a failed open closes the
  invalid wallet before reporting the error.
- A restored wallet that fails to bind is removed as MAIN; the decoy profile is preserved.

### Transactions and backups

- `ReviewTransactionViewModel` rejects a missing pending transaction, posts success only
  after the actual send/submit, and does not turn a post-broadcast refresh failure into an
  error that could invite a second payment.
- Backup creation clears only transient backup/airgapped artifacts instead of the entire
  cache, preserving the live Tor cache and cache log.
- Restoring a v1 backup now adds the fallback node to the saved-node list. Backup node
  settings are persisted only after the restored wallet binds; a failed restore does not
  leave a stale node active.
- NERO view-only restore honors the payload restore height, rejects negative heights, and
  uses the shared preference key constant. Backup restore rejects a negative restore height
  before writing credentials.

### JNI and memory safety

- Wallet creation/open/recovery/init check null Java strings and null wallet pointers before
  use.
- `java2cpp_string_array` fails closed on an unreadable element; `java2cpp_set` skips null
  elements; `getElement` initializes and checks its result.
- JNI creation/open/recovery/init paths use `ScopedUtfChars` instead of manual release pairs.
- The Tor SOCKS endpoint is volatile, and restart clears the cached endpoint before the new
  listener is published, avoiding stale proxy reuse.

### CI and release controls

- Both workflows are now `workflow_dispatch` only. `test.yml` retains `workflow_call` only
  for the manual release workflow, and both workflows have an owner `authorize` gate.
- Release is built from a required `tag` input; publishing is a separate `publish` boolean.
- Added a Gradle wrapper preflight and a native submodule/patch-series preflight with
  explicit errors instead of the historical missing-jar/patch failures.
- Historical failures were investigated with `gh run view --log-failed`: missing
  `gradle/wrapper/gradle-wrapper.jar` and a missing `external-libs/patches/*.patch` series.
- `actionlint`, `yamllint`, and `zizmor` were run statically; the only remaining zizmor
  finding is the low-risk `./` self-repository recommendation, kept for actionlint
  compatibility.

### Tests and documentation

- Added JVM coverage for `SigningSummary` parsing (single destination, change, multiple
  destinations, payment id, invalid totals, malformed input).
- Updated the architecture, security, features, development, build, and repository-state
  documentation for the current implementation.
- Added the dependency audit record and the agent instruction/skill set.

### Security-hardening cycle (committed, static review)

- Exact-amount path: the normal send flow and URI amounts no longer pass through `Double`
  or wallet2's forgiving parser. `SendScreenRoute.amount` is atomic `Long`,
  `MoneroAmount.parseExact` is used for construction, and payment/review values use an
  exact 12-decimal formatter.
- The seed is no longer a navigation argument; `OnboardSeedScreen` is an empty route and
  the words come from the ViewModel on `Dispatchers.IO`. The seed-screen preview uses
  obvious placeholders.
- PIN, RPC-credential, and signature-confirmation dialogs now set
  `SecureFlagPolicy.SecureOn`.
- The signed-transaction broadcast path in NERO now fails visibly, handles
  `CancellationException`, and no longer ignores `submitTransaction`; the review broadcast
  has an atomic double-tap guard.
- The create/restore/backup flows start the wallet service and select the profile before
  leaving onboarding; restored wallets stay managed after a successful bind.
- `WalletKey` recovery tolerates a missing cache snapshot and never copies a partial pair;
  Keystore alias creation/removal is serialized and removal checks `commit()`.
- `WalletProfileStore.removeProfile` returns failure instead of silently reporting success;
  wipe resolves the wallet2 password, requires `stopBackgroundSync` when native sync is
  active, and clears node/proxy/LWS metadata and the clipboard.
- Backup creation keeps the plaintext ZIP in `tmp_backup` (purged at startup), the archive
  allowlist rejects migration sidecars, extraction has per-entry/total size limits, and
  encrypted headers/IVs are length-checked.
- Restored backups are checked against the wallet's address and watch-only mode; restore
  height is bounded; PIN iteration counts, salt, and key lengths are bounded.
- Tor SOCKS is a nullable `StateFlow`; stop/restart emit `null`, late listener events are
  ignored when Tor is not wanted, and the wallet pauses on loss instead of reusing a stale
  port. Tor endpoints are no longer persisted as a manual proxy.
- LWS reconnect re-applies `setTrustedDaemon(false)` after a successful login; view-only
  restore without a node is treated as offline and never initialized with network access.
- JNI/C++: Polyseed generation fails closed; transaction creation validates nulls, ranges,
  and negative amounts and catches C++ exceptions; listener callbacks no longer hold the
  mutex during Kotlin calls; pending/unsigned handles are invalidated and unsigned
  transactions are disposed; JNI class lookup and `GetMethodID` failures fail closed.
- Additional JVM regression tests cover exact amount formatting/round-trip, URI amount
  parsing, next-subaddress index, bounded PIN parameters, and the existing signing summary.

### Native architecture consolidation

- Audited the repository for alternate native source files, manifests, lockfiles,
  toolchains, Gradle/NDK integration, and workflow steps. No alternate project-owned
  native runtime or build path exists in this checkout.
- Removed obsolete native-runtime wording from `AGENTS.md`, repository instructions,
  skills, README, and architecture/development/build documents.
- Replaced the old negative guard with a positive Kotlin/C++ architecture check that
  rejects project-owned `.java` sources.
- The Monero mnemonic dictionary is unchanged; its entries are dictionary data, not
  build inputs.
- `kmp-tor` remains: it is Kotlin Multiplatform, not a separate native runtime, and is
  the Tor transport integration.

### CI compile-failure and warning cycle

- Audited all three accessible Actions runs. Run `42686398569` (manual, `52d67d4`) exposed
  resource escaping, missing imports, nullable values, a missing SharedPreferences KTX
  import, and ViewModel property resolution inside a coroutine. All are fixed; CI is
  pending.
- Added `docs/ci/` with run-level failure records, warning inventory, root causes,
  equivalent-pattern searches, and pending evidence.
- Extended `scripts/check-i18n.py` to reject unescaped apostrophes and invalid Android
  string escapes, preventing a recurrence of the AAPT failure.
- Added an Android Lint job for both flavors with HTML/XML report uploads, and included
  Gradle problems reports in unit-test artifact uploads.
- Enabled `-Wall -Wextra -Wpedantic` for the project-owned C++ target and marked external
  include trees as SYSTEM so third-party headers do not pollute project warnings. No
  `anonero.cpp`/`anonero.h` warning line appears in the `43122308945` job log; `-Werror` is
  now enabled and awaits the next native/assemble run.
- Added the permanent policy that every compile error, warning, test failure, crash, JNI
  or sanitizer diagnostic requires root-cause analysis, a global pattern search, a
  regression check, and a `docs/ci/` entry.
- Corrected a stale `SeedRestoreInputTest` that contradicted `MAX_RESTORE_HEIGHT`; it would
  have failed on the first run that reached test execution.
- Added cancellation rethrows to `Send`, `PayToMany`, and `ReviewTransaction`, and reset
  the broadcast state when cancellation happens before submission.
- Hardened the JNI adapter further: invalidated handles on close, fixed a `return value`
  in a `void` export, made coin-control key images fail closed, checked
  `GetMethodID`/`NewObject` in object factories, and guarded every wallet/coins/history
  handle dereference.
- Added an `assemble` job that links `libanonero.so` even when instrumented tests are
  skipped, plus release lint, unsigned `SHA256SUMS` verification, 16 KB zipalignment
  checks, exact checksum/APK set equality, `run_attempt` artifact names, and unambiguous
  `refs/tags/` resolution.
- Manual CI run `42700250719` confirms: JVM unit tests pass, Android Lint reports zero
  errors and zero warnings for both flavors, Kotlin compilation emits zero warnings, and
  JNI/i18n/policy gates pass.
- Restored the Monero wallet-API patch series: seven patches adapted from the `monero_c`
  lineage, rebased onto the pinned `85f036ba` commit, checked in with provenance and the
  LGPL-3.0 licence. They apply with `git apply --index --3way`, and the `bc-ur`,
  `polyseed`, and `utf8proc` submodules resolve at pinned commits. Runs `42743643959` and
  later build the native archives and link the adapter; `assemble` produces both debug
  APKs. Run `42874760135` is fully green: native archives, assemble/link, and the API 35
  instrumented smoke tests pass for both anon and nero, with the XML validation confirming
  one test and zero failures per flavor. A prior run had exposed a masked NERO attach
  failure; the runner now fails on any failed XML.
- Adopted ktlint, clang-format, Ruff, shfmt, shellcheck, yamllint, zizmor, and actionlint
  with checked-in configuration, and applied the formatters across the Kotlin, C++, Python,
  and shell sources. All linters pass locally. The formatting commits `a487cf5`..`32d8c1a`
  have no CI run yet.
- Run `43108046671` (`02b97b8`) passed every job except instrumented: the KVM-less
  emulator lost `system_server` during install and produced `tests=0` XML reports. Added
  a guest health gate, crash diagnostics, stricter XML/exit-code validation, disk cleanup,
  and returned to the only configuration with a green run (2048M).
- Run `43122308945` (`381d8ab`) validated the post-format audit commits: authorization,
  unit tests, lint, localization, JNI parity, native archives, and assemble all pass.
  Instrumented still failed: the new diagnostics caught `system_server` aborting with
  SIGABRT while Gradle compiled and dexed the AndroidTest APKs; guest memory (1.9 GB
  available) and disk (5 GB free) were healthy. The instrumented job now builds the app and
  test APKs before the emulator starts and limits the connected Gradle to two workers.
  This fix has no CI run yet.
- The same run surfaced two `NewerVersionAvailable` lint advisories per flavor.
  kotlinx.serialization is updated to 1.13.0 (Kotlin 2.4.x, reverts the 1.12 exception
  wrapping) and Bouncy Castle to 1.87 (security release, twelve CVEs). ANONERO uses only
  the stable digest/PBKDF2/Base64 APIs affected neither by the 1.86-incompatible areas nor
  by the serializer change. CI must still resolve and compile the new versions.
- The post-format static audit found and fixed two fail-open data paths: a corrupt
  `nodes_store` no longer reads as an empty list (writes fail closed and the settings screen
  reports the error), and a malformed backup version now fails the restore instead of
  selecting the legacy PIN key. `QRScannerVM` no longer exposes its import state as a
  public `var`, `property-naming` is enforced by ktlint again, and
  `external-libs/.clang-format` prevents recursive formatting of vendored Monero.
  Onboarding now refuses an invalid node URL instead of persisting it and advancing, and a
  dead exception-swallowing block in proxy settings was removed. New JVM regressions cover
  both serializers. `check-jni-contract.py` now matches the exact
  `Java_package_Class_method` export instead of a method-name suffix, so a
  `Wallet_getStatusJ`/`PendingTransaction_getStatusJ` class mismatch cannot pass.

## Pending validation

- **Native build:** the archives build and the adapter links in CI. No
  `anonero.cpp`/`anonero.h` warning line appears in the `43122308945` job log; the newly
  enabled `-Werror` needs the next run.
- **Android build:** AGP 9.5.1, Gradle 9.9.0, API 37.2, NDK 30, and CMake 4.1.2 build in
  CI. The API 35 smoke tests passed once (`42874760135`) but are not stable on hosted
  runners without KVM.
- **Runtime behavior:** lock/unlock, background sync, decoy switching, Tor transitions,
  LWS reconnect, notifications, and wipe still need dedicated device tests.
- **Cryptography:** no independent side-channel or Keystore audit has been performed.

## Blocked

- Deterministic instrumented execution requires a self-hosted KVM runner or a device
  farm; the hosted runner uses software x86_64 emulation.
- Broader instrumented wallet, Tor, LWS, lock/decoy, and wipe coverage still requires
  dedicated device tests; the current emulator job runs only the package smoke test.
- OpenSSL 4.x, Polyseed/utf8proc updates, Debian base/snapshot, and Gradle lockfile
  generation have technical or validation blockers; see
  [`docs/architecture/dependencies.md`](docs/architecture/dependencies.md).

## Next priorities

See [`ROADMAP.md`](ROADMAP.md) and [`TODO.md`](TODO.md). The immediate order is:

1. Restore the native inputs and obtain a green `native` CI run.
2. Validate the Android/toolchain update in CI.
3. Add lifecycle and backup tests that exercise failure cleanup.
4. Harden remaining JNI string/array paths and document handle lifetime limits.
5. Validate Tor/LWS, lock/decoy, and backup flows on a device.

Commits through `52d67d4` were pushed. The CI compile-failure fixes, workflow gates, and
documentation were committed afterwards.
