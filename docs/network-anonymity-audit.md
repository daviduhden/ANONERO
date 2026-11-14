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

# Network anonymity review — 2026-11-14

Evidence: static inspection only; no build, tests, network probes or Actions executed.

## Threat model and regression plan (before implementation)

Assets: selected transport, destination confidentiality, separation of connection contexts.
Adversaries: a local DNS observer, a malicious proxy configuration, and concurrent UI or
lifecycle operations that authorize an obsolete transport. Trust boundary: persisted
proxy input crosses Java socket construction before wallet2; transport authorization
crosses a suspended provider acquisition. Wrong validation can send malformed numeric
input to name resolution; a stale authorization can return the previous provider after
configuration changes. Cancellation must abort authorization without replacing it with
an ordinary transport error.

Changes: validate full ASCII IP literals, canonicalize localhost to numeric loopback,
permit embedded Tor's runtime-owned endpoint without requiring external-proxy settings,
and reject authorization results invalidated by release/configuration changes. Clear
Tor listener state when no listener is reported; exclude raw Tor runtime/error payloads
from diagnostics. No wire protocol, persisted key, JNI signature, dependency or consensus
rule changes. Existing valid numeric IPv4/IPv6 addresses remain supported; scoped IPv6
proxy addresses are rejected rather than delegated to interface-dependent resolution.

CI must execute transport policy regression cases for malformed literals, IPv6 loopback,
localhost, and default embedded Tor. Device/network tests must cover concurrent transport
changes during acquire, cancellation, Tor listener loss, VPN loss, socket closure, and
DNS packet capture. Rollback is a source revert of these changes; no data migration is
needed. Reverting validation would reintroduce the exposure and must not authorize a
weaker transport as a workaround.

Residual limits: this does not isolate Tor circuits per wallet. The current wallet2
proxy interface and ProxyEndpoint support unauthenticated SOCKS only. Remote custom
proxy TCP reachability probes reveal app timing to that proxy, and TCP success does not
prove SOCKS capability or anonymity. VPN capability does not prove per-app routing or
prevent a packet race on VPN loss. A stale authorization guard does not itself terminate
already active wallet2 sockets. RPC operators can correlate sync and transaction timing;
LWS additionally receives address and private view key. Accounts and subaddresses are
not independently isolated transport identities. These require dynamic and independent
review, not security claims from static inspection.

## LWS TLS exemption threat model (before implementation)

The private view key and address cross the TLS boundary at login. A remote operator or
network observer can exploit the former `127.` string-prefix exemption with a DNS name
such as `127.attacker.example`, accepted by hostname validation, to permit HTTP to a
non-loopback server. Reuse the DNS-free loopback literal parser for the exemption;
keep localhost and canonical 127/8 literals compatible. CI regression cases cover both
`validate` and `validateTransport`, including malicious DNS suffixes and malformed
numeric hosts. No stored endpoint is rewritten. A formerly exempt remote hostname
must be configured with TLS before reconnecting; never fall back to HTTP. Rollback is
source-only and would restore the exposure. TLS peer validation and actual LWS packets
remain dynamically unverified. Severity: high, conditional on selecting/importing a
crafted LWS host; static confidence: high.

## RPC URL boundary threat model (before implementation)

Node hosts are mutable and can originate from imported JSON. Previously selection
checked only a nonblank host and its suffix; `clearnet.example/path.onion` was classified
as onion although URI parsing addresses the clearnet authority. A malicious imported
node can thereby violate the destination-network promise (through the selected proxy,
not necessarily through a direct connection). Validate the complete host before both
manual and automatic selection: plain DNS labels or validated numeric literals only;
reject URI separators, credentials, embedded ports, whitespace and malformed literals.
Preserve ordinary DNS, onion/I2P, trailing-root-dot DNS and IPv6 compatibility. No node
record is deleted or rewritten. Invalid records remain stored but cannot be connected;
users can correct them. CI regressions must exercise both selection gates. Device tests
must verify wallet2 receives the intended authority. Rollback is source-only and restores
the URL-boundary defect. Severity: medium; attacker needs node import/configuration;
confidence: high (static).
