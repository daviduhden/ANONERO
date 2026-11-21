---
name: wallet-privacy
description: "Use when implementing or reviewing Stealth Mode, Decoy Wallet, Duress PIN, lock and auto-lock behavior, notifications, logs, clipboard handling, or secure wipe. Distinguishes local anonymity and coercion mitigation from network anonymity."
---

# Wallet anonymity, privacy, and coercion controls

## Scope and priority

ANONERO's primary goal is anonymity, with privacy and security as required pillars. This
skill covers local-exposure controls (stealth, notifications, clipboard, recents, wipe) and
coercion controls (decoy, duress). They reduce local observability and some physical-access
risks; they do **not** provide network anonymity, transaction unlinkability, or plausible
deniability. Network anonymity belongs to `tor-networking` and `monero-lws`.

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

## Anonymity contribution

| Control | Primary property | Not provided |
| --- | --- | --- |
| Stealth mode | Local observability reduction | Network anonymity, transaction privacy |
| Hidden amounts | Shoulder-surfing resistance | On-chain confidentiality (Monero supplies that) |
| Auto-lock/background sync | Reduced unlocked-window exposure | Protection from root/forensics |
| Decoy wallet | Separation of wallet files and identities on device | Plausible deniability, network anonymity |
| Duress PIN | Coercion path to a decoy profile | Proof of coercion resistance |
| Secure wipe | Reduced residual local data | Erasure of backups, remote copies, or forensic traces |

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
- [ ] Controls are documented as local-exposure/coercion mitigations, not network
      anonymity or plausible deniability.
- [ ] Threat-model limitations are documented.
- [ ] Runtime behavior marked unverified until CI/device testing.


Diagnostic persistence now accepts warning/error events through `DiagnosticPolicy` exact
constants, with fixed fallback/tag and minute timestamps in every build. Do not reintroduce
an independent DebugTree, raw throwable forwarding to Android crash logging, hardware
fingerprints in exports or routine lifecycle logs. Startup removes old diagnostic store/
exports before consumers; clear invalidates queued writes and mutation rechecks stealth.
Fixed native errors remain outside Kotlin stealth suppression. Notification visibility is
SECRET in both builders. MAIN/DECOY have independent LWS settings/consent; profile removal
must clear only that profile's LWS keys. Shared RPC/transport can still correlate profiles.


Auxiliary close failures retain ownership and recovery files; retry closure before deleting
backing files/credentials or publishing decoy success. Profile deletion runs inside the
registry's closed-path guard. This does not serialize the earlier Keystore provisioning
phase globally or establish forensic/coercion resistance. Restore cleanup preserves the
transport cache; explicit wipe retains full cache cleanup. Hidden amounts also covers the
Pay to Many total. Java/native strings are not erased by hiding fields or redacting logs.

## Maintenance & Continuous Learning

When this subsystem changes, reconcile this procedure with the implementation and
record reusable constraints, root causes and remaining verification limits. Check the
closest AGENTS.md and the skills index for stale guidance. Run the read-only
`python3 scripts/validate-agent-knowledge.py --scope worktree` before review; inspect
AK004 warnings for relevance and run regression tests only in authorized CI.

Publish the lock overlay for an open wallet even before native initialization.
If native background sync cannot start, keep the faulted session locked and
offline. Proxy and node setup must check the fault after asynchronous acquisition
and before enabling network activity. Require fresh authenticated wallet opening
to clear the fault.
