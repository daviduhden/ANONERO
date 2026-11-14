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

# Continued development: core UI/payment boundary (2026-11-14)

## Threat model and regression plan before editing

Assets: reviewed recipient set/amounts, signatures, active wallet identity and output
selection. A rapid edit, repeated action, delayed validation or session transition can
misassociate native results with current UI state. Mutable Compose inputs currently cross
IO dispatch before capture; asynchronous address-validity lists retain their old length.
Wrong changes can crash payment editing, approve stale input or publish a result for a
wallet/input the user no longer sees. Snapshot inputs before IO, bind validation to the
same address list, reject missing/currently pending results, independently validate request
bounds/amount totals/addresses in the ViewModel, and guard repeated preparation. wallet2
remains authoritative; no fee/selection/ring/consensus rule changes.

Message sign/verify must capture inputs and wallet before work and discard results after
input/session/lock changes. Clear displayed proof when its inputs change. These checks do
not serialize native handle destruction; callback and native lifetime remain separate work.
Compatibility: no persisted format, key, JNI ABI, or new permission. Existing strings can
represent request/construction failures. Tests in CI must cover recipient-list growth,
removal/replacement, overflow, invalid totals and message edits while native work runs.
Rollback: revert UI/pure request policy only, never restore discarded pending results.
No local build or test may be run.

Account/output mutation review before editing: stale or repeated UI actions can mutate
locked wallets and errors currently remain invisible. Central methods must reject locked
sessions, validate account/output ownership and report failure. Freeze/thaw's JNI void API
cannot communicate failure, so verify the refreshed wallet2 frozen flag rather than assume
success after store. No local output-selection algorithm is added. UI serializes actions,
shows existing/localized failure and removes frozen/spent/stale selected outputs. Fee,
wallet2 selection and recovery files are unchanged. CI must inject persistence/native
failure, lock transitions and changed UTXO snapshots; real-wallet device tests are required.

Restore cleanup review: disposing RestorePreview must not propagate an auxiliary-cleanup
refusal as a UI crash, and restore cleanup must not delete an embedded Tor runtime's live
cache. Preserve the transport cache only for restore cleanup; explicit wipe keeps its full
cache cleanup behavior. Refused disposal cleanup emits a fixed warning and startup purge
remains the recovery path. Explicit restore/wipe cleanup still reports failure. This does
not prove forensic erasure or asynchronous Tor shutdown on wipe.
