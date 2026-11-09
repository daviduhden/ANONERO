---
name: compatibility-migrations
description: "Use when changing persisted formats, legacy compatibility paths, backup or PIN migrations, the pinned Monero patch series, Android SDK/AGP/Gradle upgrades, or data migration between app versions. Java to Kotlin is complete; Kotlin/Compose and C++ are the permanent architecture."
---

# Compatibility and migrations

## When to use

- Changing a persisted format or preference key.
- Adding or modifying a backup version, legacy PIN path, or Keystore migration.
- Updating the pinned Monero commit, patch series, NDK, SDK, AGP, Gradle, or Kotlin
  version.
- Moving data or behavior from an older app version without losing user funds or
  history.

## Scope note

- The Java-to-Kotlin migration is complete: there are no `.java` files.
- C++ is the intentional native adapter language and the permanent native engine.
- The remaining migrations are **data, format, API, and toolchain** compatibility.

## Read first

- `AGENTS.md`
- `docs/architecture/repository-state.md`
- `docs/architecture/dependencies.md`
- `app/src/main/java/io/anonero/util/backup/EncryptUtil.kt`
- `app/src/main/java/io/anonero/util/backup/BackupHelper.kt`
- `app/src/main/java/io/anonero/util/backup/BackupArchive.kt`
- `app/src/main/java/io/anonero/util/PinHash.kt`
- `app/src/main/java/io/anonero/util/KeyStoreHelper.kt`
- `app/src/main/java/io/anonero/util/WalletKey.kt`
- `app/src/main/java/io/anonero/util/WalletProfiles.kt`
- `app/src/main/java/io/anonero/util/Prefs.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `external-libs/build.sh`
- `external-libs/patches/apply.sh`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties`

## Invariants

- Read old formats; write the current format. Do not silently drop a reader.
- Never lose access to funds, seeds, history, or the ability to restore a backup.
- Migrations must be idempotent, resumable, and rollback-safe.
- Do not delete a compatibility path without a written deprecation decision and a
  test vector proving the old format is no longer needed.
- Do not change a version marker (`ANONBAK2`, `v2:` PIN prefix, backup version 2/3,
  `.premigrate`) without a migration.
- Do not lower cryptographic parameters to ease compatibility.
- Do not change `minSdk`, target behavior, ABI, JNI signatures, or the Monero pin as
  part of an unrelated migration.
- Do not commit patched submodule state or generated native archives.

## Current compatibility surface

### Backups

- v1: `Salted__` prefix, PBKDF2-HMAC-SHA256 11,000 iterations, AES-CBC. Read-only
  compatibility.
- v2: `ANONBAK2`, PBKDF2 600,000, AES-GCM, PIN-keyed wallet files (backup version 2).
- v3: same AEAD envelope, wallet files re-keyed to the seed passphrase (backup version
  3).
- `BackupArchive` accepts only flat entries (`anon.json`, `<walletName>`, and
  `<walletName>.*`), rejects symlinks, duplicate names, traversal, and non-fresh
  destinations.
- Do not change `BackupPayload`/`Backup`/`WalletBackup` fields without serializer
  defaults or a versioned migration.

### PIN and Keystore

- `PinHash` stores `v2:<iterations>:<salt>:<hash>`; legacy reversible values verify
  and are replaced on successful unlock.
- `PinThrottle` counts attempts before verification and uses the boot clock.
- `WalletKey` snapshots `<wallet>.keys` and `<wallet>` to `.premigrate` during
  migration; startup `recoverInterruptedMigration` either confirms and deletes the
  snapshot or restores it.
- Keystore aliases/prefixes (`MonerujoRSA`, `walletKey-`, `walletSecret-`, prefs file
  `wallet`) are compatibility contracts.

### Preferences and profiles

- `util/Prefs.kt` keys and `NodeFields` values are persisted.
- Main uses `PREFS_PIN_HASH`, `PREFS_PASSPHRASE_HASH`, `RESTORE_HEIGHT`; decoy uses
  `PREFS_DURESS_PIN_HASH`, `PREFS_DECOY_PASSPHRASE_HASH`,
  `PREFS_DECOY_CONFIGURED`, and its own restore height.
- `WalletSession.profile` is deliberately not persisted.

### Monero and toolchain

- Monero submodule gitlink and patch series are pinned together.
- Dependency versions and hashes in `external-libs/build.sh` move together.
- Kotlin plugin version in root `build.gradle.kts` must match
  `gradle/libs.versions.toml`.
- Gradle wrapper distribution and SHA-256 are pinned.

## Procedure

1. **Define the old and new states.** What data/format/API exists now, what will exist
   after, and which versions must still work?
2. **Freeze a fixture.** Capture a representative old artifact (backup bytes, PIN
   value, prefs layout, submodule/patch state) and keep it in a test resource or
   documented vector. Never use real user secrets.
3. **Write the read path first.** Prove the old fixture opens/parses/restores before
   changing the writer.
4. **Implement the migration.** Prefer a one-way, idempotent upgrade with an explicit
   version marker. Keep a rollback for failures that can strand a wallet.
5. **Handle failure.** Interrupted migration, missing key, wrong credential, truncated
   file, wrong network, and concurrent write.
6. **Verify both directions.** Old -> new and new -> current. For backups, verify
   decrypt + extract + open. For PIN, verify legacy and new. For Monero, verify the
   native CI build and patch markers.
7. **Update docs.** Record the format, version, migration trigger, and removal
   condition.
8. **CI evidence.** JVM tests for pure formats; native CI for Monero; device CI for
   app-level migration.

## Static checks

```sh
grep -rn "ANONBAK2\|Salted__\|BACKUP_VERSION\|V2_ITERATIONS" app/src/main/java/io/anonero
grep -rn "premigrate\|recoverInterruptedMigration\|setPassword" app/src/main/java/io/anonero
grep -rn "PREFS_\|RESTORE_HEIGHT" app/src/main/java/io/anonero/util/Prefs.kt
grep -rn "85f036ba7019cd36f08e8a38a27c37ea7b522618\|v0.18.5.4" README.md external-libs
git ls-tree HEAD external-libs/monero
git -C external-libs/monero status --short
ls external-libs/patches
```

## Completion criteria

- [ ] Old fixture still works.
- [ ] New format is written with the current parameters.
- [ ] Migration is idempotent and failure-safe.
- [ ] Rollback or recovery path exists.
- [ ] No fund/history/seed access is lost.
- [ ] Versions, hashes, and pins agree.
- [ ] Documentation updated.
- [ ] CI evidence defined; static review not presented as migration proof.
