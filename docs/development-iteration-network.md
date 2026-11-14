<!--
  Copyright (c) 2026, David Uhden Collado
  Copyright (c) 2022-2026, r4v3r23

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Network development iteration — 2026-11-14

## Threat model before editing

Assets: selected transport, wallet destination policy, suppression of accidental direct
connections, session state and diagnostic metadata. A stale legacy settings screen can
write old keys and native proxies independently of TransportController, causing the UI
and wallet to disagree about routing. A local network observer can correlate unintended
provider startup; a configuration race or VPN-loss callback can resurrect obsolete
READY state. Trust boundaries are Compose/ViewModel -> persisted TransportConfig ->
provider authorization -> wallet2, and Android callbacks -> transport StateFlow.

Replace legacy proxy controls with the existing multi-transport editor. The same editor
must offer all supported modes and explicit custom destination network during onboarding;
configuration without a live wallet must not authorize/start a provider. Keep Tor log
viewing, bounded in memory, and do not expose raw exceptions. Do not introduce new keys,
permissions, network clients, identifiers or dependencies. Persist existing transport keys
and preserve Tor as the default. A failed update must leave the wallet paused/offline.

Synchronize VPN callback publication with configuration/release generations. Recheck
VPN capability when publishing direct authorization; reject obsolete authorizations.
A lost VPN invalidates its generation and clears the endpoint; recovery does not silently
resume direct traffic. Restart is authorized through the wallet owner, not an Android
callback performing wallet JNI calls.

Compatibility: existing transport/node keys, Monero protocols, JNI, profiles and API 29
remain intact. Shared network settings UI is reused so legacy routes stay navigable.
Rollback: revert source changes, retaining persisted transport configuration; never
restore a weaker direct-path workaround. CI must check shared editor routing, default
embedded/external onboarding without unwanted Tor startup, malformed proxy input,
concurrent saves, VPN callback/configuration races and VPN loss/recovery. No tests or
builds run locally. Device packet capture is still required for DNS, VPN routing, socket
closure and reconnect behavior. State synchronization cannot prove absence of packets
between Android reporting VPN loss and wallet2 pausing, nor circuit isolation.

Durability review before editing: SharedPreferences.commit can report failure after its
in-memory map changes. An ignored result can authorize configuration that will not
survive restart. TransportConfig.save must return the actual commit result. On failure,
TransportController retains its previous authoritative config, attempts to restore that
config in preferences, invalidates provider authorization, publishes an endpoint-free
error, and returns failure. Restoration failure remains an error, never permission to
connect. No whole preference map containing wallet secrets is copied. A future process
restart can only load the surviving config; it must authorize it afresh. CI needs commit-
failure coverage; device storage-failure/restart behavior remains unverified.

## Implemented and static evidence

- `ProxySettings` delegates to `NetworkSettings(transportOnly = true)`; proxy-era
  preference and native mutation APIs are no longer called by this UI. The logs-only
  `ProxySettingsViewModel` retains at most 100 sanitized Tor diagnostics.
- `NetworkSettingsViewModel` serializes its own save/select/preference operations,
  merges preference-only edits into the latest configuration, and performs native calls
  on IO. Session ownership is rechecked after suspended proxy acquisition and before
  handling errors against a captured wallet. This is not a global wallet-operation lease.
- `TransportController` synchronizes VPN callbacks and authorization publication,
  invalidates authorization generations on VPN loss, checks VPN presence again before
  publishing Direct, and preserves readiness on preference-only changes. The embedded
  provider rejects start/acquire after release.
- `TransportState.observeVpn` has CI-only unit coverage: loss removes the direct endpoint;
  recovery advertises availability without restoring authorization. Instrumented
  `TransportPersistenceTest` models failed commit after memory modification and explicit
  configuration restoration. Neither test was executed locally.
- Static checks: ktlint on affected Kotlin source/tests, localization check and
  `git diff --check` passed. No new resources, permissions, stored formats or network
  requests were introduced.

Remaining: a callback may arrive after a network transition, and no Android callback can
prove routing of every native socket. A returned endpoint can become stale immediately
before wallet use; wallet lifecycle serialization and packet-level tests remain required.
External SOCKS reachability is still only TCP reachability. No automatic provider or LWS
operator fallback was added.
