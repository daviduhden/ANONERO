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

# Retained auxiliary wallet ownership (2026-11-14)

## Threat model and regression review before editing

Assets are wallet keys still resident in auxiliary native wallets, the decoy's
recoverability, and backup-copy integrity. An I/O failure or hostile filesystem
condition can make wallet2 refuse close. The current caller discards the reference
and deletes backing files/Keystore credentials despite that failure; successful
backup/decoy UI can also precede confirmed closure. Trust boundaries are Kotlin
ownership -> JNI close -> wallet2 file writes -> profile/cache deletion.

Retain every unmanaged wallet in its manager until close explicitly returns true.
Reject duplicate path opens and destructive cleanup while ownership remains;
provide a retry at the same path for recovery. Report success only after closure.
Serialize backup exports so a concurrent export cannot delete an active copy.
Cancellation must remain cancellation, with cleanup attempted without discarding
failed ownership. No keys, PIN formats, backup encryption, JNI signatures, consensus
rules, persisted identifiers, or managed-wallet identity are changed.

Failure of the fix could leak native references, permit late writes after deletion,
or prevent retry. Pure JVM registry tests must cover false/throwing close, later
successful retry, duplicate ownership and path containment. CI/device fault injection
must make native close fail and verify no files/credentials are removed and no
success is emitted. A process restart releases native resources; backup scratch
files are purged by existing startup cleanup, while decoy files/keys remain for
recovery or explicit removal. Retained keys remain in memory until successful close
or process death. No secure-erasure or anonymity guarantee is claimed. Rollback is
source-only but restores the prior unsafe close-failure behavior; no migration is
required.

## Implemented and static evidence

`RetainedWallets` keeps the wallet/path binding on false or throwing close, removes
it only on success, and rejects duplicate ownership. `WalletManager` retains this
registry and refuses singleton reset while any managed/auxiliary wallet remains.
A backup export uses a mutex, retries a previously retained copy before deleting
scratch files, checks closure before success, and refuses cache clearing while an
export or retained auxiliary copy exists. On failed closure it removes the
seed-bearing manifest/archive but keeps the native backing files in `tmp_backup`.

Decoy creation checks native close before publishing credentials or seed success.
Rollback deletes files/credentials only after successful closure. A cancelled
operation remains cancellation; cleanup exceptions are suppressed onto its primary
failure. Failed close remains manager-owned. Existing authenticated removal now
retries the retained close off the main thread and reports failure without
pretending removal succeeded. The existing removal action is also available for
incomplete decoy artifacts, enabling explicit recovery/removal after a failure.

Prepared `RetainedWalletsTest` for false close, throwing close, later retry,
duplicate/foreign ownership, cancelled close and failed acquisition. Tests were
not run. Adapter review by the native specialist confirms that its false-close
branch preserves the Kotlin handle/listener; the absent upstream Monero checkout
prevents independently proving wallet2 exception lifetime semantics.

No backup format/KDF change, new secret persistence location or JNI API is added.
Native backing files retained after failure are already encrypted wallet files;
changing a backup-copy password may have only partially completed, so no stronger
claim about those files' password or atomicity is made. A restart releases native
resources and existing startup cleanup removes backup scratch; an incomplete decoy
remains available for explicit removal without silently destroying recovery data.

`withUnmanagedPathClosed` executes deletion while holding the same registry monitor
used by auxiliary opens and closes. It refuses deletion while a retained wallet
exists, and prevents an auxiliary open from entering until deletion completes.
The separate retained-path query remains only a snapshot. This registry operation
does not itself serialize the earlier Keystore setup portion of new profile
provisioning with profile removal. The decoy screen's existing
multi-step creation UI limits access to removal during that operation; this is not
a process-wide lifecycle guarantee. A general profile-operation lease and dynamic
interleaving tests remain required before claiming race-free provisioning/removal.
