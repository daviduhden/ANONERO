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
  include trees as SYSTEM so third-party headers do not pollute project warnings. Native
  compilation and `-Werror` remain pending a reproducible native build.
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
- Restored the Monero wallet-API patch series: five patches adapted from the `monero_c`
  lineage, rebased onto the pinned `85f036ba` commit, checked in with provenance and the
  LGPL-3.0 licence. They apply with `git apply --index --3way`, and the `bc-ur`,
  `polyseed`, and `utf8proc` submodules resolve at pinned commits. Native compile remains
  pending; runs `42707311457` and `42708047372` applied every patch but stopped at a
  Debian snapshot 503 in the container base image.

## Pending validation

- **Native build:** the wallet-API patch series is checked in and applies to the pinned
  commit, but the local Monero submodule is uninitialized and the archives have not been
  compiled. The manual `native` job is the authority. CMake/NDK/Boost/zlib changes are
  statically reviewed only.
- **Android build:** AGP 9.5.1, Gradle 9.9.0, API 37.2, NDK 30, and CMake 4.1.2 have not
  been built or instrumented locally.
- **Runtime behavior:** lock/unlock, background sync, decoy switching, Tor transitions,
  LWS reconnect, notifications, and wipe need CI/device validation.
- **Cryptography:** no independent side-channel or Keystore audit has been performed.

## Blocked

- Native compile of the restored patch series (local submodule uninitialized; CI required).
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
