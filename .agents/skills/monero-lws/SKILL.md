---
name: monero-lws
description: "Use when changing the optional Monero Light Wallet Server integration: LWS configuration and validation, login, trust policy, sync behavior, or the LWS settings UI. Distinguishes privacy requirements from connectivity requirements."
---

# Monero Light Wallet Server integration

## When to use

- Adding, changing, or reviewing LWS configuration, validation, save/load, or UI.
- Changing light-wallet login, trust, sync, or reconnect behavior.
- Auditing whether LWS traffic can leave Tor or whether the server is treated as
  trusted.
- Explaining what an LWS operator can see.

Use `tor-networking` for the underlying SOCKS/Tor mechanics.

## Read first

- `AGENTS.md`
- `app/src/main/java/io/anonero/util/PrivacySettings.kt` (`LwsConfig`,
  `LwsValidationError`)
- `app/src/main/java/io/anonero/ui/home/settings/LwsSettings.kt`
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
- LWS traffic must use the built-in Tor SOCKS endpoint. A manual/clearnet proxy is not
  acceptable, even if it works.
- The LWS must be treated as an untrusted remote index:
  `setTrustedDaemon(false)` before login and after login, including failure paths.

**Connectivity requirements**

- Host and port are stored separately; host is a DNS name, `.onion` address, or IPv4
  literal.
- Reject scheme, credentials, path, whitespace, embedded port, and invalid ports.
- Default port is 8443.
- Changing configuration requires a restart/re-login; do not pretend it applies live.
- Light-wallet sync height may legitimately trail the chain; do not apply the full-node
  height-difference reconnect rule to it.

## Invariants

- Only `address` + `viewKey` are sent to the server. Do not add fields, headers, or
  identifiers that increase linkability.
- `setTrustedDaemon(false)` before and after `lightWalletLogin()`. A failed login must
  leave trust false.
- LWS requires `WALLET_USE_TOR == true` and the actual built-in Tor endpoint. Reject a
  manual proxy for LWS.
- `LwsConfig.validateHost` must remain strict: non-empty, <= 253 chars, no `://`, `/`,
  `@`, space, or `:`; allowed characters are alphanumeric, dot, and hyphen with
  alphanumeric ends.
- `LwsConfig.validatePort` must remain 1..65535.
- Do not log the host together with view-key material or wallet addresses in sensitive
  paths.
- Do not silently fall back to a full node if LWS login fails.
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
4. **Preserve Tor.** Require `WALLET_USE_TOR` and the built-in SOCKS endpoint. Do not
   accept a user-entered proxy for LWS.
5. **Validate configuration.** Use `LwsConfig.validate`; add stable error codes, not
   raw exception text, for UI.
6. **Handle reconnect.** `MoneroHandlerThread.tryRestartConnection` re-inits and
   re-logs in for light wallets. Keep the trust flag false.
7. **Update UI text.** The privacy warning and Tor requirement are user-facing
   security disclosures; keep them accurate and translated.
8. **Test pure validation in JVM.** Extend `LwsConfigTest` only in CI.

## Static checks

```sh
grep -rn "setTrustedDaemon" app/src/main/java/io/anonero
grep -rn "lightWalletLogin\|isLightWallet\|lightWallet" app/src/main/java/io/anonero
grep -rn "LwsConfig\|PREFS_LWS" app/src/main/java/io/anonero
grep -rn "requires Tor\|LWS requires" app/src/main/java/io/anonero
grep -rn "setDaemon\|setProxy" app/src/main/java/io/anonero/services/AnonWalletHandler.kt
python3 scripts/check-i18n.py
```

## Completion criteria

- [ ] View key/spend key handling is unchanged and documented.
- [ ] Trust is false before and after login, including failures.
- [ ] LWS cannot use a manual/clearnet proxy.
- [ ] Host/port validation remains strict.
- [ ] No full-node fallback on LWS failure.
- [ ] Light-wallet sync semantics preserved.
- [ ] UI discloses server trust and Tor requirement in all locales.
- [ ] Runtime behavior marked unverified until CI/device testing.
