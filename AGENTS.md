# ANONERO — Agent Instructions

This is the repository-wide contract for human and AI contributors. Read it before
changing code. More specific `AGENTS.md` files in subdirectories add constraints for
their subtree; they never weaken the security rules in this file. Reusable procedures
live in `.agents/skills/*/SKILL.md`; the index is `.agents/SKILLS.md`.

When instructions conflict, apply this order:

1. Security and fund-safety invariants in this file.
2. The closest `AGENTS.md` to the file being changed.
3. The relevant skill.
4. General conventions.

If a requested change cannot satisfy all four, stop and report the conflict instead of
silently weakening a guarantee.

> **Git authorization is mandatory.** Never run `git commit` (including `--amend`),
> `git push` (including force variants), create/publish tags, or use an equivalent
> GitHub CLI/API operation without prior, explicit, unambiguous authorization from the
> human supervisor. A commit authorization is not a push authorization. See §8.1.

## 1. Project

ANONERO is a privacy- and security-focused Monero wallet for Android. It is a
single-application repository with two product flavors:

- `anon` — full wallet (ANON).
- `nero` — view-only wallet (NERO), built with `VIEW_ONLY = true`.

The application is Kotlin + Jetpack Compose. The only project-owned native layer is a
C++ JNI adapter in `app/src/main/cpp/`. Monero itself is consumed as prebuilt static
archives produced by `external-libs/`. There is no project-owned Java.

### Verified repository state (2026-11-09)

This section records facts verified during the instruction audit. Re-verify before
relying on it after a significant change.

- The audit baseline was `ed8b097` (`docs: add Apache-2.0 license headers and normalize
  comment style`). The current hardening cycle adds commits through `52d67d4`; the CI
  compile-failure fixes, workflow gates, and `docs/ci/` inventory were committed afterwards.
- `app/src/main/java` contains Kotlin only. There are no `.java` files.
- The native boundary is the C++ JNI adapter in `app/src/main/cpp/`; there is no
  alternate project-owned native runtime or FFI layer.
- `external-libs/monero` is a git submodule pinned to
  `85f036ba7019cd36f08e8a38a27c37ea7b522618` (README: Monero `v0.18.5.4`).
- `external-libs/patches/` contains the seven wallet-API patches plus `apply.sh` and
  `README.md`. The series is adapted from the `monero_c` Monero patches and applies to
  the pinned commit; see `external-libs/patches/README.md` for provenance and licence.
  The local submodule is still unpopulated, so the native build requires CI or a manual
  `git submodule update --init --recursive` followed by `make -C external-libs patches`.
- `docs/` had no documentation before this audit. It now contains a documentation index
  and the architecture, security, features, development, build, state, and dependency
  documents.
- The CI workflow `test.yml` has a `native-language-policy` job that enforces the
  Kotlin/C++ source layout; the release workflow repeats the same check.

### Implemented in source (not runtime-verified by this audit)

- Wallet create/restore with Polyseed (16 words) and legacy mnemonics (25 words),
  plus seed passphrases.
- Multiple accounts and subaddresses, no subaddress reuse by default.
- Balances, history, notes, transaction details, UTXO list, freeze/thaw, coin control.
- Send, sweep, pay-to-many, exact amount parsing, fee display, transaction review.
- Message signing/verification.
- Key-image and output export/import; unsigned/signed transaction exchange; UR/QR
  airgapped flows.
- Background sync and a foreground wallet service.
- Embedded Tor (`io.matthewnelson.kmp-tor`) with a mandatory SOCKS proxy path.
- Optional Monero Light Wallet Server (LWS) over Tor.
- Android Keystore-bound wallet secret, PBKDF2 PIN gate, legacy PIN migration.
- Encrypted backups: v3 writer, v1/v2 readers.
- Stealth mode, decoy wallet, duress PIN, secure wipe, sensitive clipboard handling.
- English, Spanish (Spain), Russian, and Simplified Chinese resources.
- GitHub Actions test and release pipelines with APK signing and artifact verification.

### Not present or not proven

- No alternate native runtime, FFI layer, or project-owned Java.
- Runtime, device, or cryptographic proof from this audit. Static inspection can find
  defects; it cannot prove correctness or security.
- Any claim that “it works” without CI, logs, or an explicitly cited artifact.

## 2. Architecture

```
Compose screens
  -> ViewModels / screen state
  -> WalletState (StateFlow/SharedFlow)
  -> AnonWalletHandler (open, start, proxy, daemon, wipe)
  -> Wallet / WalletManager (Kotlin)
  -> C++ JNI adapter (app/src/main/cpp/anonero.cpp)
  -> libanonero.so
  -> Monero wallet2 static archives (external-libs/<abi>/monero)

TorService (kmp-tor) -> SOCKS endpoint -> wallet2 proxy
LWS host:port -> wallet2 light-wallet mode -> SOCKS/Tor -> remote LWS
```

Key process facts:

- `WalletManager` is a process-wide singleton that manages exactly one live wallet.
  Decoy creation and backup re-keying use explicitly unmanaged wallets.
- `MoneroHandlerThread` owns wallet callbacks and runs on a thread with a 5 MB stack.
  Wallet refreshes and stores must not block the main thread.
- `WalletState` is the UI-facing state owner. Do not create a second source of truth.
- Tor and wallet state are process-scoped Koin singletons (`AppModule.kt`).

### Code organization

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/io/anonero/` | Android application, services, models, UI, utilities |
| `app/src/main/java/io/anonero/model/` | Wallet2-facing Kotlin models and `external` declarations |
| `app/src/main/java/io/anonero/services/` | Tor, wallet lifecycle, sync thread, state, foreground service |
| `app/src/main/java/io/anonero/ui/` | Compose screens, navigation, theme, components |
| `app/src/main/java/io/anonero/util/` | PIN/Keystore, profiles, backups, formatting, privacy settings |
| `app/src/main/cpp/` | Project-owned C++ JNI adapter |
| `external-libs/` | Pinned Monero and dependency build, patches, static archives |
| `scripts/` | Static CI checks (JNI symbol parity, localization) |
| `.github/workflows/` | Build, test, signing, and release automation |

## 3. Language and dependency policy

- Kotlin is the primary implementation language. Jetpack Compose is the UI toolkit.
- C++ is the only project-owned native language; it is retained for the JNI adapter and
  native build glue. JNI is the only Kotlin/native boundary.
- Do not introduce new Java, another native runtime, or another FFI layer. The
  Kotlin/Compose + C++/JNI split is the permanent architecture.
- Upstream Monero C++ is vendored through the pinned submodule. Do not edit the
  submodule working tree as a shortcut; record changes as patches.
- New third-party dependencies require a concrete need, license review, maintenance
  check, transitive-dependency review, and an entry in `gradle/libs.versions.toml`.
  Do not add a dependency just to avoid a few lines of code.
- Keep the Kotlin plugin version in the root `build.gradle.kts` `buildscript` block in
  sync with `kotlin` in `gradle/libs.versions.toml` (the catalog accessor is not
  available there).

## 4. Security invariants

These are non-negotiable. A change that weakens one requires an explicit security
decision and documentation, not a local workaround.

1. **Funds first.** Prefer refusing an operation to guessing. Fail closed on missing
   keys, missing proxies, failed wallet init, failed login, or unknown transaction
   state.
2. **Private keys and seeds never leave wallet2** except through the explicit
   encrypted-backup or seed-display flows. Never log, persist in plaintext, send, or
   put them in intents, clipboard, notifications, crash messages, navigation arguments,
   saved state, or test fixtures.
3. **The spend key is only present when the wallet is unlocked.** Background sync must
   not expose spend capability. The lock screen must remain above the UI until
   `stopBackgroundSync` succeeds.
4. **A proxy is mandatory; Tor is the default and preferred path.** Wallet traffic must
   use the embedded Tor SOCKS endpoint. The only allowed alternative is an explicitly
   configured manual proxy when the user disables Tor. Do not add a clearnet fallback.
   LWS requires the built-in Tor endpoint, not a manual proxy.
5. **LWS is untrusted.** Keep `setTrustedDaemon(false)` before and after login. Only
   the public address and view key may be sent.
6. **Profile isolation.** Main and decoy wallets have separate files, Keystore secrets,
   PIN hashes, passphrase hashes, and restore heights. Never read, copy, back up, or
   wipe the other profile implicitly.
7. **Encryption stays authenticated.** New backups use the v2/v3 AEAD format. Do not
   write new data with the legacy unauthenticated CBC format. Do not lower KDF
   iteration counts or key sizes. A plaintext backup archive may exist only in the
   transient `tmp_backup` directory and must be purged at startup.
8. **JNI boundaries are hostile.** Validate nulls, lengths, handles, and exceptions.
   Never let a C++ exception cross into Kotlin. Never expose raw pointers to UI code.
9. **Secrets stay out of logs.** `Timber` output is persisted and shareable. Never log
   seeds, passphrases, PINs, wallet passwords, Keystore material, node credentials, or
   full addresses/amounts in sensitive paths.
10. **No silent downgrade.** A failure in Tor, Keystore, backup authentication, or
    transaction review must surface as an error, not as a less private or less safe
    mode.

## 5. Build, test, and evidence policy

- **Do not compile locally.** Do not run `./gradlew`, `make`, `cmake`, `docker`/
  `podman`, `adb`, emulators, or any project build.
- **Do not run tests locally.** Unit and instrumented tests are authoritative only in
  GitHub Actions.
- **Static analysis is allowed and expected.** Reading files, `git` inspection,
  `grep`/`rg`, Python static checks in `scripts/`, XML/TOML/YAML parsing, `bash -n`,
  and `git diff --check` do not execute the application. Use them.
- **CI is manual-only.** Workflows use `workflow_dispatch` (and the guarded
  `workflow_call` used by the manual release workflow). Do not trigger, rerun, or cancel a
  workflow without explicit authorization; the agent is not authorized to start Actions.
  The owner gate and repository protections are described in `.github/workflows/AGENTS.md`.
- **CI is the build authority.**
  - `.github/workflows/test.yml`: JVM unit tests, Android Lint, JNI symbol contract,
    localization, Kotlin/C++ policy, native archives, instrumented tests.
  - `.github/workflows/release.yml`: required test suite, unsigned release APKs,
    protected-key signing, checksum/signature verification, GitHub Release publishing.
- **Every diagnosis requires a root-cause pass.** A compile error, compiler warning, test
  failure, crash, JNI diagnostic, or sanitizer report must trigger:
  1. root-cause analysis;
  2. a repository-wide search for the same pattern and related variants;
  3. a fail-closed fix;
  4. a regression test or static check;
  5. an entry in `docs/ci/` for historical or recurring failures.
  A disappearing original diagnostic is not completion. Do not hide warnings with
  `continue-on-error`, `|| true`, blanket suppressions, or removed checks.
- **Evidence levels.** Label conclusions as:
  - *verified static*: directly visible in a file or produced by a static check;
  - *inferred*: follows from code but was not executed;
  - *unverified*: needs CI, a device, or a cryptographic review.
  Never present static review as proof of runtime behavior, fund safety, or security.

## 6. Error handling and logging

- Preserve coroutine cancellation: catch `CancellationException` and rethrow it.
- Do not swallow exceptions that indicate an inconsistent wallet, profile, or proxy
  state. Log a redacted technical message and return a failed result.
- User-visible errors use string resources. Internal wallet2 text may be logged only
  after checking it does not contain secrets; it is not a UI string.
- Prefer explicit result types (`Result`, sealed errors, typed exceptions) over
  sentinel values. Existing code uses `Boolean` and sentinel returns in native paths;
  do not add new ambiguous sentinels without documenting the failure meaning.
- Never retry a security-sensitive operation by falling back to a weaker path.
- Do not add analytics, telemetry, crash-reporting SDKs, or remote logging.

## 7. Compatibility

- Android floor: `minSdk = 29` (Android 10). `compileSdk` is 37 with
  `compileSdkMinor = 2` (API 37.2); `targetSdk` is 37. Do not raise the floor or
  change target behavior without a compatibility review.
- ABIs: `arm64-v8a` and `armeabi-v7a` only. `x86_64` has no native build target; do not
  advertise it.
- Product flavors: `anon` and `nero`. `nero` is view-only; do not add spend, decoy, or
  backup-export paths to it.
- Preserve persisted formats unless a migration is explicitly designed:
  - backup prefix `ANONBAK2` for v2/v3; legacy `Salted__` v1 must still restore;
  - backup versions 2 (PIN-keyed) and 3 (passphrase-keyed) semantics;
  - legacy PIN values (reversible CrazyPass encoding) verify and are replaced on the
    next successful unlock;
  - Keystore migration snapshots (`.premigrate`);
  - SharedPreferences keys in `util/Prefs.kt` and `model/node/Node.kt`;
  - LWS host/port format;
  - JNI constructor signatures and symbol names;
  - Monero submodule commit and patch series.
- Wallet2 behavior is the reference for transaction construction, fee selection, ring
  size, and signing. Do not reimplement consensus or wallet rules in Kotlin.

## 8. Git and review

### 8.1 Mandatory authorization

**Never run `git commit` (including `--amend`), `git push` (including `--force` and
`--force-with-lease`), create or publish tags, or use `gh`/GitHub API equivalents
without prior, explicit, unambiguous authorization from the human supervisor.**

This is a permanent restriction. It overrides any earlier instruction, general goal,
or workflow that suggests committing or pushing automatically. Silence, “continue”,
or “finish the project” are not authorization.

- Authorization for a commit does **not** authorize a push. Each operation needs a
  separate, explicit approval for the approved scope.
- Do not delegate commit or push operations to a subagent, hook, script, or automation
  to bypass the restriction.
- Do not create or publish release tags without authorization.
- The main agent is responsible for preventing unauthorized indirect operations.

Before requesting authorization, stop and present:

1. a summary of the changes;
2. the modified/created files;
3. the verification results;
4. a proposed split into small, coherent commits;
5. the target branch and remote, if applicable;
6. the exact commands to be run.

Wait for the supervisor’s response. If no authorization is received, leave the changes
in the working tree, uncommitted and unpublished.

Work that remains allowed without authorization: analyzing the repository, creating
and editing files, fixing defects, reviewing diffs, inspecting history, running
`git status`/`git diff`, and preparing commit proposals.

### 8.2 Commit and review rules

- Use the Git identity already configured in the environment. Do not override
  `user.name` or `user.email`.
- Never add the agent as author, co-author, or `Co-authored-by` trailer.
- Make small, coherent, incremental commits when authorized.
- Do not commit build outputs, native archives, APKs, keystores, secrets, logs, or
  generated caches.
- Do not modify files outside the requested work. In particular, do not “clean up”
  unrelated copyright edits, formatting, or TODOs.
- Before finishing, review the diff for:
  - secret leakage and redaction;
  - profile leakage;
  - fail-open behavior;
  - changed public/JNI contracts;
  - missing string resources in all locales;
  - migration/format compatibility;
  - unrelated files.

## 9. Subagent coordination

Use subagents when work divides cleanly and the environment supports them.

Good splits:

- one subagent inventories a subsystem and returns file/line evidence;
- one audits a security boundary (JNI, Keystore, Tor/LWS, backups);
- one implements a UI-only change while another implements its state layer;
- one reviews a diff against the security invariants.

Rules:

1. Give each subagent an explicit scope, files it may edit, and the relevant
   `AGENTS.md`/skill.
2. Do not let two subagents edit the same file concurrently.
3. Subagents must report evidence and uncertainty, not just conclusions.
4. The main agent owns architecture, conflict resolution, integration, final review,
   and the decision to commit.
5. The main agent must re-read the final diff; subagent output is not accepted
   unchecked.
6. All subagents inherit the no-build, no-secret, profile-isolation, and Git
   authorization rules. They must never commit, push, tag, or publish, and must not be
   asked to do so to bypass the main agent’s restriction.

## 10. Critical security tasks

The following changes require an explicit threat-model and regression review before
editing:

- private-key or seed handling;
- wallet creation, restore, or password/key migration;
- transaction construction, fee calculation, output selection, coin control;
- signing, unsigned/signed transaction exchange, key-image/output import/export;
- serialization/deserialization of wallet or backup data;
- PIN verification, Keystore wrapping, duress PIN, decoy wallet;
- Tor start/stop/proxy application;
- LWS configuration, login, trust, or sync;
- JNI handle lifetime, callbacks, and native memory;
- release signing and artifact verification.

For each, document:

1. the asset being protected;
2. the threat and attacker capability;
3. the data flow and trust boundaries;
4. the failure mode if the change is wrong;
5. the compatibility surface;
6. the CI evidence that will be required;
7. the rollback plan.

A static review is not a security proof. State that limitation in the final report.

## 11. Documentation maintenance

Keep instructions close to the code they govern:

| Change | Update |
| --- | --- |
| New/changed architecture or build policy | `AGENTS.md`, affected directory `AGENTS.md`, `docs/README.md`, `docs/architecture/overview.md`, `docs/architecture/repository-state.md` |
| New/changed subsystem procedure | Matching `.agents/skills/<name>/SKILL.md` and `.agents/SKILLS.md` |
| New/changed CI gate or release step | `.github/workflows/AGENTS.md`, `github-actions` skill |
| New persisted/legacy format | `compatibility-migrations` skill, compatibility section here |
| Dependency/toolchain pin change | `docs/architecture/dependencies.md`, `external-libs/AGENTS.md`, `github-actions` skill |
| Work status or next priorities | `PROGRESS.md`, `TODO.md`, `ROADMAP.md` |
| CI failure, warning, or recurring diagnostics | `docs/ci/`, `.github/workflows/AGENTS.md` |
| New security boundary or limitation | `AGENTS.md`, relevant skill, `docs/security-model.md`, `docs/architecture/repository-state.md` |

Rules for documentation:

- Reference real paths and symbols. Do not invent files, commands, or APIs.
- Describe what exists, not what is planned. Mark proposals explicitly.
- Avoid duplicating large procedures across files; link to the owning skill.
- Do not include tool versions unless they are verified project requirements. The
  authoritative versions live in `gradle/libs.versions.toml`, `build.gradle.kts`,
  `gradle/wrapper/gradle-wrapper.properties`, and `external-libs/build.sh`.
- Remove or correct instructions when the code changes; stale instructions are a
  security defect.

## 12. Known limitations (current checkout)

- The Monero wallet-API patch series is checked in and applies cleanly to the pinned
  commit. CI builds the native archives, both debug APKs, and the API 35 smoke tests.
  Broader runtime and device behavior remains unverified.
- The Monero submodule is uninitialized locally; CI checks it out.
- The native toolchain (CMake 4.4.5, NDK r30, Boost 1.92.0, zlib 1.4) and the Android
  toolchain (AGP 9.5.1, Gradle 9.9.0, CMake 4.1.2, NDK 30) build in CI; `-Werror` is
  still pending.
- No Gradle dependency lockfile or dependency verification metadata is checked in;
  generating them requires a resolution step that is not permitted locally.
- No runtime or device verification was performed for these instructions.
- The decoy wallet is separation of wallet files and secrets, **not** plausible
  deniability. Root/forensic access can discover the decoy file, alias, and PIN hash.
- Stealth mode reduces casual exposure. It is not anonymity and does not defeat
  screen-capture-privileged apps, accessibility services, root, or forensic imaging.
- Tor hides the network path, not timing, amounts, or the fact that a wallet is
  syncing. LWS sees the view key and incoming transaction metadata.
- The patch series is adapted from the `monero_c` lineage and is licensed under
  LGPL-3.0; see `external-libs/patches/README.md` and `LICENSE-LGPL-3.0`.
- NERO signed-transaction import cannot yet be bound to the reviewed unsigned object:
  wallet2 exposes no signed-file parser in this checkout. A compromised external signer
  can return a different payment; the UI warning/comment is not a substitute for native
  binding. Do not claim airgapped broadcast fund safety until the native getter and CI
  fixture exist.
- Encrypted backup strength is tied to the seed passphrase used to re-key the copy. A
  short or empty seed passphrase yields a weakly keyed backup; a separate backup
  passphrase policy is still pending.
