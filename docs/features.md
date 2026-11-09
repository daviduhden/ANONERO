# Implemented features

This document describes behavior visible in the source. Runtime behavior still requires CI
and device validation. For design rationale and threat boundaries, see
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
  first sync. Tor is started by the app when a node/LWS is configured.

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

The airgapped flow uses the UR registry types in `AnonUrTypes`:

| Type | Purpose |
| --- | --- |
| `xmr-output` | Export/import outputs |
| `xmr-keyimage` | Export/import key images |
| `xmr-txunsigned` | Export/import an unsigned transaction |
| `xmr-txsigned` | Export/import a signed transaction |

`QRExchangeScreen` renders animated UR QR codes; `QRScanner`/`URQRScanner` assemble
fragments. Imported files are written to cache under the fixed names in `AnonConfig` and
cleaned at startup, after wipe, or by `AnonConfig.clearSpendCacheFiles`.

## 6. Message signing and verification

`SignVerifyScreen` uses wallet2's `signMessage`/`verifySignedMessage`. The message is encoded
as UTF-8 in Kotlin before crossing JNI so non-BMP text is signed exactly as displayed. A
valid signature proves possession of the signing key; it does not prove the identity of the
signer or that the message is truthful.

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

## 8. Network: Tor, daemon, and LWS

- **Tor** is embedded through `kmp-tor`. The wallet waits for a SOCKS endpoint and fails
  closed if it does not appear. The daemon is restarted after an unexpected stop while Tor
  is wanted.
- **Manual proxy** is available only when Tor is disabled. The proxy host must be a numeric
  IPv4/full IPv6 address and the port must be in `1..65535`.
- **Full node** mode connects wallet2 to a Monero daemon over the proxy. The daemon is marked
  trusted after initialization.
- **LWS** is optional, opt-in, and requires the built-in Tor endpoint. Host and port are
  stored separately; the host must be a DNS name, `.onion`, or IPv4 literal with no scheme,
  credentials, path, or embedded port, and the port must be in `1..65535` (default 8443). The
  server receives the public address and view key and is kept untrusted
  (`setTrustedDaemon(false)`) before and after login. LWS sync height may legitimately trail
  the chain; reconnect logic is separate from full-node height comparison.
- **Offline signer** mode is selected when ANON has no node host. It does not start network
  synchronization and is intended for airgapped signing.

## 9. Privacy features

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

These are privacy and coercion mitigations, not anonymity. See
[`security-model.md`](security-model.md) for limits.

## 10. Settings and localization

- Settings exposes theme, language, node, proxy, LWS, accounts, seed (ANON only), change
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
  series is restored but the archives have not been compiled here. See
  [`architecture/repository-state.md`](architecture/repository-state.md).
- Manual proxy validation rejects compressed IPv6 forms.
- The decoy is separation of wallet files and secrets, not plausible deniability.
- Stealth mode does not defeat root, accessibility services, or screen capture.
- LWS sees the view key and incoming transaction metadata; Tor does not hide that from the
  server.
- No Gradle dependency lockfile or verification metadata is checked in.
- Runtime/device behavior has not been validated locally; CI is authoritative.
