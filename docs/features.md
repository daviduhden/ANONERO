# Implemented features

This document describes behavior visible in the source. Runtime behavior still requires CI
and device validation. ANONERO is an anonymity-focused wallet; privacy and security are
required pillars. For design rationale and threat boundaries, see
[`security-model.md`](security-model.md) and
[`architecture/overview.md`](architecture/overview.md).

## 1. Product flavors

| Flavor | Application ID | Capability |
| --- | --- | --- |
| `anon` | `io.anonero.anon` | Full wallet: spend, accounts, decoy provisioning, encrypted backup |
| `nero` | `io.anonero.nero` | View-only: watch balances/history, build/handle unsigned and signed transactions; no spend key, seed view, or decoy provisioning |

The `stageNet` build type adds a `.stagenet` suffix and is debuggable. It is not a release
artifact. Network selection is derived from the application ID
(`AnonConfig.getNetworkType`): stagenet when the ID contains `stagenet`, otherwise mainnet.

## 2. First run and wallet provisioning

The onboarding graph supports:

- **Create wallet.** Generates a Polyseed wallet, requires a passphrase, sets a PIN, and
  stores a random Keystore-wrapped wallet password. The PIN never encrypts the wallet.
- **Restore from seed.** Accepts 16-word Polyseed and 25-word legacy mnemonics, normalizes
  Unicode separators, validates word count/height, and passes the mnemonic to wallet2.
- **Restore from encrypted backup.** Selects a backup, decrypts it, validates the archive
  layout and network, previews the metadata, and rebinds the restored files to this device.
- **Restore from keys.** NERO imports an address and private view key and creates a
  view-only wallet using the restore height supplied in the payload.
- **Node/proxy setup.** Configures a daemon or LWS endpoint and the network path before the
  first sync. Embedded Tor is started by the app only when it is the selected transport;
  external Tor, I2P, and custom providers must already be running.

`RestoreTarget.requireEmpty` blocks a new MAIN wallet when files, a wrapped secret, or
credentials already exist. It refuses symlinked or partially present targets rather than
overwriting them.

## 3. Home, accounts, and subaddresses

- The home graph starts at the lock screen unless the managed wallet is already open and
  healthy. The bottom destinations are Transactions, Receive, Send, and Settings.
- **Accounts** are subaddress accounts inside one wallet file, not separate wallets. The
  active account controls balances, history, coins, and transaction construction.
- **Subaddresses** are created on demand with labels. The receive screen uses the latest
  subaddress and does not intentionally reuse old ones.
- **Receive** displays the active account/subaddress, supports QR display, and copies
  addresses through `SecureClipboard`.
- **Transaction history** shows incoming/outgoing/pending transactions, confirmation count,
  notes, and details. Notes are stored through wallet2 and reloaded with history.

## 4. Sending and coin control

- **Send** builds a wallet2 pending transaction for a single destination. It can sweep all
  spendable outputs or use selected UTXOs.
- **Pay to many** builds one wallet2 transaction set with multiple destinations and exact
  atomic amounts. `MoneroAmount.parseExact` rejects malformed, zero, over-precise, and
  overflowing values before construction.
- **Coin control** lists unspent outputs, supports freeze/thaw by output public key, and
  passes selected key images to wallet2 for construction.
- Pay-to-many reduces the number of transactions but links its outputs in one set. Coin
  control can reduce unintended input linkage only when used correctly; a poor manual
  selection can worsen linkability. Neither is an anonymity guarantee.
- **Review** displays amount, fee, total, txid (when available), and destination rows. For
  imported unsigned transactions, rows come from wallet2's `confirmationMessage`; if it
  cannot be parsed, the raw sentence is shown. The advisory destination/amount getters are
  not used as a review source.
- **Broadcast** commits a pending transaction or submits a signed file. The UI does not
  return to the filled send form after a successful broadcast.

### View-only sending

NERO has no spend key. Its flow can build an unsigned transaction (importing key images
when needed), export it over QR/UR, import a signed transaction, and submit the signed file.
It cannot sign with the wallet spend key.

## 5. Airgapped QR/UR exchange

The airgapped flow uses the UR registry types in `AnonUrRegistryTypes`:

| Type | Purpose |
| --- | --- |
| `xmr-output` | Export/import outputs |
| `xmr-keyimage` | Export/import key images |
| `xmr-txunsigned` | Export/import an unsigned transaction |
| `xmr-txsigned` | Export/import a signed transaction |

`QRExchangeScreen` renders animated UR QR codes; `QRScanner`/`URQRScanner` assemble
fragments. Imported files are written to cache under the fixed names in `AnonConfig` and
cleaned at startup, after wipe, or by `AnonConfig.clearSpendCacheFiles`.

Airgapping keeps signing keys away from the networked device and can reduce key-exposure
and identity-correlation risk when the workflow is disciplined. It does not hide on-chain
data, the eventual broadcast path, or a compromised signer/recipient.

## 6. Message signing and verification

`SignVerifyScreen` uses wallet2's `signMessage`/`verifySignedMessage`. The message is encoded
as UTF-8 in Kotlin before crossing JNI so non-BMP text is signed exactly as displayed. A
valid signature proves possession of the signing key; it does not prove the identity of the
signer or that the message is truthful. It contributes authenticity, not anonymity.

## 7. Backups

- The current writer exports the active MAIN wallet (never the decoy), re-keys a copy to the
  seed passphrase, zips the flat wallet layout plus `anon.json`, and encrypts it with the v3
  AEAD envelope.
- v1 (`Salted__`, CBC) and v2 (AEAD, PIN-keyed wallet files) remain readable.
- The archive reader validates every entry before writing, rejects symlinks, duplicate
  names, traversal, and existing destinations, and removes a failed extraction tree.
- The backup preview shows metadata and network; a network mismatch aborts the restore.
- A restored wallet is re-keyed to a fresh device secret and bound to the new PIN. A failed
  bind removes only MAIN, preserving a configured decoy; backup node settings are applied
  only after the bind succeeds.

Backups protect wallet data at rest. They do not change on-chain privacy or network
anonymity, and a weak seed passphrase weakens the backup encryption.

## 8. Network: transports, anonymity, daemon, and LWS

- **Embedded Tor** is the default. The wallet waits for a SOCKS endpoint and fails closed if
  it does not appear. The daemon restarts after an unexpected stop while wanted.
- **External Tor** (Orbot or InviZible Pro) and **I2P** (i2pd, InviZible Pro, or the official I2P for Android app) use a
  user-configured local SOCKS5 endpoint. Ports are not assumed: the UI asks for host and
  port. `.onion` nodes are accepted only in Tor modes and `.i2p` nodes only in I2P modes.
- **System VPN** uses clearnet nodes only while Android reports `TRANSPORT_VPN`. It never
  starts embedded Tor and never falls back to direct traffic when the VPN disappears. It is
  not network anonymity; it shifts trust to the VPN operator and does not prove per-app
  routing or leak-free DNS.
- **Custom SOCKS5** requires an explicit destination network (clearnet, `.onion`, or
  `.i2p`); a generic proxy is not assumed to provide Tor/I2P anonymity.
- **Full node** mode connects wallet2 to a Monero daemon over the selected path. The daemon
  is marked trusted after initialization for full-node data.
- **LWS** is optional and opt-in. Host, port, and TLS scheme are stored separately; the
  destination must match the selected transport network (`.onion` on Tor, `.i2p` on I2P,
  clearnet on system VPN or an explicit custom-clearnet proxy). Non-loopback clearnet LWS
  requires TLS. The server receives the public address and view key and is kept untrusted
  before and after login. The first save of a new server shows a consent dialog; toggling
  LWS or changing its endpoint takes effect on restart, while a transport change
  re-applies the endpoint through the same `TransportController` path. LWS sync height
  may legitimately trail the chain.
- **Offline signer** mode is selected when ANON has no node host. It does not start network
  synchronization and is intended for airgapped signing.
- **I2P Java** detects the official `net.i2p.android` app but uses a user-created I2PTunnel SOCKS client tunnel; package presence does not imply a running tunnel. See [`architecture/network-transport.md`](architecture/network-transport.md).
- **Network & Nodes** exposes transport, proxy, automatic/manual node selection, active
  node, node network, LWS status, connection state, and anonymity/privacy/security
  warnings. See [`architecture/network-transport.md`](architecture/network-transport.md).

## 9. Anonymity, privacy, and coercion features

ANONERO's primary product goal is anonymity. The features below contribute in different
ways; they are not interchangeable and none is an anonymity guarantee.

| Feature | Primary contribution | Limits |
| --- | --- | --- |
| Tor/I2P transport | Network-path anonymity | Traffic correlation, endpoint compromise, service metadata |
| System VPN / custom proxy | Trust shift; may hide IP from the destination | Not anonymity; VPN/proxy operator sees the connection |
| No intentional subaddress reuse | Reduces on-chain linkability | Wallet behavior and external identity links remain |
| Coin control / freeze-thaw | Avoids unintended input linkage | User must understand selection; not automatic |
| Airgapped signing | Keeps keys off networked devices | Does not hide on-chain data; workflow discipline required |
| LWS | Faster scanning; optional external index | Server sees address/view key and incoming metadata |
| Stealth mode | Local visual/notification exposure | Not network anonymity; root/accessibility can defeat it |
| Decoy wallet / duress PIN | Coercion and physical-access mitigation | Not plausible deniability or network anonymity |
| Secure wipe | Reduces residual local data | Cannot erase backups, remote copies, or forensic traces |

- **Stealth mode** redacts notification title/body, hides amounts, can auto-lock on
  `onStop`, and suppresses persisted logs. `FLAG_SECURE` is always set regardless of stealth.
- **Decoy wallet** is a separate `anon-alt` wallet with its own Keystore secret, PIN hash,
  passphrase hash, and restore height. It is created from the main profile and cannot be
  configured from the decoy session or the view-only build.
- **Duress PIN** selects the decoy profile when entered on the lock screen. A duress PIN
  cannot equal the main PIN; a decoy passphrase cannot equal the main passphrase. PIN
  resolution performs both derivations to avoid timing disclosure.
- **Secure wipe** requires the seed passphrase, stops the foreground service, closes the
  active wallet, removes only that profile's files/secret/hashes, clears nodes/logs/cache,
  and resets the PIN throttle. A failed close aborts the wipe.
- **Secure clipboard** marks text sensitive on API 33+ and clears it after 60 seconds if it
  is still the primary clip.

These are local-exposure, privacy, and coercion mitigations. They do not provide network
anonymity, transaction unlinkability, or plausible deniability. See
[`security-model.md`](security-model.md) for limits.

## 10. Settings and localization

- Settings exposes theme, language, Network & Nodes (transport, proxy, node), LWS, accounts, seed (ANON only), change
  PIN, encrypted backup (non-decoy), decoy (ANON non-decoy), sign/verify, logs, and secure
  wipe. Subaddresses are reached from the receive flow.
- Supported UI locales: English, Spanish (Spain), Russian, and Simplified Chinese. The
  in-app selector applies the locale through `LocaleController`.
- Theme modes: system, light, dark. The Compose theme uses Material 3 and the project’s
  color/type tokens.
- Logs are capped, shareable, and suppressed in stealth mode. The Logs screen maps Android
  priorities to labels and colors.

## 11. Known functional limitations

- Native build/release validation still requires the manual CI `native` job; the patch
  series is restored and CI builds the native archives and debug APKs. See
  [`architecture/repository-state.md`](architecture/repository-state.md).
- Proxy endpoints must be numeric or loopback; external Tor/I2P proxies must be local. Both
  the legacy proxy screen and Network & Nodes use the shared numeric/loopback validator.
- The decoy is separation of wallet files and secrets, not plausible deniability.
- Stealth mode does not defeat root, accessibility services, or screen capture.
- VPN and clearnet custom-proxy modes are not network anonymity; Tor/I2P still face
  traffic correlation and endpoint compromise.
- No traffic-correlation, VPN-routing, or LWS-metadata measurement has been performed.
- LWS sees the view key and incoming transaction metadata; the selected transport does not
  hide that from the server.
- No Gradle dependency lockfile or verification metadata is checked in.
- Runtime/device behavior has not been validated locally; CI is authoritative.


## November 2026 hardening (source implemented, runtime pending)

MAIN and DECOY choose LWS independently; DECOY begins disabled and does not inherit MAIN's
operator consent. Reopening a wallet resets account/sync/UI shortcuts. Log sharing contains
reviewed fixed warnings/errors, coarse UTC times and API level, without hardware metadata;
unknown diagnostic text is withheld. Both wallet notifications request hidden lock-screen
content. Existing wallet, airgapped and alternative transport features remain; see the
[audit](architecture/anonymity-audit-2026-11.md) for compatibility and unverified limits.


## Continued feature integration (source only)

Accounts and output actions now reject locked sessions, prevent repeated UI actions and
show translated failures. Coin selections drop frozen/spent/missing outputs; freeze/thaw
checks wallet2's refreshed flag. Pay to Many handles changing recipient lists safely,
revalidates immutable submission inputs, prevents concurrent preparation and masks totals
when amounts are hidden. Message inputs are captured before work and stale results are
not published. Back navigation has accessible labels in the edited screens.
Decoy provisioning and backup exports require successful auxiliary close before success,
and incomplete decoys expose explicit removal/recovery. Legacy proxy setup offers the
same transport/network choices as Network & Nodes. See the
[feature-state table](development-iteration-2026-11.md); runtime behavior remains pending.
