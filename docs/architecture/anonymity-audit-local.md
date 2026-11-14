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

# Local diagnostic boundary — 2026-11-14

Evidence: static review of working tree based on `a2c9cfb6f668ab6ee59d71d162d909b2c392428b`.
Existing documentation edits were present before this audit and are preserved.

## Threat model and regression plan (before implementation)

- Asset: wallet/network metadata, profile separation, diagnostic export contents.
- Adversary: recipient of shared diagnostics, local file reader, privileged logcat reader;
  arbitrary wallet2/provider exception text can contain endpoints, paths or wallet data.
- Flow: Timber formats messages and throwable stack traces before AnonLogTree; the tree
  asynchronously persists them in a shared DataStore; Logs exports that store.
- Failure: free text, debug QR payloads and throwable details survive into logs; independent
  debug tree bypasses stealth. Queued writes can reappear after a log clear/profile switch.
- Plan: fixed allowlisted diagnostic events, warning/error retention only, fixed fallback,
  no raw tag/throwable forwarding, no independent logcat tree; generation guard for queued
  writes at clear and stealth checks at the actual DataStore mutation. Export API level only, without hardware/build fingerprint. Sanitize old stored entries on
  reading/export as well as writing.
- Limits: errors still reveal coarse timing and failure categories. No memory erasure,
  root/OS protection, forensic erasure, or network anonymity is claimed. OS crash handler
  and upstream native logging require separate review.
- Compatibility: diagnostic JSON fields remain unchanged; wallet/backup files and keys
  are unaffected. Existing diagnostic text is intentionally discarded.
- CI evidence: policy unit tests with arbitrary payloads, stack traces and unknown tags;
  instrumented concurrency/stealth/export validation remains required.
- Rollback: revert diagnostic code only; do not restore historical sensitive logs.

Notification review before editing: notification previews can expose selected endpoints,
sync progress and receipt timing to a locked-screen observer. Mark both wallet notification
builders SECRET; this affects locked-screen presentation, not foreground-service operation.
No new permission or format is needed. Device validation must check API 29 and newer,
channel/user overrides and stealth changes. Roll back the presentation flag if Android
compatibility breaks; OS/privileged observers still see service activity. Exact-alarm
permissions have no call sites in the source inventory and can be removed without changing
any scheduling implementation; confirm background sync behavior in device CI.

Historical diagnostic files need startup cleanup as well: read-time sanitization protects
exports but cannot remove old raw bytes already on disk. Before Koin/DataStore consumers
are initialized, delete the diagnostic DataStore file via AndroidX `dataStoreFile` and
the log-export cache directory. These files contain no recovery data. A failed cleanup
produces a fixed warning and leaves read-time sanitization in force; it cannot promise
physical erasure on flash. Process-local DataStore ownership and startup ordering must be
validated in CI. No migration is applied to wallet files.

Crash boundary review before editing: forwarding the original uncaught Throwable to
Android's default handler can publish its message/causes to system logs independently of
Timber. Forward a new fixed exception with no original cause or stack instead, preserving
the default termination path. This loses detailed Java crash diagnostics deliberately;
CI/device reproduction must collect reviewed synthetic fixtures instead. Native tombstones,
OS internals and memory remain outside this boundary. Rollback is source-only but restores
exception disclosure.
