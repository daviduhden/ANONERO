---
name: tor-networking
description: "Use when changing ANONERO network connectivity: embedded/external Tor, I2P, system VPN, SOCKS5 proxies, daemon or LWS routing, or fail-closed networking behavior. Use monero-lws for LWS protocol and trust details."
---

# Tor and network anonymity

## Anonymity hierarchy

Network anonymity is the primary goal of transport policy. Tor, I2P, VPN, and custom
proxies are not equivalent:

- Tor modes target `.onion` services and are the preferred path.
- I2P modes target `.i2p` services with I2P's separate threat model.
- System VPN is a clearnet mode that shifts trust to the VPN operator; it is not network
  anonymity.
- A custom SOCKS5 proxy provides only what its operator provides and is not anonymity by
  itself.

Do not add a path that silently bypasses the selected transport. Do not describe VPN or
clearnet custom-proxy use as anonymous or equivalent to onion/I2P.

## When to use

- Starting, stopping, restarting, or observing the embedded Tor daemon.
- Selecting a transport and applying its SOCKS5 proxy or direct VPN path to wallet2.
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

- Embedded Tor is the default (`TransportMode.EMBEDDED_TOR`). Do not change the default.
- `TransportController` is the single policy owner. Allowed modes: embedded Tor,
  Orbot, InviZible Tor, system VPN, i2pd, InviZible I2P, official I2P Java, custom SOCKS5. Tor/I2P/custom
  require their proxy and never fall back to clearnet.
- `AnonWalletHandler.applyProxy()` acquires the endpoint from `TransportController` and
  applies it to both `WalletManager` and the live `Wallet`. Embedded Tor waits up to
  60 seconds; a `TorSplash` timeout must not authorize a direct connection.
- System VPN is the only clearnet mode. Require `TRANSPORT_VPN`; on loss set the wallet
  offline and pause refresh. Never fall back to the default network.
- LWS uses the same selected transport as RPC. Reject an LWS destination whose network
  does not match the transport (`.onion` on Tor, `.i2p` on I2P, clearnet on system VPN or
  an explicit custom-clearnet proxy). Non-loopback clearnet LWS requires TLS.
- Proxy endpoints are numeric/loopback and validated for port 1..65535. External
  Tor/I2P modes must use loopback. Do not accept a hostname that requires DNS before
  the proxy.
- wallet2 receives an explicit `socks5://` URI so `.onion`/`.i2p` resolution happens at
  the proxy. Do not rely on the default SOCKS4a format for I2P.
- `TorService` owns the embedded `TorRuntime` only; external modes never start it.
  `wanted` distinguishes an intentional stop from a crash. On an unexpected stop, clear
  the cached SOCKS endpoint and retry after ten seconds while still wanted.
- A stale SOCKS port must never remain usable after Tor dies. Clear it before restart.
- Transport changes release the old provider, stop embedded Tor when leaving it, clear
  incompatible proxy state, and re-select a node on the new network.
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
5. **Keep LWS on the selected transport.** Use `LwsConfig.validateTransport` and let
   `AnonWalletHandler.reapplyLightWallet()` apply the endpoint on transport changes. LWS
   must not obtain a proxy from `TorService` directly, and must not start embedded Tor
   when an external provider is selected.
6. **Validate proxy input.** Numeric address or loopback, port 1..65535, no scheme or
   credentials in the host. External Tor/I2P endpoints must be loopback.
7. **Validate node network.** `.onion` only on Tor modes, `.i2p` only on I2P modes,
   clearnet only on system VPN/custom-clearnet. Manual and automatic selection share
   `NodeSelectionPolicy`.
8. **Test in CI/device.** Tor/I2P/VPN behavior, restart, and transport transitions cannot
   be proven statically.

## Threat model

- Tor hides the network path from the destination and local network observers. It does
  not hide timing, transaction amounts, wallet sync patterns, the fact that Tor is used,
  or guarantee resistance to traffic correlation.
- I2P provides a different anonymity model and is not interchangeable with Tor. A VPN
  hides the path from the destination but exposes it to the VPN operator.
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
- [ ] LWS uses the selected transport and rejects cross-network destinations.
- [ ] VPN and custom-proxy modes are documented as trust shifts, not network anonymity.
- [ ] No new unproxied network client or DNS path.
- [ ] Runtime/network behavior marked unverified until CI/device testing.


The November source guards validate full numeric proxy literals without InetAddress,
canonicalize localhost, validate complete RPC hosts in both selection gates, invalidate
missing Tor listeners and reject stale suspended authorization publication. Keep cancellation
propagation. These guards cannot revoke an endpoint already returned, prove socket closure
or provide authenticated SOCKS circuit isolation; see `docs/network-anonymity-audit.md`.


Onboarding ProxySettings delegates to the common editor; do not restore handler-level
manual proxy mutation or independent legacy preference writes. TransportConfig.save must
report Editor.commit failure; controller keeps the prior authoritative config, attempts
network-only restoration and drops authorization. VPN loss invalidates generations; VPN
reappearance alone does not authorize traffic. Preference-only changes preserve the current
provider. These guards do not prove old native sockets have closed or per-app VPN routing.

Probe external SOCKS5 endpoints with a bounded no-auth greeting and no destination request. A matching reply proves protocol readiness at that instant only, not provider identity or DNS safety.

## Maintenance & Continuous Learning

When this subsystem changes, reconcile this procedure with the implementation and
record reusable constraints, root causes and remaining verification limits. Check the
closest AGENTS.md and the skills index for stale guidance. Run the read-only
`python3 scripts/validate-agent-knowledge.py --scope worktree` before review; inspect
AK004 warnings for relevance and run regression tests only in authorized CI.
