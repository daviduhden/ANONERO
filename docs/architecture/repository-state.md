# Repository state and instruction decisions

This document records the evidence and decisions behind `AGENTS.md`, the directory
instruction files, and `.agents/skills/`. It is not a substitute for those files.

## Audit metadata

- Date: 2026-11-09
- Audit baseline: `ed8b097` (`docs: add Apache-2.0 license headers and normalize comment style`)
- Current cycle: hardening commits through `52d67d4`; the CI compile-failure fixes and
  `docs/ci/` inventory were committed afterwards.
- Working tree: the copyright/newline normalization was committed in `52d67d4`; no
  uncommitted changes remain after this cycle.
- Scope: repository structure, build files, Android/Kotlin sources, C++ JNI adapter,
  native build scripts, CI workflows, tests, resources, and existing agent
  instructions.
- Existing agent instructions at the start of the audit: none. `.agents/skills/` and
  `docs/` were empty; this work adds the instruction and documentation set.

## Verified evidence

| Fact | Evidence |
| --- | --- |
| Android app is Kotlin only; no Java sources | `find app/src -name '*.java'` returned none; 127 `.kt` files |
| CI enforces the Kotlin/C++ source layout | `.github/workflows/test.yml` `native-language-policy`; `.github/workflows/release.yml` |
| Two flavors, view-only NERO | `app/build.gradle.kts` `productFlavors` |
| minSdk 29, compileSdk 37.2, targetSdk 37 | `app/build.gradle.kts` |
| ABIs arm64-v8a and armeabi-v7a only | `ndk.abiFilters`, `splits`, `external-libs/build.sh` |
| JNI adapter is C++ and project-owned | `app/src/main/cpp/anonero.cpp` (2195 lines), `anonero.h` |
| JNI symbol parity is statically checked | `scripts/check-jni-contract.py`, `test.yml` |
| Localization parity is statically checked | `scripts/check-i18n.py`, `test.yml` |
| Monero submodule pinned | `git ls-tree HEAD external-libs/monero` -> `85f036ba...` |
| Submodule not populated locally | `external-libs/monero` empty |
| Wallet-API patch series present | `external-libs/patches/0001..0005`, `apply.sh`, `README.md` |
| Patch series applies to the pin | `git apply --index --3way` on a clean `85f036ba` checkout |
| Native archives not compiled | requires the manual CI `native` job |
| Embedded Tor with mandatory proxy | `TorService.kt`, `AnonWalletHandler.applyProxy`, `WALLET_USE_TOR` default true |
| Optional LWS over Tor | `PrivacySettings.kt` `LwsConfig`, `AnonWalletHandler.startService` |
| Keystore-bound wallet secret | `KeyStoreHelper.kt`, `WalletKey.kt` |
| PBKDF2 PIN gate and throttle | `PinHash.kt`, `PinThrottle.kt` |
| Backup v1/v2/v3 compatibility | `EncryptUtil.kt`, `BackupHelper.kt`, tests |
| Stealth, decoy, duress, wipe | `PrivacySettings.kt`, `WalletProfiles.kt`, `DecoySettings.kt`, `SecureWipe.kt` |
| FLAG_SECURE and no OS backup | `MainActivity.kt`, `AndroidManifest.xml`, XML exclusion rules |
| CI release signs six APKs and verifies checksums | `.github/workflows/release.yml` |
| No runtime verification performed | no builds, tests, emulator, or device access used |

## State classification

### Implemented in source

- Kotlin/Compose Android application with Koin, DataStore, and a foreground wallet
  service.
- Wallet2 access through the C++ JNI adapter and prebuilt Monero archives.
- Create/restore (Polyseed and legacy), accounts/subaddresses, balances/history,
  UTXO freeze/thaw, coin control, send/sweep/pay-to-many, fee display, review,
  signing/verification, key-image/output exchange, UR/QR airgapped flows.
- Tor service with SOCKS proxy enforcement and fail-closed behavior.
- Optional LWS with view-key exposure and untrusted-server policy.
- Keystore-wrapped wallet secret, PIN hash/throttle, legacy PIN migration.
- Encrypted backups with AEAD v3 writer and v1/v2 readers.
- Stealth mode, decoy/duress, secure wipe, sensitive clipboard.
- English, Spanish (Spain), Russian, Simplified Chinese resources.
- CI test/release workflows, native archive artifact, signing, checksums.

### In progress or incomplete

- Native reproducibility: the wallet-API patch series is checked in and applies to the
  pinned commit. The local submodule is uninitialized and the native archives have not
  been compiled; the manual CI `native` job must validate them.
- Documentation: `docs/` was empty; this audit adds the documentation index, architecture
  and JNI docs, security model, features, development, build/release, state, dependency
  record, progress/roadmap/todo files, and the instruction system.
- No evidence of runtime/device validation of the current working tree.

### Pending or out of scope

- Java migration. Already complete.
- Another native runtime or FFI layer. Kotlin/Compose and C++/JNI are the permanent
  architecture.
- Removal of legacy compatibility formats. No deprecation decision exists.
- A full threat model document beyond the security sections in the skills.

## Decisions

### 1. Kotlin/Compose + C++ is the architecture

The repository’s build files and CI enforce Kotlin for Android and C++ for the JNI
adapter:

- `app/build.gradle.kts` and `app/CMakeLists.txt` (C++ adapter);
- `.github/workflows/test.yml` `native-language-policy`;
- `.github/workflows/release.yml` policy step;
- the project-owned native boundary in `app/src/main/cpp/`.

The instructions document Kotlin/Compose + C++/JNI as the permanent architecture and do
not include a separate native-runtime skill.

### 2. `native-jni` combines the native and FFI procedures

The native boundary is C++/JNI with handle lifetime, local/global references, callbacks,
and wallet2 status mapping. A single `native-jni` skill covers that procedure and
references the directory
`AGENTS.md`.

### 3. `compatibility-migrations` replaces `code-migration`

There is no Java left to migrate, and C++ is intentional. The useful migration work is
persisted-format compatibility (backups, PIN, Keystore snapshots), Monero patch/pin
upgrades, and Android toolchain upgrades. The skill is scoped to that work.

### 4. Directory instructions are limited to real subtree differences

- `app/AGENTS.md`: module build, state, persistence, UI, tests.
- `app/src/main/cpp/AGENTS.md`: JNI contract, memory, exceptions, linkage.
- `external-libs/AGENTS.md`: pinned native build and patches.
- `.github/workflows/AGENTS.md`: CI trust boundaries and release invariants.

Other directories are covered by a skill; adding an `AGENTS.md` there would duplicate
procedure without adding subtree-specific constraints.

### 5. Static analysis only

The environment prohibits local builds/tests. The instructions explicitly allow static
checks that do not execute the app (`scripts/check-*.py`, grep, parsing, shell syntax
checks) and require CI evidence for runtime claims. This matches the repository’s CI
design.

### 6. Application source and Git state

The instruction pass added documentation and instructions only. A separate dependency
update then changed build configuration, native pins, CI workflow settings, and
documentation; it did not change Kotlin application source. A documentation pass then added
KDoc/comments and Markdown; it did not change behavior. The copyright/newline normalization
was committed in `52d67d4`. The CI compile-failure fixes were committed afterwards.

## Known gaps and follow-ups

1. **Validate the restored Monero patch series.** The five patches and their nested
   submodule pins are checked in, but the native archives have not been compiled. The
   manual `native` CI job is the authority; the series is adapted from `monero_c` under
   LGPL-3.0 (see `external-libs/patches/README.md`).
2. **Initialize the Monero submodule for local native work.**
   `git submodule update --init --recursive` is required before patch application or a
   native build. Do not run it as part of an unrelated change.
3. **Validate instructions in CI.** Markdown/YAML/frontmatter were checked statically.
   Skill discovery and agent behavior still need a real agent run.
4. **Runtime privacy validation.** Tor restart, LWS reconnect, background-sync lock,
   notifications, decoy switching, and wipe need device/instrumented validation.
5. **Cryptographic review.** The PIN/Keystore/backup design is documented from code,
   not independently audited. Side-channel and Keystore behavior remain unverified.
6. **README accuracy.** `README.md` describes a build flow that depends on missing
   patches. Do not treat it as proof of the current checkout.
7. **Legacy compatibility lifecycle.** v1 backups and legacy PIN values are still
   supported. A future removal needs a deprecation decision and fixtures.
8. **Validate dependency updates in CI.** AGP/Gradle, CMake/NDK, Boost, and zlib
   changes are statically reviewed only. The `native`, Android, instrumented, and
   release jobs must pass before claiming compatibility.
9. **Add dependency resolution controls.** No Gradle lockfile or dependency
   verification metadata is checked in; generating them requires Gradle resolution in
   CI, not locally.

## Maintenance triggers

Update this document and the affected instructions when:

- the language policy or JNI boundary changes;
- the Monero pin, patch series, native dependency pins, NDK, SDK, AGP, Gradle, or
  Kotlin version changes;
- a persisted format or migration is added/removed;
- a security invariant or threat-model limitation changes;
- a CI gate, signing step, or release invariant changes;
- a new skill is added or a directory gains subtree-specific rules.

Do not update this document with unverified claims. Record the evidence source and the
date.
