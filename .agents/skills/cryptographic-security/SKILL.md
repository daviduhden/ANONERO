---
name: cryptographic-security
description: "Use when reviewing or changing ANONERO key handling, PIN verification, Android Keystore wrapping, backup encryption, secret serialization, randomness, or any cryptographic boundary. Do not use to invent new cryptographic algorithms."
---

# Cryptographic and secret-handling security

## When to use

- PIN hashing, throttling, or unlock verification.
- Android Keystore key creation, wrapping, loading, migration, or removal.
- Wallet secret generation and storage.
- Backup encryption/decryption and archive validation.
- Seed/passphrase/secret serialization or zeroization.
- Reviewing a change for secret leakage, weak parameters, or side channels.

Do not use to design a new cipher, KDF, or protocol. Use the platform and the formats
already in the repository.

## Read first

- `AGENTS.md`
- `app/src/main/java/io/anonero/util/PinHash.kt`
- `app/src/main/java/io/anonero/util/PinThrottle.kt`
- `app/src/main/java/io/anonero/util/KeyStoreHelper.kt`
- `app/src/main/java/io/anonero/util/WalletKey.kt`
- `app/src/main/java/io/anonero/util/CrazyPassEncoder.kt`
- `app/src/main/java/io/anonero/util/backup/EncryptUtil.kt`
- `app/src/main/java/io/anonero/util/backup/BackupHelper.kt`
- `app/src/main/java/io/anonero/util/backup/BackupArchive.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/ui/onboard/OnboardViewModel.kt`

## Invariants

- **No custom cryptography.** Use `SecureRandom`, JCA, the Android Keystore, and the
  existing Bouncy Castle usage. Do not hand-roll a cipher, KDF, MAC, encoding, or
  padding scheme.
- **PIN gate:** PBKDF2-HMAC-SHA256, 16-byte random salt, 210,000 iterations, 256-bit
  derived key, constant-time comparison. Do not lower the work factor. Legacy
  reversible CrazyPass values must still verify and be replaced on successful unlock.
- **Wallet secret:** 32 random bytes, Base64 without wrap, used as the wallet2
  password. It is wrapped by an Android Keystore AES-256-GCM key, StrongBox preferred,
  IV 12 bytes, tag 128 bits, prefs file `wallet`, prefix `walletSecret-`. It must not
  be exportable, logged, or written in plaintext.
- **Legacy RSA path:** alias `MonerujoRSA`, `SHA256withRSA`, and the native
  `slowHash` broken-variant policy are compatibility contracts. Do not “modernize”
  them without a migration and test vectors.
- **Backups:** new files use `ANONBAK2` + PBKDF2-HMAC-SHA256 at 600,000 iterations +
  AES-256-GCM (12-byte IV, 128-bit tag). Legacy `Salted__` v1 and v2 PIN-keyed files
  remain readable. Do not write new backups with the legacy 11,000-iteration CBC
  format.
- **Randomness:** every salt, IV, and secret comes from `SecureRandom` or the Keystore.
  Never use `Random`, `Math.random`, timestamps, or counters as secrets.
- **Zeroization is best-effort.** Filling arrays is good hygiene, but do not claim it
  guarantees erasure from the JVM, native heap, Keystore, or flash.
- **No secret in logs/errors.** `Timber`, `android.util.Log`, exception messages, and
  UI toasts must not contain seed words, passphrases, PINs, wallet passwords, wrapped
  secrets, or node credentials.
- **Fail closed.** A missing key, failed unwrap, failed GCM tag, or failed migration
  must not fall through to a weaker credential or plaintext.

## Procedure

1. **Name the asset.** Identify what is being protected (seed, wallet password W, PIN,
   backup contents, node credentials) and where it lives at rest and in memory.
2. **Draw the data flow.** Input -> KDF/keystore -> storage -> use -> destruction.
   Note every place it crosses a process, file, log, or UI boundary.
3. **Check parameters against the existing contract.** Do not change iteration counts,
   key sizes, IV/tag sizes, aliases, prefixes, or formats without a migration plan.
4. **Check failure modes.** Wrong PIN, missing Keystore key, invalidated key, truncated
   ciphertext, tampered tag, interrupted migration, and rollback.
5. **Check compatibility.** Legacy PIN values, v1/v2 backups, `.premigrate` snapshots,
   and existing wrapped secrets must still work.
6. **Add test vectors for pure logic.** Hash format, legacy verification, v1/v2 backup
   round-trip, archive validation. Tests run only in CI.
7. **Update documentation.** Record any new format, parameter, or limitation.
8. **State the limits.** Static review cannot prove side-channel resistance or
   cryptographic correctness.

## Static checks

```sh
grep -rn "Random(\|Math.random\|nextInt\|currentTimeMillis" app/src/main/java/io/anonero/util
grep -rn "AES/CBC\|AES/ECB\|MD5\|SHA-1\|DES\|RC4" app/src/main/java
grep -rn "PBEKeySpec\|PBKDF2\|ITERATIONS\|TAG_BITS\|SALT_BYTES" app/src/main/java/io/anonero/util
grep -rn "Log\.\|Timber" app/src/main/java/io/anonero/util | grep -i "key\|pass\|seed\|secret\|pin"
grep -rn "getSecretViewKey\|getSecretSpendKey\|getSeed" app/src/main/java
```

Review every hit for whether the value is a secret, a public identifier, or a test
fixture.

## Completion criteria

- [ ] No new cryptographic primitive or custom encoding.
- [ ] Parameters and formats match the documented contract.
- [ ] Randomness is cryptographically secure.
- [ ] Failure is fail-closed and compatibility is preserved.
- [ ] No secret in logs, errors, UI, or plaintext storage.
- [ ] Migration/rollback path is defined.
- [ ] Tests/vectors are in CI, not run locally.
- [ ] Remaining side-channel/runtime risk is stated, not hand-waved.
