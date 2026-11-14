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

# Wallet session isolation review (2026-11-14)

## Threat model before implementation

The protected assets are the previous wallet's balances, accounts, addresses, sync
activity, and pending UI shortcuts. A local observer or a delayed native callback can
observe or republish these values after a wallet is reopened or another profile is
selected. The boundary is the native listener -> process-wide WalletState -> UI and
notification collectors. A stale callback must not operate on the replacement wallet.

The planned changes reject callbacks whose captured wallet is no longer managed,
publish the managed wallet reference with cross-thread visibility, store incoming-event
state on the captured wallet only, and reset UI state for every
new open, including reopening the same profile. No wallet files, keys, persisted
formats, JNI signatures, transport selection, or transaction rules change. A failed
close must retain the old state and wallet; state is reset only after close succeeds.

If incorrect, a guard could suppress valid updates or leave stale UI state. CI must
run the wallet-state reset unit regression and both flavors' compilation; device
tests must delay callbacks across close/open, lock, service restart, and profile
switch. Native callback/close interleavings still require runtime validation; an
identity guard is not native lifetime synchronization or forensic resistance.
Rollback consists of reverting the session-state/listener changes without migrating
wallet data. Root or an app-context compromise can still read live process memory.

## Findings

- Medium, local/session scope, verified static: `WalletState.setProfile` cleared
  cached wallet data only when the profile enum changed. Reopening the same wallet
  retained sync/account/loading state, and profile changes retained selected account,
  offline/loading flags and queued unlock shortcuts. Reset every session explicitly.
- Medium, concurrent callback scope, verified static: `moneyReceived` used the global
  managed wallet, while other callbacks had no managed-wallet identity check. A late
  callback could store the replacement wallet or publish old progress. Guard callback
  entry and use the listener's captured wallet. Exploitability depends on native
  callback timing and is unverified.

Static review does not establish runtime correctness, anonymity, or fund safety.

## Scope and regression evidence

Reviewed `WalletProfileStore`, `WalletManager`, `WalletState`,
`AnonWalletHandler`, `MoneroHandlerThread`, and the service/open call sites. Main
and decoy file prefixes remain distinct (`anon.` does not match `anon-alt`), and
profile-specific credentials and restore-height keys remain separate. No new
persisted session identifier is introduced. Accounts and subaddresses still share
one wallet's keys and remote sync context; they are not independent network identities.

`WalletStateSessionTest` prepares CI coverage for reopening the same profile and
switching profiles, including queued-action removal. `git diff --check` passed.
No builds, application execution, unit tests, instrumented tests, or Actions were
run. Native callback lifetime stress testing remains required. The Monero
submodule is uninitialized; upstream behavior was not independently audited here.

## Profile-scoped LWS threat and compatibility review before editing

The additional asset is the separation of main and decoy public addresses/view keys.
A shared LWS operator can correlate the two wallets using their login timing and
connection context. Previously all profiles inherited one enabled LWS setting, so
opening a decoy could disclose its view key without that profile opting in. The
configuration -> wallet startup/login boundary must read only the active profile.

Keep all legacy `lws*` keys for MAIN, introduce distinct `decoyLws*` keys for DECOY,
and default decoy LWS to disabled with no inherited endpoint. No copy migration is
performed: unknown prior decoy consent cannot be inferred from main consent. The
settings screen continues to request consent for initial enabling/server changes.
Removal deletes only the active profile's LWS keys. Shared RPC/transport settings
remain a documented correlation surface; this change does not promise separate
circuits or independently operated servers. Wrong profile selection could either
expose view keys or prevent sync; CI must cover legacy MAIN reads, decoy defaults,
independent round trips and removal. Device validation must switch profiles and
observe that no LWS login happens before per-profile enabling. Wallet funds and
backup formats are unchanged. Rolling back preserves MAIN settings but older app
versions share MAIN LWS settings again; that loss of isolation must be disclosed.

Implemented LWS settings persistence checks the synchronous commit result and shows
an existing localized generic error on save/session failure. It does not report
success or expose an endpoint in the error. `LwsConfigTest` fixes the legacy MAIN
key contract and disjointness; `LwsProfileIsolationTest` exercises real preferences
with a legacy MAIN fixture and independent DECOY writes. These tests are prepared
for CI and were not run locally.
