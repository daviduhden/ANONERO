---
name: wallet-privacy
description: "Use when implementing or reviewing Stealth Mode, Decoy Wallet, Duress PIN, lock and auto-lock behavior, notifications, logs, clipboard handling, or secure wipe. Includes threat modeling and the limits of each protection."
---

# Wallet privacy: stealth, decoy, duress, wipe

## When to use

- Changing stealth notifications, hidden amounts, auto-lock, or log suppression.
- Changing decoy wallet creation, duress PIN, profile selection, or profile removal.
- Changing the lock screen, background sync lock, secure wipe, clipboard, or
  notification content.
- Reviewing whether a change leaks main/decoy data across profiles.

## Read first

- `AGENTS.md`
- `app/AGENTS.md`
- `app/src/main/java/io/anonero/util/PrivacySettings.kt`
- `app/src/main/java/io/anonero/util/WalletProfiles.kt`
- `app/src/main/java/io/anonero/util/PinHash.kt`
- `app/src/main/java/io/anonero/util/PinThrottle.kt`
- `app/src/main/java/io/anonero/ui/home/LockScreen.kt`
- `app/src/main/java/io/anonero/ui/home/settings/DecoySettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/SecureWipe.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/services/AnonNeroService.kt`
- `app/src/main/java/io/anonero/ui/MainActivity.kt`
- `app/src/main/java/io/anonero/ui/viewmodels/AppViewModel.kt`

## Threat model

Protect against:

- a thief or casual observer with the unlocked/locked device;
- shoulder surfing, recents thumbnails, screenshots, and notification previews;
- an attacker who extracts app files but not the Keystore;
- a coerced user who needs a second wallet/PIN.

Not protected against:

- root, forensic imaging, or a compromised OS;
- an attacker who can execute code in the app context and read memory;
- a compromised Keystore/TEE;
- network metadata correlation by a node/LWS/Tor observer;
- a camera pointed at the screen;
- an accessibility service or screen-capture-privileged app that the OS allows.

State these limits explicitly in reviews and user-facing text.

## Stealth mode invariants

- `FLAG_SECURE` is set unconditionally in `MainActivity`; stealth mode does not replace
  it.
- Stealth notifications must not contain balances, heights, addresses, transaction
  counts, or the product name. Use the existing `StealthMode.notificationTitle/Body`
  and `incomingTitle/Body`.
- `AnonLogTree` suppresses persisted logs while stealth is enabled.
- Hidden amounts and auto-lock are UI-level reductions, not encryption.
- Auto-lock enters background sync on `onStop` only when stealth and auto-lock are
  enabled. Do not weaken the `isChangingConfigurations`/`isFinishing`/permission-prompt
  guards.

## Decoy and duress invariants

- Main wallet file: `anon`. Decoy wallet file: `anon-alt`.
- Separate Keystore-wrapped secret, PIN hash, passphrase hash, and restore height for
  each profile.
- `WalletSession.profile` is **not persisted**. Every process start begins at MAIN;
  the entered PIN selects the profile. Do not persist “last used profile”.
- `WalletProfileStore.resolvePin` always performs both PIN derivations, using a dummy
  derivation when no decoy exists, so timing does not reveal whether a duress PIN is
  configured.
- A duress PIN must never equal the main PIN. A decoy passphrase must never equal the
  main passphrase. Enforce both at configuration time.
- Decoy setup is available only from the main profile and is refused in the decoy
  session and in the view-only `nero` build.
- The decoy cannot be exported through the encrypted backup path; its seed is the
  recovery path.
- Wiping from the decoy removes only the decoy profile’s files, Keystore secret,
  hashes, and restore height. It must not delete or lock the main wallet.
- `removeProfile` is the only sanctioned removal path; do not ad-hoc delete wallet
  files or preference keys.
- On a profile switch, close the previous wallet and reset `WalletSession`; if close
  fails, refuse the switch rather than leave both profiles’ keys referenced.
- Clear logs on every successful unlock so a decoy session cannot read the main
  session’s diagnostics and vice versa.
- Do not reveal which profile was opened in notifications, recents, or UI labels.

## Secure wipe invariants

- Require the seed passphrase, not just a tap.
- Stop the foreground service, pause/stop wallet sync, close the wallet, remove the
  active profile, then clear only shared non-credential state (nodes, logs, cache,
  PIN throttle).
- Do not clear the entire preference file; that would strand the other profile.
- Delete cache backups, extracted restores, and airgapped export files.
- A failed wallet close must fail the wipe, not report success.

## Procedure

1. **Name the adversary and asset.** Which profile, key, balance, address, or activity
   is exposed, and to whom?
2. **Draw the isolation matrix.** Rows: main, decoy. Columns: wallet file, Keystore
   alias, PIN hash, passphrase hash, restore height, logs, notifications, cache,
   backup. Confirm no cross-cell access.
3. **Trace every read/write.** `WalletSession`, `WalletProfileStore`, `Prefs.kt`,
   `AnonWalletHandler`, `AppViewModel`, `AnonApplication` cleanup.
4. **Check the failure path.** Missing decoy key, wrong PIN, failed close, failed
   Keystore load, failed wipe. Fail closed without revealing profile existence.
5. **Check logs/notifications/clipboard.** Any new log line, notification field, toast,
   or clipboard use must be reviewed for leakage.
6. **Update the threat model and UI limitations.** If a limitation changes, update the
   settings text and this skill.
7. **Test pure logic in JVM.** `WalletProfileStore`, PIN throttling, and preference
   key isolation can be unit-tested. Runtime lock/notification behavior needs CI/device
   testing.

## Static checks

```sh
grep -rn "WalletSession\|WalletProfile\|PREFS_DURESS\|PREFS_DECOY" app/src/main/java/io/anonero
grep -rn "isDecoy\|DECOY\|duress" app/src/main/java/io/anonero
grep -rn "notificationTitle\|notificationBody\|incomingTitle\|incomingBody" app/src/main/java/io/anonero
grep -rn "FLAG_SECURE\|allowBackup" app/src/main/java app/src/main/AndroidManifest.xml
grep -rn "deleteRecursively\|removeProfile\|wipe(" app/src/main/java/io/anonero
grep -rn "Timber" app/src/main/java/io/anonero/ui/home/settings/DecoySettings.kt
```

Look specifically for a main-profile key read while `WalletSession.isDecoy` is true, or
a decoy key read from the main path.

## Completion criteria

- [ ] No cross-profile file, key, hash, log, notification, or cache access.
- [ ] Profile is not persisted.
- [ ] PIN timing does not reveal decoy configuration.
- [ ] Main and duress PIN/passphrase collisions are rejected.
- [ ] Wipe removes only the active profile and shared non-credential state.
- [ ] Stealth notifications and logs contain no sensitive values.
- [ ] `FLAG_SECURE` and the lock overlay are intact.
- [ ] Threat-model limitations are documented.
- [ ] Runtime behavior marked unverified until CI/device testing.
