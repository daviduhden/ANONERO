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

# Native anonymity audit — 2026-11-14

## Threat model and regression plan (before implementation)

Asset: wallet activity, output identifiers, file paths, signed messages and transaction
metadata. An attacker able to read logcat, collect diagnostics, inspect shared logs,
or view an error screen can correlate wallet operations across sessions. wallet2 error
text crosses an untrusted native-library boundary into native logging and JNI strings;
exception text may embed its input. The adapter must not assume that text is safe.

Change: replace raw exception logging and import/export/sign operation failure strings
with fixed technical messages; preserve successful sentinel strings and false returns.
Catch unknown exceptions in those existing handlers. A wrong change could hide a failed
operation as success, so the success strings and false defaults must remain unchanged.
No transaction construction, cryptography, file formats, JNI symbols or signatures change.

Residual limits: fixed errors still reveal that an operation failed. Removing a log does
not erase memory or previously recorded diagnostics. Explicit status/error getters and
upstream logging require separate review. This does not prove overall JNI exception or
handle safety. CI must compile both flavors/ABIs and exercise malformed export/import,
sign/verify and transaction submission failures. Rollback consists of reverting this
adapter/checker change; it does not require a persisted-data migration.

## Verified static findings

- Medium severity, high confidence: exception text was emitted to logcat/stderr by
  `exportKeyImages`, `exportOutputs`, `Coins.setFrozen`, `Coins.thaw`, `signMessage`
  and `verifySignedMessage`. Exploitation requires access to diagnostics and a failing
  operation whose upstream text includes sensitive input. Fixed messages remove this
  uncontrolled disclosure channel.
- Medium severity, high confidence: `importKeyImages`, `importOutputs`,
  `submitTransaction` and `signAndExportJ` returned raw upstream failure text to Kotlin.
  These now return fixed failures while retaining their existing success sentinels.
- Pending: many adapter exports call wallet2 without a boundary catch. Opaque raw
  handles depend on Kotlin owner lifetime; the helper validates neither liveness nor
  ownership. A full fix needs a coordinated lifetime/API review and instrumented CI.
- Pending: explicit transaction error getters return wallet2 text, and native logger
  configuration remains callable. Their consumers and upstream logging need review.

Evidence is static only; no build, test, network, device or cryptographic proof was run.

## Follow-up threat model: native lifecycle diagnostics

Successful open/close, listener, seed-type, daemon-init and background-sync messages
allow a log reader to infer session timing even without addresses. These bypass Kotlin's
stealth logging policy. Remove verbose/debug/info emissions and retain only fixed
warning/error diagnostics. Preserve background-sync calls and their Boolean results;
failed setup retains a fixed error diagnostic. Initialize wallet2 logging explicitly to
silent at JNI load and again after explicit logger initialization. The submodule is
unpopulated, so upstream defaults cannot be verified locally. Existing explicit logging
JNI methods remain compatibility surfaces and need review before future use.

Residual stealth limit: fixed native error diagnostics still reach logcat/stderr and
are not controlled by Kotlin Stealth Mode. This change reduces routine session metadata;
it does not provide complete log silence or erase existing records. CI must check both
ABIs/flavors, setup failure, and verify no native routine lifecycle messages appear.
Rollback has no persistence impact; revert the adapter and checker changes together.
