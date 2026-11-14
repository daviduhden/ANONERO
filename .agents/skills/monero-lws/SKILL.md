---
name: monero-lws
description: "Use when changing the optional Monero Light Wallet Server integration: LWS configuration and validation, login, trust policy, sync behavior, or the LWS settings UI. Distinguishes privacy requirements from connectivity requirements."
---

# Monero Light Wallet Server integration

## When to use

- Adding, changing, or reviewing LWS configuration, validation, save/load, or UI.
- Changing light-wallet login, trust, sync, or reconnect behavior.
- Auditing whether LWS traffic uses the selected transport or whether the server is
  treated as trusted.
- Explaining what an LWS operator can see.

Use `tor-networking` for the shared transport, proxy, VPN, and fail-closed mechanics.

## Read first

- `AGENTS.md`
- `app/src/main/java/io/anonero/util/PrivacySettings.kt` (`LwsConfig`,
  `LwsValidationError`)
- `app/src/main/java/io/anonero/network/TransportModels.kt` and
  `TransportController.kt` (`TransportConfig`, `NodeNetwork`, `validateTransport`)
- `app/src/main/java/io/anonero/ui/home/settings/LwsSettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/NetworkSettings.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/services/MoneroHandlerThread.kt`
- `app/src/main/java/io/anonero/model/Wallet.kt`
- `app/src/main/java/io/anonero/ui/home/settings/ProxySettings.kt`
- `app/src/test/java/io/anonero/util/LwsConfigTest.kt`

## Privacy vs connectivity

Keep these separate in design and review:

**Privacy requirements**

- LWS sees the wallet’s public address and private view key. It can see incoming
  transactions, amounts, and approximate timing, and can lie about chain state.
- The spend key never leaves the device.
- LWS traffic uses the transport selected in `TransportController`, through the same
  wallet2 SOCKS/direct setting as RPC. Do not add a separate LWS proxy or a second HTTP
  client.
- The destination must match the transport network: Tor modes reach `.onion`, I2P modes
  reach `.i2p`, system VPN reaches clearnet, and a custom proxy reaches the network the
  user explicitly configured. A mismatch fails closed.
- Non-loopback clearnet LWS requires TLS (`https://`). `.onion` and `.i2p` endpoints rely
  on their transport.
- The user must consent before the view key is sent to a different LWS server. Never
  silently switch LWS operators; auto-reconnect to the already authorized server is fine.
- The LWS must be treated as an untrusted remote index:
  `setTrustedDaemon(false)` before login and after login, including failure paths.

**Connectivity requirements**

- Host, port, and TLS flag are stored separately; host is a DNS name, `.onion` address,
  `.i2p` address, or IP literal.
- Reject scheme, credentials, path, whitespace, embedded port, and invalid ports.
- Default port is 8443; default scheme is `http://` for Tor/I2P and must be `https://` for
  non-loopback clearnet.
- `LwsConfig.validateTransport(config, transport)` is the single compatibility gate,
  shared with the settings UI, `AnonWalletHandler`, and the transport-change path.
- Changing configuration applies through `reapplyLightWallet()` (stop refresh, re-apply
  proxy, `wallet.init(0, lightWallet = true)`, login, restart refresh) or a full restart;
  do not pretend an endpoint change applies to in-flight requests.
- Light-wallet sync height may legitimately trail the chain; do not apply the full-node
  height-difference reconnect rule to it.

## Invariants

- Only `address` + `viewKey` are sent to the server. Do not add fields, headers, or
  identifiers that increase linkability.
- `setTrustedDaemon(false)` before and after `lightWalletLogin()`. A failed login must
  leave trust false.
- `LwsConfig.validateTransport` must remain the only transport gate. Do not re-introduce
  `mode.usesEmbeddedTor` checks around LWS.
- `LwsConfig.validateHost` must remain strict: non-empty, <= 253 chars, no `://`, `/`,
  `@`, space, or `:`; allowed characters are alphanumeric, dot, and hyphen with
  alphanumeric ends.
- `LwsConfig.validatePort` must remain 1..65535.
- Clearnet without TLS is accepted only for loopback (`localhost`, `127.0.0.0/8`, `::1`).
- Do not log the host together with view-key material or wallet addresses in sensitive
  paths.
- Do not silently fall back to a full node if LWS login fails, and do not silently fall
  back to another LWS operator.
- Do not make LWS mandatory. It is optional and opt-in.
- Do not enable LWS in the `nero` view-only build unless the product decision is
  explicit; the current code supports the mode but the settings path and trust rules
  still apply.

## Procedure

1. **Classify the change.** Is it a privacy rule, a connectivity rule, or UI-only?
2. **Trace startup.** `AnonWalletHandler.startService` loads `LwsConfig`, overrides the
   daemon host/port, sets offline state, applies the proxy, constructs a `Node`,
   `wallet.init(0, lightWallet = true)`, logs in, and starts refresh.
3. **Preserve trust.** Set false before login and in every catch/failure path. Do not
   move `setTrustedDaemon(true)` into a shared path.
4. **Preserve transport binding.** Load `TransportConfig`, call
   `LwsConfig.validateTransport`, and let `TransportController` produce the endpoint. Do
   not call `TorService` from LWS and do not start embedded Tor for an external provider.
5. **Validate configuration.** Use `LwsConfig.validate` and `validateTransport`; add
   stable error codes, not raw exception text, for UI.
6. **Handle reconnect.** `MoneroHandlerThread.tryRestartConnection` re-inits and
   re-logs in for light wallets. `AnonWalletHandler.reapplyLightWallet()` is the
   transport-change path. Keep the trust flag false in both.
7. **Handle transport changes.** Pause refresh, apply the new proxy, re-init LWS, log in,
   and restart refresh. If the new transport is incompatible, keep the wallet offline and
   surface the error; never connect over the previous transport.
8. **Update UI text.** The privacy warning, network compatibility status, TLS switch, and
   consent dialog are user-facing security disclosures; keep them accurate and
   translated.
9. **Test pure validation in JVM.** Extend `LwsConfigTest` and the transport tests only in
   CI.

## Static checks

```sh
grep -rn "setTrustedDaemon" app/src/main/java/io/anonero
grep -rn "lightWalletLogin\|isLightWallet\|lightWallet" app/src/main/java/io/anonero
grep -rn "LwsConfig\|PREFS_LWS" app/src/main/java/io/anonero
grep -rn "usesEmbeddedTor\|requires Tor\|LWS requires" app/src/main/java/io/anonero
grep -rn "setDaemon\|setProxy\|reapplyLightWallet" app/src/main/java/io/anonero/services/AnonWalletHandler.kt
python3 scripts/check-i18n.py
```

## Completion criteria

- [ ] View key/spend key handling is unchanged and documented.
- [ ] Trust is false before and after login, including failures.
- [ ] LWS uses the selected transport and rejects a destination from another network.
- [ ] Non-loopback clearnet LWS requires TLS.
- [ ] No LWS path calls `TorService` directly or starts embedded Tor for external modes.
- [ ] Host/port/scheme validation remains strict.
- [ ] No full-node or different-operator fallback on LWS failure.
- [ ] User consent precedes first view-key disclosure to a new server.
- [ ] Light-wallet sync semantics preserved.
- [ ] UI discloses server trust, transport network, and TLS requirement in all locales.
- [ ] Runtime behavior marked unverified until CI/device testing.
