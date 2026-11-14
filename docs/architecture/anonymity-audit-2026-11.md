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

# Source anonymity hardening — 2026-11-14

Baseline HEAD: `a2c9cfb6f668ab6ee59d71d162d909b2c392428b`. Documentation already had
uncommitted edits when this work began; they were preserved. No commit, push, tag, Actions,
build, application execution or test execution was performed. Evidence is static only.

## Findings and implemented corrections

Severity describes potential impact before correction, not a measured exploit. Source
facts are verified static with high confidence; exploit timing and packet behavior are
inferred until dynamic validation.

| Finding | Severity / scope / exploit prerequisite | Evidence and correction |
| --- | --- | --- |
| TLS exemption accepted a DNS name starting with `127.` | High / LWS view key and address / configured or imported crafted host | `app/src/main/java/io/anonero/util/PrivacySettings.kt:263`: strict literal loopback parser replaces prefix trust; no HTTP fallback |
| Shared LWS endpoint/consent across MAIN and DECOY | High / cross-wallet operator correlation / opening decoy after MAIN LWS setup | `app/src/main/java/io/anonero/util/PrivacySettings.kt:164`: MAIN keeps legacy keys, DECOY gets distinct disabled defaults and its own consent; removal is profile-specific |
| Arbitrary errors, tags, QR debug messages and device attributes entered diagnostics | High / local files, logcat and exported logs / access to diagnostics | `app/src/main/java/io/anonero/store/DiagnosticPolicy.kt:32`: exact reviewed event allowlist, fixed fallback/tag, warning/error only, minute timestamps; independent debug logcat tree removed; export contains API level, no hardware/build host or local timezone |
| Original uncaught exceptions bypassed Timber policy | High / system crash logs / exception carrying sensitive text | `app/src/main/java/io/anonero/AnonApplication.kt:166`: default termination receives a fixed exception without original cause or stack |
| Raw native exception/error strings crossed diagnostic and operation-result boundaries | High / native logs and JNI consumers / wallet2 failure containing metadata | `app/src/main/cpp/anonero.cpp:1671`: fixed failure text in four operation results, six exception log sinks redacted, catch-all handling extended in affected paths; success semantics unchanged |
| Native routine lifecycle logging disclosed activity independently of stealth | Medium / logcat / privileged observer | `app/src/main/cpp/anonero.cpp:104`: remove verbose/debug/info calls, initialize wallet2 silent at JNI load and logger init; fixed native errors still exist |
| Proxy validators accepted malformed pseudo-literals before socket construction | Medium / local DNS and proxy connection / malformed configuration | `app/src/main/java/io/anonero/network/TransportModels.kt`: parse ASCII IPv4/IPv6 without InetAddress, canonicalize localhost, reject scoped/malformed literals |
| RPC host suffix could be spoofed with URL syntax | Medium / destination-network boundary / malformed imported mutable node | `app/src/main/java/io/anonero/network/NodeSelectionPolicy.kt:79`: complete host validation for manual and automatic selection rejects `clearnet.example/path.onion` |
| Suspended provider authorization could publish an obsolete endpoint | Medium / concurrent transport transitions / overlapping acquire and save/release | `app/src/main/java/io/anonero/network/TransportController.kt:185`: generation/config guards and synchronized publication; cancellation propagates |
| Missing Tor listener could leave a cached SOCKS endpoint | Medium / reconnection / listener disappears | `app/src/main/java/io/anonero/services/TorService.kt:116`: empty listener invalidates SOCKS; runtime messages exclude provider payloads |
| Same-profile reopen retained state; callbacks could address replacement wallet | Medium / session/profile boundary / delayed callback or reopen | `app/src/main/java/io/anonero/services/WalletState.kt:244`, `services/MoneroHandlerThread.kt:29`: reset every authenticated open after successful close, clear selected account and pending shortcuts; guard captured wallet identity, store captured wallet |
| Queued diagnostic writes could reappear after clear | Medium / session history / asynchronous write after clear | `app/src/main/java/io/anonero/store/LogsDataStore.kt:143`: generation invalidates queued writes, stealth rechecked at mutation; sanitize reads/writes; startup deletes historical diagnostic store and export cache before consumers |
| Lock-screen notifications could expose sync/operator metadata | Medium / local observer / enabled lock-screen previews | `app/src/main/java/io/anonero/services/AnonNeroService.kt:308`: both builders use SECRET visibility; OS/channel behavior still needs device checks |
| Notification service probed the daemon with a live RPC call on every tick | Medium / node operator and local timing observer / service running with network | `AnonNeroService.kt`: notification state now consumes `WalletState.connectionStatusValue`, published by wallet2 refresh callbacks; the separate periodic RPC probe was removed |
| Default Tor validation required an external proxy host | Low / availability / fresh default configuration | `TransportConfig.validationError` exempts the embedded runtime's dynamically owned endpoint; no weaker fallback added |
| Unused exact-alarm permissions | Low / Android permission surface / installation | Both exact-alarm permissions removed after repository-wide search found no scheduler use |
| New development string file omitted from localization checker | Low / untranslated or unresolved UI text / future edits | `scripts/check-i18n.py` now includes `strings_development.xml` for the base and all three locales; the checker caught two unresolved `R.string.wallet_operation_failed` references before CI |

No change was made to consensus, ring signatures, fee/output selection algorithms, Monero
pin/patches, seed/PIN cryptography, backup formats, minimum SDK, ABIs or available transports.
Kotlin/C++/JNI remain the architecture. kmpTor remains default.

## Exposure inventory and correlation limits

- RPC: saved endpoint, authentication, sync/broadcast timing, request patterns and repeated
  operator selection remain linkable. Selection does not probe candidate RPC servers or
  rotate operators. Random rotation can spread metadata to more observers, so none was added.
- LWS: explicitly receives public address and private view key, incoming metadata and timing.
  Profile-specific configuration prevents inherited disclosure, but selecting the same
  operator for both profiles can still link them. No automatic LWS operator switch was added.
- Transport: embedded Tor, external Tor, I2P, custom SOCKS and VPN are preserved. External
  proxy reachability opens a TCP connection only to the configured numeric proxy, not RPC;
  this still reveals timing to that proxy. TCP availability is not a SOCKS/anonymity proof.
- Sessions: notification updates no longer issue an extra RPC probe; status comes from wallet2 callbacks. No persisted last-profile or new client tracking identifier was added. Shared RPC
  catalog and transport remain shared contexts. No authenticated SOCKS credentials are
  exposed by the current integration; no per-wallet Tor circuit isolation is claimed.
- Accounts/subaddresses: belong to the same wallet and sync context. No independent network
  identity or guaranteed unlinkability is implied. Pay-to-many, coin control and signing
  remain wallet2 operations; combining outputs or reusing signed identity messages can
  correlate contexts. No experimental wallet algorithm was introduced.
- Persistence: wallet files, credentials, restore heights and LWS configuration are scoped
  where described. Logs are minimized and transient across process starts. Deletion does
  not establish flash/forensic erasure; strings/native buffers are not securely zeroized by
  log suppression. Exported off-device copies cannot be recalled.
- Android: `FLAG_SECURE`, backup exclusion, immutable PendingIntents, private FileProvider
  paths and non-exported service remain. Explicit sharing/browser/clipboard operations cross
  OS trust boundaries. Clipboard history/accessibility/root and native tombstones remain
  outside these controls. No analytics or remote logging was added.
- Background: refresh/service work remains observable by timing, power use and OS service
  state. Lock/key transitions need device validation. Listener identity checks do not
  establish synchronization with concurrent native destruction.

## CI regressions prepared, not executed

- JVM: `TransportPolicyTest`, `NodeSelectionPolicyTest`, `LwsConfigTest`,
  `WalletStateSessionTest`, `DiagnosticPolicyTest` cover parser/network-policy rejection,
  loopback spoofing, profile-key separation, session reset and arbitrary diagnostic payloads.
- Instrumented: `LwsProfileIsolationTest` preserves an installed MAIN configuration while
  keeping DECOY disabled/independent; `DiagnosticIsolationTest` rejects old-generation and
  stealth writes. These are source additions, not passing-test claims.
- Static CI: the existing owner-gated manual `jni-contract` job also runs
  `scripts/check-native-diagnostics.py` and `scripts/check-anonymity-boundaries.py`.
  These source guards are deliberately narrow and supplement runtime tests.

Required dynamic matrix: each transport on API 29 and newer; DNS/IPv4/IPv6 packet capture;
VPN loss/per-app exclusion; listener loss/restart; cancellation and simultaneous node/
transport changes; old socket termination; failed LWS login and server changes; delayed
callbacks across close/open/duress; service/process restart; notification/clipboard
behavior; failed persistence and native logger initialization. Independent review is
required for transaction privacy, cryptography, handle lifetime and traffic correlation.

## Remaining actionable risks

1. RPC trust is enabled unconditionally by existing startup/reconnect/manual paths.
   `external-libs/patches/0002-UR-functions.patch:470` requires trusted daemon for UR key-image
   import. Redesign with explicit trust/disclosure policy after upstream review and CI
   fixtures; blindly changing the Boolean breaks existing behavior. Exact extra requests
   are unverified because the Monero submodule is uninitialized.
2. The auxiliary-close diagnosis recorded here was addressed in the
   [continued-development iteration](../development-iteration-2026-11.md): retained ownership,
   close retry, guarded deletion and success-after-close are implemented. Native failure
   semantics and provisioning interleavings still require dynamic validation.
3. JNI still has unguarded wallet2 exports and pointer/close interleavings. No complete
   exception containment or lifetime proof is claimed. Transaction error getters can still
   return wallet2 text to explicit UI consumers; persistence is sanitized separately.
4. NERO signed-file binding and backup passphrase strength remain existing critical limits;
   see `TODO.md`. No new guarantee of airgapped fund safety is made.
5. Generation guards cannot revoke an endpoint already returned or close every wallet2
   socket. VPN presence does not prove routing for ANONERO or absence of packet races.
6. MAIN's legacy LWS consent is preserved; rollback to an older app ignores DECOY LWS keys
   and restores the older shared-configuration behavior. Do not downgrade as an anonymity
   workaround. Decoy files/Keystore aliases remain discoverable to forensic adversaries.

Detailed pre-edit threat models, compatibility and rollback plans:
[network](../network-anonymity-audit.md), [wallet sessions/LWS](../wallet-session-audit.md),
[native](../native-anonymity-audit.md), [local diagnostics](anonymity-audit-local.md).

This is source hardening with static evidence, not an anonymity, security, runtime or fund
safety proof. The unavailable upstream source and dynamic matrix prevent an exhaustive
Monero-engine or network-behavior assurance claim.

## Verification recorded for this working tree

Verified static: ktlint formatting/checking of 28 changed/new Kotlin files;
`clang-format --dry-run --Werror` on the adapter; `actionlint` on both workflows;
JNI contract (131 Kotlin externals, 134 exports, three documented orphan exports);
i18n (389 strings, two plurals, three translated locales; development resource files included); both new source guards;
XML parsing across main resources/manifest, TOML parsing, Python script syntax and
`git diff --check`. No dependencies were installed. `ruff` was unavailable; Python
checks are syntax/source-policy checks, not a substitute for Python lint or CI.
All JVM/instrumented tests and compilation remain unexecuted and unverified.
