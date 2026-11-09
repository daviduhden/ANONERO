---
name: tor-networking
description: "Use when changing ANONERO network connectivity: embedded Tor, SOCKS proxy application, manual proxy, daemon or LWS routing, or fail-closed networking behavior. Use monero-lws for LWS protocol and trust details."
---

# Tor and network privacy

## When to use

- Starting, stopping, restarting, or observing the embedded Tor daemon.
- Applying a SOCKS or manual proxy to wallet2.
- Changing daemon/LWS routing or the Tor splash/readiness flow.
- Reviewing a change for a clearnet fallback, DNS leak, or silent privacy downgrade.

Use `monero-lws` in addition when the change is LWS-specific.

## Read first

- `AGENTS.md`
- `app/src/main/java/io/anonero/services/TorService.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/services/MoneroHandlerThread.kt`
- `app/src/main/java/io/anonero/ui/TorSplash.kt`
- `app/src/main/java/io/anonero/ui/home/settings/ProxySettings.kt`
- `app/src/main/java/io/anonero/util/PrivacySettings.kt`
- `app/src/main/AndroidManifest.xml`

## Invariants

- `WALLET_USE_TOR` defaults to `true`. Do not change that default.
- Every wallet/daemon/LWS connection must go through the embedded Tor SOCKS endpoint
  or an explicitly configured manual proxy. There is no clearnet fallback.
- `AnonWalletHandler.applyProxy()` waits up to 60 seconds for a SOCKS endpoint and
  throws on timeout. A startup timeout in `TorSplash` must not authorize a direct
  connection; the wallet path still fails closed.
- Apply the proxy to both `WalletManager` and the live `Wallet`; if either rejects it,
  pause refresh and surface a failure.
- LWS requires the built-in Tor endpoint. A manual proxy is not acceptable for LWS.
- Manual proxy is only allowed when Tor is disabled; validate numeric IPv4/IPv6 and
  port range. Do not accept hostnames that would require a DNS lookup before the
  proxy.
- `TorService` owns a single `TorRuntime`; `wanted` distinguishes an intentional stop
  from a crash. On an unexpected stop, clear the cached SOCKS endpoint and retry after
  ten seconds while still wanted.
- A stale SOCKS port must never remain usable after Tor dies. Clear it before restart.
- Do not add a new HTTP client, socket, DNS resolver, or download path that bypasses
  wallet2’s proxy setting.
- Do not log Tor control data or wallet credentials. SOCKS host/port is not a secret,
  but do not add new endpoint logging in sensitive paths.
- `usesCleartextTraffic="true"` exists for node/LWS compatibility; it is not permission
  to bypass the proxy.

## Procedure

1. **Trace the connection.** Identify the caller, the destination, and where the proxy
   is applied. Confirm the path goes through `wallet2`, not an independent client.
2. **Preserve readiness semantics.** Wait for `socksFlow`/`socks`, do not poll a stale
   value, and re-apply when Tor restarts.
3. **Preserve failure semantics.** On Tor failure, pause refresh, mark disconnected,
   and return an error. Do not set a fallback proxy or connect directly.
4. **Handle restart.** If the daemon dies, the cached endpoint must be cleared and the
   wallet must be re-proxied when the new endpoint appears.
5. **Keep LWS on Tor.** If the change touches LWS, require `WALLET_USE_TOR` and the
   actual built-in SOCKS endpoint, not just a truthy flag.
6. **Validate manual proxy input.** Numeric address, port 1..65535, no scheme, no
   credentials in the host.
7. **Test in CI/device.** Tor behavior, restart, and network transitions cannot be
   proven statically.

## Threat model

- Tor hides the network path from the destination and local network observers. It does
  not hide timing, transaction amounts, wallet sync patterns, or the fact that Tor is
  used.
- A malicious or compromised daemon/LWS can lie about chain state. LWS additionally
  sees the view key and incoming transaction metadata.
- A proxy bypass leaks the user’s IP to the node/LWS. Treat it as a critical defect.
- A DNS lookup before the proxy can leak the destination. Manual proxy input is
  numeric for this reason.

## Static checks

```sh
grep -rn "OkHttp\|HttpURLConnection\|URL(\|Socket(\|DatagramSocket\|InetAddress" app/src/main/java
grep -rn "setProxy\|applyProxy\|WALLET_USE_TOR\|WALLET_PROXY" app/src/main/java
grep -rn "setTrustedDaemon\|lightWalletLogin" app/src/main/java
grep -rn "TorSplash\|socksFlow\|socks" app/src/main/java
grep -rn "usesCleartextTraffic" app/src/main/AndroidManifest.xml
```

Any new network client outside wallet2 is a red flag unless it is explicitly Tor-only
and reviewed as a new trust boundary.

## Completion criteria

- [ ] No clearnet fallback.
- [ ] Proxy applied to manager and live wallet.
- [ ] Fail-closed on Tor/LWS failure.
- [ ] Stale SOCKS port cannot be reused.
- [ ] Manual proxy is numeric and only used with Tor disabled.
- [ ] LWS uses built-in Tor.
- [ ] No new unproxied network client or DNS path.
- [ ] Runtime/network behavior marked unverified until CI/device testing.
