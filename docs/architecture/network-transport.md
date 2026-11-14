# Network transport and node selection

This document describes the transport boundary introduced for ANONERO. It separates five
concerns that were previously implicit in `WALLET_USE_TOR` and a single manual proxy:

1. transport provider;
2. destination network;
3. proxy endpoint;
4. Monero RPC node selection;
5. wallet connection and synchronization.

The code lives in `app/src/main/java/io/anonero/network/` and
`services/AnonWalletHandler.kt`. Tor integration remains `services/TorService.kt`.

## Supported modes

| Mode | Provider | Proxy | Node network |
| --- | --- | --- | --- |
| `EMBEDDED_TOR` (default) | kmp-tor inside ANONERO | embedded SOCKS5 | `.onion` only |
| `ORBOT` | externally started Orbot | user-configured local SOCKS5 | `.onion` only |
| `INVIZIBLE_TOR` | InviZible Pro Tor module | user-configured local SOCKS5 | `.onion` only |
| `SYSTEM_VPN` | Android system VPN | none | clearnet only |
| `I2PD` | external i2pd router | user-configured local SOCKS5 | `.i2p` only |
| `INVIZIBLE_I2P` | InviZible Pro I2P module | user-configured local SOCKS5 | `.i2p` only |
| `I2P_JAVA` | official I2P for Android (`net.i2p.android`) | user-configured local SOCKS5 from its I2PTunnel | `.i2p` only |
| `CUSTOM_PROXY` | external SOCKS5 | user-configured, numeric host | explicit choice |

`TransportController` persists the mode and endpoints in `anonPref`. A legacy
`useTor`/`proxy`/`proxyPort` configuration is migrated: a valid legacy manual proxy
becomes `CUSTOM_PROXY`; otherwise the default is `EMBEDDED_TOR`. The legacy keys are kept
coherent for older readers, but `TransportConfig` is the authoritative policy.

External Tor/I2P modes require a loopback proxy address (`127.0.0.1`, `::1`, or
`localhost`). A remote SOCKS5 endpoint is not assumed to provide Tor or I2P. The custom
mode allows a numeric remote proxy, but the selected node network is the user's explicit
responsibility. Proxy username/password authentication is not exposed by the current UI;
wallet2 supports SOCKS5 userinfo, but ANONERO does not persist proxy credentials yet.

## Official I2P for Android (`i2p.android.base`)

The official I2P Android application is a separate app with package IDs
`net.i2p.android`, `net.i2p.android.donate`, and `net.i2p.android.router`. ANONERO
detects it through `PackageManager` with a narrow `<queries>` declaration. Detection
only enables setup instructions: package presence is **not** proof that the router or a
tunnel is running, and ANONERO never starts or configures the app.

The official router exposes I2CP on `127.0.0.1:7654` and, when enabled, SAM on
`127.0.0.1:7656`; SAM is disabled by default in Java I2P and 1.8.0+ uses interactive
authorization. The app's I2PTunnel supports SOCKS 4/4a/5 client tunnels. ANONERO uses a
user-created I2PTunnel SOCKS client tunnel because wallet2 accepts a SOCKS proxy, not
SAM/I2CP directly. Creating a SAM-to-SOCKS bridge would add a new network protocol and
interactive-auth flow; it is not implemented.

Setup required by the user:

1. Install I2P for Android from the official project or its listed distribution channels.
2. Start the router and create a client tunnel of type SOCKS in I2PTunnel.
3. Bind it to `127.0.0.1` and note the configured port. Ports are not assumed; do not use
   the desktop defaults unless the app shows them.
4. In ANONERO Network & Nodes select **I2P Java (Official Android App)**, enter that
   loopback host/port, and apply. Use only `.i2p` nodes.

If the package is absent, the UI explains the requirement and offers an official download
link; it never installs or launches the app. If the package is present but the tunnel is
missing or stopped, the endpoint probe fails closed with `EXTERNAL_PROXY_UNREACHABLE` and
the wallet stays offline. There is no Tor, VPN, or clearnet fallback.

## Proxy application

`TransportEndpoint.Socks5` is formatted as:

```
socks5://[ipv6]:port
socks5://ipv4:port
```

wallet2 defaults an unqualified `host:port` to SOCKS4a. ANONERO uses the explicit
`socks5://` scheme so `.onion`/`.i2p` names are sent to the proxy and
`set_connect_command(domain)` can resolve them remotely. This is important for I2P and
for IPv6 proxy endpoints.

`AnonWalletHandler.applyProxy()` acquires the endpoint and applies it to both the
wallet manager and the live wallet. `applyEndpoint()` also supports `Direct` for system
VPN mode: it clears the proxy on both native clients (`setProxy("")`). A manager/wallet
half-failure rolls the manager back to the previous endpoint and throws.

`WalletManager.rpcUrl` adds `http://` or the opt-in `https://` scheme to the selected
node or LWS endpoint. `https://` forces TLS through the pinned
`0008-enforce-tls-for-https-daemon-address.patch`: without it, wallet2's autodetect mode
would retry in cleartext when the TLS handshake fails. epee verifies the peer against the
system CA and performs an RFC 2818 hostname check. `http://` remains for `.onion`/`.i2p`
(protected by their transport) and loopback. epee does not follow HTTP redirects. Runtime
certificate behavior still requires CI/device validation.

## Light Wallet Server over the transport

LWS does not have its own connectivity stack. `LwsConfig` stores host, port, and a TLS
flag; `LwsConfig.address` produces `http://host:port` or `https://host:port`, and
`LwsConfig.validateTransport(config, TransportConfig)` is the single compatibility gate.
The host validator accepts DNS names, `.onion`, `.i2p`, and IPv4 literals; IPv6 literals
are not accepted yet and must be represented by a DNS name.

| Transport | LWS network |
| --- | --- |
| `EMBEDDED_TOR`, `ORBOT`, `INVIZIBLE_TOR` | `.onion` |
| `I2PD`, `INVIZIBLE_I2P`, `I2P_JAVA` | `.i2p` |
| `SYSTEM_VPN` | clearnet |
| `CUSTOM_PROXY` | `customNodeNetwork` |

`AnonWalletHandler.startService` and `reapplyLightWallet()` load the LWS configuration,
validate it against `TransportController.config`, call `applyProxy()` to apply the selected
endpoint to wallet2, set the daemon to the LWS address with the matching scheme, initialize
light-wallet mode, call `lightWalletLogin()`, and restart refresh. The LWS path never reads
`TorService` directly and never starts embedded Tor for an external provider.

On a transport change, `NetworkSettingsViewModel` pauses refresh, sets the wallet offline,
saves the new transport, and either re-applies an active LWS or re-selects a full node. An
incompatible active LWS destination keeps the wallet offline with a visible error instead
of reusing the previous transport. Disabling LWS while the wallet is light also requires a
restart; a transport change does not silently switch back to RPC. A new LWS server requires
explicit user consent before the view key is sent; a failed login never switches operator or
backend.

wallet2 keeps the LWS HTTP client internal: applying the wallet2 proxy is what routes LWS
traffic, and there is no second HTTP library to keep in sync. This is verified statically
only; TLS certificate handling and the per-transport LWS path require CI/device testing.

LWS and full-node RPC share the same wallet file. `wallet.init(..., lightWallet = true)`
switches the in-memory mode and daemon for LWS; switching back requires
`lightWallet = false` and a refresh. ANONERO does not rewrite the wallet format, does not
rewind the restore height, and does not switch from LWS to RPC (or the reverse) on login
failure. A user change is applied on restart or through `reapplyLightWallet()` for an
active LWS transport change; the UI states that an LWS endpoint or enable/disable change
needs a restart. A transport change while the wallet is still in light mode but LWS has
just been disabled is refused with a restart message instead of silently switching the
backend.

## Fail-closed rules

- Embedded Tor: wait up to 60 seconds for a SOCKS endpoint; never connect directly.
- External Tor/I2P/custom: probe the configured local proxy; a failure is
  `EXTERNAL_PROXY_UNREACHABLE`, not a direct fallback.
- System VPN: no active `TRANSPORT_VPN` means `VPN_INACTIVE`; ANONERO sets the wallet
  offline and pauses refresh. It never opens a direct connection as a fallback.
- Node/transport mismatch: both automatic selection and manual node validation reject a
  node whose host belongs to another network.
- I2P Java: package detection is separate from tunnel availability; a missing package or
  unreachable loopback tunnel fails closed. Only `.i2p` nodes are candidates.
- LWS: `LwsConfig.validateTransport` requires the LWS destination network to match the
  transport network. Tor modes accept only `.onion`, I2P modes only `.i2p`, system VPN only
  clearnet, and a custom proxy the network the user explicitly selected. A mismatch fails
  closed. The LWS endpoint is not treated as a full node. Non-loopback clearnet LWS
  requires TLS.
- Changing transport releases the previous provider, stops embedded Tor when leaving it,
  applies the new proxy, and re-selects a compatible node. Errors leave the wallet
  offline/disconnected.

## VPN limitation

`ConnectivityManager.getNetworkCapabilities(activeNetwork).hasTransport(TRANSPORT_VPN)`
only proves that a VPN network is the app's default network. It does not prove that the
VPN operator included ANONERO, that every socket is routed through it, or that DNS is
protected. Android supports per-app allow/disallow lists and `allowBypass()`. ANONERO
therefore treats VPN mode as an explicit user choice and fails closed on the absence of
the transport, but does not claim a leak-proof VPN guarantee. The UI must keep this
warning.

## Node catalog and selection

`Node` now exposes:

- `network`: `CLEARNET`, `ONION`, or `I2P`, derived from the host suffix;
- `hostPort`: bracket-aware IPv6 host and RPC port;
- `rpcUrl`: `http://` or `https://` endpoint used by wallet2;
- `useTls`: opt-in flag persisted in the node JSON.

`NodeSelectionPolicy` filters saved nodes by transport network and wallet network, then
selects a candidate (current compatible node, trusted preference, name/host order).
There is no separate HTTP health probe: a probe would create a second network client and
could bypass the proxy. Availability is learned from the real wallet2 RPC connection.
External proxy availability is checked with a TCP connect to the configured numeric/loopback
proxy endpoint only; it never resolves or contacts a Monero node and does not use the
wallet connection. It is not an HTTP/DNS client. External proxy availability is checked
when a transport is applied. If Orbot, InviZible,
i2pd, or a custom proxy is unavailable at startup, the wallet stays offline and the user
must apply the transport again after the provider returns; automatic external-provider
restart is not implemented.
Remote public-node discovery is intentionally **not** implemented: no vetted, stable,
privacy-preserving directory source is checked in. Users can add nodes manually; the
Network & Nodes screen filters and selects from the saved catalog.

## Android UI

Settings → Connection → **Network & Nodes** exposes:

- the eight modes;
- proxy host/port and custom node network;
- automatic selection and trusted-node preference;
- LWS enabled/disabled status, address, destination network, transport compatibility,
  privacy warning, and a link to the LWS settings screen;
- connection status, active node, node network;
- compatible saved nodes and a link to node management;
- VPN/I2P/custom warnings and configuration errors.

The old `ProxySettings` screen remains for onboarding compatibility but is no longer the
primary entry point. It writes the legacy `WALLET_USE_TOR`/`WALLET_PROXY` keys directly and
does not choose a custom-proxy destination network, so `Network & Nodes` is authoritative
after onboarding. Moving onboarding onto `TransportController` is a pending product
decision; until then, a manual proxy selected during onboarding must be re-applied from
Network & Nodes. `Network & Nodes` is reachable even in offline mode so a first node can
be configured.

## Tests and limitations

Pure JVM tests cover the transport↔network matrix, proxy validation, SOCKS5 URI
formatting, LWS TLS/network validation, and node selection. Not covered by local tests
because they require CI or a device:

- real Orbot/InviZible/i2pd/I2P Java availability and port configuration;
- VPN routing and loss behavior;
- wallet2 SOCKS5 remote resolution and HTTPS daemon behavior;
- per-transport LWS login, TLS certificate validation, and transport-change cancellation;
- Android Keystore/Tor lifecycle interactions.

A static review cannot prove the absence of leaks. In particular, a VPN can exclude
ANONERO, a malicious proxy can lie about the destination network, and wallet2's TLS
support must be verified against the pinned native build. Do not describe the VPN mode
as leak-proof.

## References

- I2P for Android source: <https://github.com/i2p/i2p.android.base>
- I2P SOCKS proxy documentation: <https://geti2p.net/en/docs/api/socks/>
- I2P SAM v3 documentation: <https://geti2p.net/en/docs/api/samv3>
- I2P port assignments: <https://i2p.net/en/docs/overview/ports/>
