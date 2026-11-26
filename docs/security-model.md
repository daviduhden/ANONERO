# Anonymity, privacy, and security model

This document describes the anonymity properties ANONERO is designed to preserve, the
privacy and security boundaries it assumes, and the limits that remain. It is a design
review, not an independent audit. A static review cannot prove runtime behavior,
side-channel resistance, anonymity against traffic correlation, or fund safety.

## Priority and scope

ANONERO is an anonymity-focused Monero wallet. The priority order is:

1. **Anonymity.** Reduce identifiable information, observable metadata, activity
   correlation, and unnecessary trust in third parties.
2. **Privacy.** Protect wallet and network data from unnecessary collection, persistence,
   or disclosure.
3. **Security.** Protect funds, keys, backups, software integrity, and communications.

Privacy and security are indispensable, but they are not synonyms for anonymity. These
properties are distinct:

- **Network anonymity:** the difficulty of linking a connection to the user's identity.
- **Transaction privacy:** the on-chain unlinkability provided by Monero, plus wallet
  behavior such as subaddress handling and input selection.
- **Data confidentiality:** protection of seeds, keys, notes, backups, and local metadata.
- **Fund security:** protection against theft, loss, corruption, and unauthorized spend.
- **Local anonymity:** resistance to observation by people or apps with device access.
- **Correlation resistance:** resistance to linking separate sessions, transactions,
  addresses, or network flows by timing, amounts, or infrastructure.

A secure wallet can be non-anonymous. A private transaction can be correlated by IP or
timing. An anonymous network path does not protect a compromised key. ANONERO therefore
evaluates changes against all of these properties instead of treating one as a substitute
for another.

## 1. Assets

| Asset | Where it lives | Primary protection |
| --- | --- | --- |
| Recovery seed / passphrase | wallet2; displayed only on explicit seed screens | App sandbox, Keystore-wrapped wallet password, PIN gate, `FLAG_SECURE` |
| Spend key | wallet2, present only while the wallet is unlocked | Background sync removes it; Keystore/PIN gate |
| View key | wallet2; sent to LWS in light-wallet mode | Selected transport; LWS trust policy |
| Wallet password `W` | 256-bit random secret | Android Keystore AES-GCM wrapping; never derived from PIN |
| PIN | user input; stored as PBKDF2 hash | PBKDF2-HMAC-SHA256, throttle, constant-time compare |
| Encrypted backup | user-selected file | PBKDF2 600k + AES-256-GCM; v1/v2 read compatibility |
| Transaction authenticity | wallet2 construction, review, signing | Review from `confirmationMessage`; sign the loaded object |
| Network identity | IP address, DNS, destinations, timing, and connection metadata | Transport policy, proxy/DNS rules, no silent fallback |
| Node credentials | `SharedPreferences` and DataStore | App sandbox only; not Keystore-encrypted |
| Local identifying metadata | notifications, logs, clipboard, recents | Stealth mode, `FLAG_SECURE`, log suppression, sensitive clipboard |
| Build integrity | Gradle/Monero/dependency pins; CI | Version catalog, hashes, pinned Actions, protected release signing |

## 2. Trust boundaries

| Boundary | Trusted for | Not trusted for |
| --- | --- | --- |
| Android OS and app sandbox | File isolation, process isolation, permission enforcement | Root/forensic access, compromised OS, screen capture |
| Android Keystore/TEE/StrongBox | Non-exportable key storage, AES-GCM wrapping | A fully compromised app process that can call the key |
| wallet2 | Key derivation, transaction construction, signing, wallet file format | UI policy, network proxy, PIN gate |
| Monero daemon | Chain data and wallet RPC | Honesty about chain state; it can lie or be malicious |
| Transport (Tor/I2P/VPN/custom) | Network-path anonymity/confidentiality according to the selected mode | Timing, amounts, sync patterns; correlation; a VPN cannot prove per-app routing |
| LWS | Remote indexing | Trust; it sees the view key and incoming metadata and can lie |
| Backup file | Portability | Confidentiality if the passphrase is weak; integrity for v1 only |
| CI/release chain | Artifact provenance | A compromised dependency, action, runner, or signing secret |

## 3. Threat model

### Adversaries and observations

| Adversary | Can observe | ANONERO mitigation | Residual risk |
| --- | --- | --- | --- |
| Monero RPC node operator | Source IP (unless proxied), RPC queries, broadcast timing, transaction set | Tor/I2P/custom proxy, fail-closed transport, node-network filtering, no clearnet fallback in Tor/I2P modes | The node can lie about chain state and correlate queries; traffic analysis remains |
| LWS operator | Public address, view key, incoming amounts/timing, connection IP unless proxied | Selected transport, TLS for non-loopback clearnet, untrusted-daemon policy, explicit consent, no automatic operator switch | The view key reveals incoming activity; the operator can lie or correlate |
| VPN provider | Source IP, DNS, destinations, connection metadata | Explicit system-VPN mode, TLS for clearnet LWS, no silent fallback | VPN can log; Android cannot prove per-app inclusion or leak-free DNS |
| Network/ISP observer | Destination IP, DNS/SNI, timing, volume where not encrypted | Tor/I2P paths, TLS, remote SOCKS resolution, no direct fallback | VPN/clearnet modes and traffic correlation remain observable |
| Correlation adversary | Repeated destinations, timing, amounts, sync patterns, reuse | Tor by default, no intentional subaddress reuse, LWS consent, airgapped flows | Monero graph analysis, timing correlation, and off-device identity links remain |
| Malicious app on device | Screen, clipboard, notifications, accessibility, logs | `FLAG_SECURE`, stealth mode, clipboard timeout, log suppression | Root, accessibility, screen capture, and privileged apps can defeat these |
| Person with physical access | Device, wallet files, recents, coercion | Keystore-wrapped secret, PIN gate, decoy/duress, wipe | Coercion and forensic access; decoy is not plausible deniability |
| Build/release compromise | Source, build artifacts, signing | Pinned inputs and signer certificates, hashes, manual CI, per-app protected signing | Compromised runner, dependency, action, or signing secret |

### Considered

- A thief with the locked device, including notification previews, recents, and screenshots.
- A forensic copy of app-private files without the device Keystore.
- A coercer who demands a wallet and PIN; the duress PIN can open a decoy.
- A malicious or compromised daemon/LWS that returns false chain data.
- A local network observer or a node that would otherwise learn the user's IP.
- A malicious QR/UR payload or a transaction file that does not match what the user saw.
- A weak or reused backup passphrase, and a corrupted/tampered backup.
- A supply-chain compromise in a dependency, Gradle wrapper, Monero pin, or GitHub Action.
- Accidental secret leakage through logs, notifications, clipboard, or crash text.
- Correlation of separate sessions or transactions through timing, amounts, reuse, or
  shared infrastructure.

### Not fully mitigated

- Root, a compromised OS, a compromised app process, or a compromised Keystore.
- Screen capture by a privileged app, accessibility services, or a camera pointed at the
  screen.
- Correlation of timing, amounts, IP metadata, or LWS view-key data by an external observer.
- Traffic correlation and endpoint compromise even when Tor or I2P is used; no transport
  provides guaranteed anonymity.
- A user who chooses a weak PIN/passphrase, shares a seed, reuses an address, or links
  wallet activity to an identity outside ANONERO.
- A compromised build runner or release signing secret.
- Plausible deniability of the decoy wallet (see below).

## 4. Wallet creation and restore

`OnboardViewModel` writes credentials and the Keystore secret before creating the wallet
file. A failed creation deletes the partial wallet. Before writing MAIN, it rejects:

- a PIN that matches the configured duress PIN;
- a passphrase that matches the decoy passphrase;
- an existing MAIN wallet file or credential set (`RestoreTarget.requireEmpty`).

Seed restore accepts 16-word Polyseed and 25-word legacy mnemonics. The seed and passphrase
are passed to wallet2; the app does not reimplement mnemonic or key derivation. View-only
creation uses `WalletManager.createWalletWithKeys`, honors the payload restore height, and
the NERO build has no spend path.

## 5. PIN, Keystore, and migration

- `WalletKey.newSecret()` creates 32 random bytes (Base64 text) as the wallet2 password.
- `KeyStoreHelper` wraps it with an AES-256-GCM Keystore key, preferring StrongBox and
  falling back to TEE/software. The wrapped value is `IV || ciphertext||tag`, Base64.
- `PinHash` stores `v2:<iterations>:<salt>:<hash>` using PBKDF2-HMAC-SHA256 at 210,000
  iterations, a 16-byte salt, and a 256-bit key. Comparison is constant-time.
- Legacy reversible PIN values still verify and are replaced on the next successful unlock.
- The lock screen requires a successful commit of the attempt count before checking the
  PIN. Failed storage denies the current attempt.
  The legacy CrAzYpass path uses `KeyStoreHelper.getCrazyPass` and the native `slowHash` with
  its ARM32 broken-variant compatibility rule; it is not a new key-derivation design.
- `PinThrottle` counts an attempt before checking it, uses the boot clock, and increases the
  wait after five attempts up to five minutes.
- `AnonWalletHandler.migrateToKeystore` snapshots `.keys` and the cache to `.premigrate`,
  re-keys the live wallet, wraps the new secret, and rolls back on failure.
  Recovery runs under a closed-path guard after PIN verification and before MAIN opens. It
  retains legacy snapshots through copy failures. A wrapped-secret preference
  alone cannot retire the fallback. Cleanup requires a successful wallet open
  or verified migration. A cache-only sidecar cannot overwrite a newer cache.

**Limits:** the PIN is an app-level gate, not a device biometric. An attacker with live
app-context code execution can use the Keystore key. A Keystore reset or device change makes
the wrapped secret unavailable; the seed/backup is the recovery path.

## 6. Unlock and background sync

`MainActivity` enters background sync when the user leaves the app, and stealth mode can do
so on `onStop`. `WalletState.enterBackgroundSync` calls wallet2's `startBackgroundSync`,
which removes spend capability. The lock overlay remains above the UI until
`WalletState.exitBackgroundSync` successfully calls `stopBackgroundSync` with the
Keystore-bound secret (or the legacy PIN). If the native stop fails, the wallet stays locked.

The APK sets `FLAG_SECURE` unconditionally. `AnonLogTree` suppresses persistence while
stealth mode is enabled. `AppViewModel.openWallet` clears the previous session's logs and
cancels stale incoming notifications on every successful unlock.

**Limitation:** if wallet2's `startBackgroundSync` fails, `WalletState` keeps the lock
overlay visible but cannot prove that the native spend key was removed. The UI remains
locked; a process restart or a later successful `stopBackgroundSync` is the safe recovery
path.

## 7. Transaction construction, review, and signing

- Amounts are atomic units. `MoneroAmount.parseExact` rejects zero, negative, over-precision,
  and overflow for pay-to-many. The single-send path currently uses wallet2's
  `getAmountFromString`; this is a parser difference to keep in mind when reviewing input
  validation.
- Multi-destination sends are one wallet2 transaction set. Destination/amount counts and
  exact totals are validated before construction.
- Coin control uses key images for construction and output public keys for freeze/thaw.
- Unsigned review is built from wallet2's `confirmationMessage`, parsed by `SigningSummary`.
  If parsing fails, the raw sentence is shown; the advisory scalar getters are not used as a
  fallback.
- `UnsignedTransaction.sign` signs the reviewed object. `Wallet.signAndExportJ` reloads the
  file from disk and is a different operation.
- ANON broadcasts a reviewed pending transaction and reports
  success after wallet2 succeeds.

### Anonymity properties and limits

- Monero supplies ring signatures, stealth addresses, and confidential amounts; ANONERO
  does not reimplement consensus or transaction privacy.
- The wallet avoids intentional subaddress reuse. External addresses, exchange records,
  and on-chain graph analysis remain outside its control.
- Pay-to-many reduces the number of transactions but links the outputs in one set.
- Coin control and freeze/thaw can reduce unintended input linkage, but incorrect manual
  selection can worsen it. There is no automatic anonymity guarantee.
- Sign/verify flows provide authenticity, not transaction anonymity.

## 8. Backups

- Export uses `ANONBAK2` with PBKDF2-HMAC-SHA256 at 600,000 iterations, AES-256-GCM,
  a 12-byte IV and a 128-bit tag. The payload version identifies the copied wallet's
  credential. Version 3 re-keys the copy to the seed passphrase. Version 2 preserves
  the legacy PIN. The live wallet keeps its existing secret.
- The reader also accepts v1 (`Salted__`, 11,000 iterations, AES-CBC). That envelope
  has no authentication tag.
- `BackupArchive` accepts flat `anon.json`, `<wallet>` and `<wallet>.*` entries.
  It rejects duplicate names, traversal and non-fresh destinations. It checks entries
  before extraction, then copies their contents to regular files without restoring
  ZIP attributes.
- Restore stages the decrypted ZIP under app-private `tmp_backup` and removes it
  before publishing the preview. A failed native close prevents a legacy PIN retry
  and defers deletion of the managed wallet’s files.
- The decoy cannot be exported through this path because a restore targets MAIN; use the
  decoy seed instead.
- `BackupHelper.createBackUp` clears only transient backup/airgapped artifacts, preserving
  the live Tor cache and cache log. A failed restore removes only MAIN and does not leave the
  backup's node as the active configuration.
- Restored node records are validated by `BackupNodePolicy`: schemes, credentials, paths,
  whitespace, embedded ports, malformed IPv6, and out-of-range ports are dropped, and the
  saved network type is the build's network rather than the backup's value.

## 9. Network anonymity: transports, Tor, I2P, VPN, and LWS

- Transport is explicit: embedded Tor (default), external Tor, external I2P (i2pd, InviZible, official I2P Java), custom SOCKS5,
  or system VPN. Tor/I2P/custom require their proxy and never fall back to clearnet.
- Transport modes are not equivalent for anonymity. Tor modes target `.onion`, I2P modes
  target `.i2p`, system VPN is a clearnet mode that shifts trust to the VPN operator, and a
  custom proxy provides only what its operator provides. None is a guarantee.
- Embedded Tor waits up to 60 seconds for SOCKS and throws on timeout. A `TorSplash` timeout
  does not authorize a direct connection.
- System VPN is the only clearnet mode. It requires an active `TRANSPORT_VPN` and fails
  closed when the VPN is absent or lost. Android cannot prove that a VPN included this app,
  so this mode is not described as leak-proof.
- LWS uses the same selected transport as RPC. Its destination must match the transport
  network (`.onion` on Tor, `.i2p` on I2P, clearnet on system VPN or an explicit
  custom-clearnet proxy); a mismatch is rejected and leaves the wallet offline. The server
  receives the public address and view key, can observe incoming transactions/amounts/timing,
  and can lie about chain state.
- Non-loopback clearnet LWS requires TLS (`https://`); loopback may use `http://` for a
  local server. `.onion` and `.i2p` endpoints use `http://` and rely on their transport.
  Patch `0008-enforce-tls-for-https-daemon-address.patch` forces TLS for `https://`
  instead of wallet2's autodetect, which could otherwise retry in cleartext. epee verifies
  the system CA and hostname; this still requires CI/device validation.
- The first save of a new LWS server requires explicit user consent before the view key is
  disclosed. ANONERO never silently switches to a different LWS operator; automatic
  reconnection to the already authorized server is allowed.
- `setTrustedDaemon(false)` is set before and after LWS login, including failure paths. A
  LWS login failure does not fall back to a full node.
- Tor hides the network path, not timing, amounts, sync patterns, the use of Tor, or
  guarantee resistance to traffic correlation. I2P and VPN have their own, narrower
  properties and are not interchangeable with Tor. VPN and clearnet custom-proxy use are
  not equivalent to onion/I2P destinations.
- `AndroidManifest.xml` permits cleartext traffic for node/LWS compatibility. That flag is
  not a proxy bypass or a TLS guarantee; Android will not enforce HTTPS for those endpoints.
  The app-level transport rule keeps wallet traffic on the selected path.

## 10. Local anonymity, stealth, decoy, and duress

Stealth mode is a casual local-exposure reduction:

- redacted notification title/body;
- hidden amounts;
- auto-lock on `onStop` when enabled;
- no persisted logs;
- `FLAG_SECURE` for recents and screenshots.

It is not network anonymity, transaction unlinkability, or plausible deniability, and does
not defeat root, accessibility services, or a camera.

The decoy is a separate wallet file (`anon-alt`) with a separate Keystore secret, PIN hash,
passphrase hash, and restore height. `WalletSession.profile` is not persisted; every process
start begins at MAIN and the entered PIN selects the profile. `resolvePin` always performs
both PIN derivations (using a dummy derivation when no decoy exists) so timing does not
reveal whether a duress PIN is configured.

Wiping from the decoy removes only the decoy profile, its Keystore secret, hashes, and
restore height; it does not delete or lock MAIN. The decoy is **not** plausible deniability:
root/forensic access can discover the file, alias, and PIN hash, and network metadata may
correlate the two profiles.

## 11. Logging, clipboard, and notifications

- Logs are persisted in DataStore and are user-shareable. Never log seeds, passphrases,
  PINs, wallet passwords, Keystore material, node credentials, or proxy credentials.
- No analytics, telemetry, crash-reporting SDK, remote config, or tracking identifiers.
  Do not add a client-side identifier that can link sessions or devices.
- Android backup is disabled (`allowBackup=false`); both backup rule files exclude all
  domains. Do not re-enable cloud/device-transfer backup for wallet data.
- `AnonLogTree` persists info and above in release builds, debug and above in debug builds,
  and nothing in stealth mode.
- `SecureClipboard` marks copied text as sensitive on API 33+ and clears it after 60 seconds
  only if it is still the primary clip. Clipboard managers and the OS may retain history.
- The `FileProvider` exposes only `cache/logs`, `cache/backup`, and `cache/exports`. Never add
  wallet files, keys, or Keystore material to its paths.
- Notifications in stealth mode contain no balances, heights, addresses, transaction counts,
  or product name.

## 12. Known issues and limitations

These are current limitations. Items fixed in the latest development cycle are listed
separately below.

1. **Monero patch series licensing and native validation.** Seven wallet-API patches
   adapted from `monero_c` are licensed under LGPL-3.0; the project-original
   `0008-enforce-tls-for-https-daemon-address.patch` is Apache-2.0 (see
   `external-libs/patches/README.md`). CI builds the native archives; local compilation is
   not permitted. Treat runtime TLS and wallet behavior as unvalidated until the manual
   `native` and device jobs pass.
2. **No dependency lockfile/verification metadata.** Gradle resolution is not locked, and
   the wrapper JAR is the only checked-in binary build artifact. Supply-chain verification
   relies on pinned versions, the wrapper checksum, and CI. See `dependencies.md`.
3. **Node credentials are not Keystore-encrypted.** They are stored in app-private
   SharedPreferences/DataStore. The app sandbox is the protection boundary; a forensic copy
   of app data exposes them.
4. **LWS metadata exposure.** The view key and incoming transaction metadata are disclosed to
   the LWS. The selected transport does not hide them from the server.
5. **Decoy is not plausible deniability.** See section 10.
6. **Stealth is not anonymity.** See section 10.
7. **Legacy proxy validation now matches Network & Nodes.** The old `ProxySettings` screen
   delegates to `ProxyEndpoint.isNumericOrLoopback`, so IPv4, compressed IPv6, and loopback
   names are accepted consistently. This is still a usability boundary, not a clearnet bypass.
8. **Background-sync failure is not a proof of spend-key removal.** The UI remains locked,
   but if wallet2 refuses `startBackgroundSync`, the native wallet may still hold the spend
   key. Restarting the process or a later successful `stopBackgroundSync` is the recovery
   path.
9. **No runtime validation.** The Android, native, cryptographic, Tor/LWS, and UI behavior
   described here has been inspected statically. CI and device tests remain authoritative.
10. **No measured anonymity.** Traffic correlation, endpoint compromise, VPN logging, and
    LWS metadata exposure are not quantified. The transport design reduces linkability; it
    does not prove anonymity against a capable observer.

### Fixed in the current development cycle

- Failed wallet creation/restore now removes the partial MAIN profile and wrapped secret, so
  `RestoreTarget.requireEmpty` does not block a retry.
- Restoring a v1 node fallback now adds the node to the saved-node list.
- A broadcast cannot post success without a pending transaction; post-broadcast refresh
  failures no longer turn a sent payment into an error state.
- Backup creation clears only transient backup/airgapped artifacts, not the live Tor cache.
- NERO view-only restore honors the payload restore height, rejects negative heights, and
  uses the shared preference key constant. Backup restore rejects a negative restore height
  before writing credentials.
- WalletManager rejects a zero native handle and failed status/password setup instead of
  constructing a wrapper that would dereference null.
- Opening a new wallet closes any previous managed wallet first; a failed open closes the
  invalid wallet before reporting an error.
- JNI creation/open/recovery/init paths check null strings and null wallet pointers, and the
  string/array helpers fail closed instead of dereferencing null.
- The Tor SOCKS reference is volatile, and restart clears the cached endpoint before the
  new listener is published. `SigningSummary` has JVM coverage for review parsing.

## 13. Review rules

1. Identify the asset, the adversary, and the linkability/metadata exposure.
2. Trace the data flow across every boundary.
3. Check failure behavior; it must fail closed.
4. Check profile isolation, local exposure, and secret redaction.
5. Check the persisted format and migration path.
6. Check the JNI handle/lifetime contract if native code is involved.
7. Require CI evidence; do not present static review as anonymity or security proof.

## 14. Related documents

- [`architecture/overview.md`](architecture/overview.md)
- [`architecture/jni-boundary.md`](architecture/jni-boundary.md)
- [`features.md`](features.md)
- [`build-and-release.md`](build-and-release.md)
- [`architecture/repository-state.md`](architecture/repository-state.md)
- [`architecture/dependencies.md`](architecture/dependencies.md)


## November 2026 source hardening

[Audit and residual risks](architecture/anonymity-audit-2026-11.md) record the source
findings, pre-edit threat models and required dynamic matrix. LWS settings/consent are
profile-specific (legacy MAIN preserved; DECOY disabled initially). Strict host/loopback
validation closes URL-network and TLS-exemption spoofing; stale transport authorization
and session callbacks are rejected at the reviewed boundaries. This does not prove native
socket/callback lifetime, per-wallet Tor circuits or VPN routing.

Persisted diagnostics are fixed reviewed warning/error events with generic fallback/tag,
minute timing and no hardware fingerprint; no debug logcat tree exists. Old diagnostic
files are removed at startup before consumers. Java crash forwarding uses a fixed exception;
native fixed errors/OS tombstones still remain outside Kotlin stealth suppression. Both
wallet notifications request SECRET lock-screen visibility; device policy can affect behavior.
No cryptographic or consensus rules were changed. Every future feature must answer what
information it reveals, what identities/connections/activities it can link, and how to reduce
that exposure without compromising funds.


## Continued lifecycle and UI hardening

Auxiliary wallets remain reachable after failed close and their backing files/credentials
are preserved until confirmed closure/recovery. Registry-guarded deletion serializes with
auxiliary opens/closes, but not the complete pre-registry Keystore provisioning phase.
Failed native snapshot conversion now raises an error rather than returning incomplete
coin/history data. Payment checks bind to exact inputs; freeze/thaw checks observed flags;
locked mutation guards and visible errors reduce stale-state decisions. Signing/verification
results are withheld after input/session changes. None of these establishes native lifetime,
cryptographic correctness, memory zeroization or fund safety. Retained tmp_backup files are
purged at process startup; failed password transitions and flash erasure remain unproven.

Saved-node diagnostics omit hosts and credentials. LWS startup and transport reapply
check that proxy acquisition did not outlive the wallet profile and operator settings
used to authorize login. A full lifecycle lease is still required to serialize changes
during native calls. See [Kotlin, JNI and transport hardening](development-iteration-modernization.md).

For LWS login, the adapter checks wallet2's trust flag after writing it.
Startup, reconfiguration and callback reconnect reject login or refresh when
the untrusted state cannot be confirmed. Device and native fault coverage is
tracked in [the LWS audit](ci/lws-session-reapply.md).
