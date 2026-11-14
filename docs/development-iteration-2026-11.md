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

# Continued Kotlin/C++ development — 2026-11-14

Baseline HEAD remains `a2c9cfb6f668ab6ee59d71d162d909b2c392428b`. The prior uncommitted
anonymity audit and pre-existing documentation edits were preserved. This iteration
continues them; it does not restart or replace the project. No build, local test, Actions
dispatch/rerun/cancel, commit, push, tag or publication was performed.

## Priorities, root causes and implementation

| Priority / area | Demonstrated source defect or limitation | Implemented correction |
| --- | --- | --- |
| High: auxiliary ownership | Backup/decoy callers discarded false or throwing close, could delete backing files/keys and report success | RetainedWallets owns wrappers until confirmed close, refuses duplicate paths and supports retry; deletion executes under its monitor; singleton reset refuses live ownership |
| High: backup/decoy completion | Success and credentials could precede close; cancellation and cleanup could obscure primary failure | Export mutex, retained-copy retry before scratch reuse, close-before-success, preserved recovery data, cancellation propagation and suppressed secondary cleanup failure; incomplete decoy has explicit removal action |
| High: native snapshots | JNI builders cleared JVM errors/skipped failed records, allowing partial output/history lists to look complete | Scoped local refs, checked allocations, atomic model/list conversion and fixed Java failure at both refresh boundaries; native close exceptions preserve wrapper ownership |
| High: payment input boundary | Address verdict list survived list edits and was indexed with the new length; submission mixed current addresses with older parsed amounts | Immutable checked address snapshots, shape/content matching, safe per-row lookup and full click-time recapture; independent request/address validation and atomic preparation guard |
| Medium: account/output UI | Repeated or locked mutations and ignored errors; failed account refresh could leave previous-account funds visible | Central lock guards, bounded account validation, cancellation preservation, cleared account-derived state on selection, current local index retained after failure; serialized UI actions and localized snackbar errors |
| Medium: freeze/thaw | Void JNI calls could fail yet store made caller report success | Verify refreshed frozen flag before success; reject missing/spent/other-account outputs, publish observed state before returning store result; selected outputs prune frozen/spent/missing records |
| Medium: sign/verify | Mutable form fields crossed IO dispatch and results could remain visible after edits | Capture input and wallet, suppress stale result publication, clear proofs on input/lock change, disable input edits during operation |
| Medium: transport configuration | Legacy onboarding wrote independent proxy state; preference changes discarded providers; disk failure ignored | Reuse common editor, remove obsolete handler proxy APIs, serialize editor actions, merge current policy, preserve provider for preference changes, check commit and restore only network config on failure |
| Medium: VPN/provider races | Callback publications and delayed acquire could restore obsolete authorization | Synchronized VPN transitions, generation invalidation on loss, explicit authorization after recovery, released embedded provider refuses later acquisition |
| Medium: CI contracts | Callable test entry point contradicted latest dispatch-only requirement; shared release jobs could drift if copied | Dispatch-only workflows, generated embedded suite with explicit tag refs/cache policy, fail-closed aggregate and same-run native artifact; synchronization checker and CI-only mutation tests |
| Low: restore/UX/i18n | Disposal cleanup refusal could crash UI; cleanup could remove live transport cache; new catalogs escaped a fixed checker file list | Disposal warning/recovery path, restore-specific transport preservation, explicit cleanup failures, accessible back labels, all-locale catalog discovery and regression sources |

NERO/DECOY backup export is refused before MAIN scratch access. KDFs, backup versions,
seeds, Monero pin/patches, fees/rings/consensus, SDK floor, ABIs and dependency pins remain
unchanged. kmpTor stays default; external transports and shared LWS routing remain supported.
No new dependency, permission, tracking identifier or application network client was added.

## Feature state after this iteration

| Feature | Source integration reviewed/extended | Still unverified or limited |
| --- | --- | --- |
| New UI | Existing Compose screens retained; action/error/loading/accessibility behavior improved | Rendering, accessibility services, varying screens and portrait policy need device review |
| Stealth Mode | Pay-to-many total respects hidden amounts; prior diagnostics/notification controls retained | Root, cameras, privileged capture/accessibility and traffic correlation remain |
| Decoy Wallet | Retained close ownership, success-after-close, recovery/removal of partial setup | Pre-registry Keystore setup vs deletion needs global lifecycle serialization; no plausible deniability |
| Duress PIN | Existing profile-specific credential/PIN flow preserved; safer decoy completion and removal | Coercion resistance and timing require independent/device review |
| Accounts/subaddresses | Locked mutations refused; failure is visible and old-account snapshots cleared | Native fixtures for refresh/store failure and session interleavings |
| Pay to Many | Exact amounts, snapshot validation, 16-recipient form bound, guarded preparation and wallet2 construction | Fee sufficiency/transaction review/sign/broadcast require real-wallet CI/device fixtures |
| Sign/Verify | Native signing preserved; inputs/results bound and stale proofs cleared | Native handle lifetime and cryptographic/device correctness |
| Freeze/Thaw | Observed wallet2 state checked; bad output contexts refused and selection pruned | Failed native mutation, store failure and races need native fixtures |
| LWS | Prior per-profile settings/consent and common transport retained; shared editor removes legacy proxy bypass | Live operator/TLS/DNS/login/reconnect behavior; no operator rotation or circuit isolation |
| Network & Nodes | One editor for onboarding/settings, explicit custom destination network, durable-result handling, VPN generation guards | Packet capture, old-socket closure, DNS/IPv6 and per-app VPN inclusion |

These are implemented source paths, not a claim that visual components alone complete
features or that the application is stable.

## Regression sources prepared (not executed)

- JVM: RetainedWalletsTest covers false/throwing/cancelled close, retry, duplicate/foreign
  ownership, failed acquisition and guarded deletion. MultiPaymentPolicyTest covers bounds,
  exact sum overflow, invalid entries and stale/mixed address verdicts. TransportPolicyTest
  covers VPN loss/recovery state in addition to earlier parser cases.
- Android: TransportPersistenceTest models failed disk commit after memory update; previous
  DiagnosticIsolationTest/LwsProfileIsolationTest remain. Real native wallet fixtures for
  account/output/close failures are still required.
- Python CI-only: constructor mutation tests, shared-workflow mutation tests, cancellation
  import tests and feature-catalog discovery tests. These run in the manual JNI job.
- Static guards now cover four model constructors/order, atomic native snapshots, output
  frozen-state postconditions, forbidden backup-export contexts, cancellation imports and
  dispatch-only generated suite synchronization. Text checks are narrow, not behavioral proof.

## Existing CI evidence inspected using gh

[43633496677](https://github.com/daviduhden/ANONERO/actions/runs/43633496677), committed
`a2c9cfb`: JVM, Android Lint, native, assemble, JNI, localization and architecture jobs
passed. Instrumentation failed before the repository test script: about 605-second boot,
then optional settings animator-duration write returned Broken pipe (32)/adb 224. No XML
reports were produced. The directly observed failure is a guest system-service operation;
its deeper cause is not proven. The optional animation setup is disabled, while readiness,
actual test execution, report validation and nonzero-test gates remain mandatory.
No successful device-test result is claimed for this configuration change.

[43606679969](https://github.com/daviduhden/ANONERO/actions/runs/43606679969) confirmed
historical nullable/import errors already addressed before this iteration. Existing run
43633496677 validates those committed baseline fixes, not this dirty tree. No CI operation
was initiated, retried or cancelled by an agent.

## Architecture, compatibility and remaining work

Retained auxiliary ownership does not constitute a global operation lease. It serializes
auxiliary open/close/path deletion, not every managed wallet operation or the earlier
Keystore provisioning step. Entry guards cannot prove callback quiescence, native handle
lifetime or closure of established sockets. The upstream submodule remains uninitialized;
wallet2 exception-lifetime semantics need independent review against the pinned source.

After failed backup-copy close, manifest/ZIP are removed but native backing files remain
only in tmp_backup until close retry or process-start purge. Their password transition is
not independently proven atomic; no forensic erasure/zeroization guarantee is added.
Restore cleanup preserves the live transport cache; explicit wipe keeps full cache cleanup,
whose coordination with asynchronous Tor shutdown still requires device evidence.

No persisted wallet/backup migration is introduced. MAIN/DECOY LWS key separation from the
previous iteration remains. Release now embeds the same required jobs rather than invoking
another workflow; regenerate its marked block from Tests after shared-job edits. Actions,
permissions, protected signing, artifact checks and opt-in publication remain intact.

Next priorities: explicitly authorized CI for the current tree; deterministic instrumented
runner/device infrastructure; native listener/global operation lifetime; RPC trust consent
with UR key-image compatibility; signed NERO file binding; backup-passphrase policy and
wallet failure-injection fixtures. Dependency/toolchain upgrades are deferred until evidence
justifies them; the current pins compiled in existing baseline CI.

Threat models recorded before edits: [core](development-iteration-core.md),
[auxiliary wallets](development-iteration-wallet.md), [native](development-iteration-native.md),
[network](development-iteration-network.md), [CI](development-iteration-ci.md).
Rollback is source-only as described there; never delete retained recovery files or restore
an insecure fallback to make a failure disappear.

Documentation updated: README, repository/app/native/workflow AGENTS, applicable skills,
architecture/JNI/network/security/development/features, CI inventory, TODO/ROADMAP/PROGRESS.
All conclusions above distinguish source implementation from compilation, automated tests
and device behavior; static inspection is not a security, anonymity or fund-safety proof.

## Reviewable working-tree code inventory

Includes the preserved prior audit plus this iteration; documentation is listed above.

- `.github/workflows/release.yml`
- `.github/workflows/test.yml`
- `app/src/androidTest/java/io/anonero/DiagnosticIsolationTest.kt`
- `app/src/androidTest/java/io/anonero/LwsProfileIsolationTest.kt`
- `app/src/androidTest/java/io/anonero/network/TransportPersistenceTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/cpp/anonero.cpp`
- `app/src/main/java/io/anonero/AnonApplication.kt`
- `app/src/main/java/io/anonero/di/AppModule.kt`
- `app/src/main/java/io/anonero/model/Coins.kt`
- `app/src/main/java/io/anonero/model/RetainedWallets.kt`
- `app/src/main/java/io/anonero/model/WalletManager.kt`
- `app/src/main/java/io/anonero/network/NodeSelectionPolicy.kt`
- `app/src/main/java/io/anonero/network/TransportController.kt`
- `app/src/main/java/io/anonero/network/TransportModels.kt`
- `app/src/main/java/io/anonero/services/AnonNeroService.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/services/MoneroHandlerThread.kt`
- `app/src/main/java/io/anonero/services/TorService.kt`
- `app/src/main/java/io/anonero/services/WalletState.kt`
- `app/src/main/java/io/anonero/store/DiagnosticPolicy.kt`
- `app/src/main/java/io/anonero/store/LogsDataStore.kt`
- `app/src/main/java/io/anonero/ui/TorSplash.kt`
- `app/src/main/java/io/anonero/ui/home/CoinsScreen.kt`
- `app/src/main/java/io/anonero/ui/home/PayToMany.kt`
- `app/src/main/java/io/anonero/ui/home/accounts/AccountsScreen.kt`
- `app/src/main/java/io/anonero/ui/home/settings/DecoySettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/Logs.kt`
- `app/src/main/java/io/anonero/ui/home/settings/LwsSettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/NetworkSettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/NodeSettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/ProxySettings.kt`
- `app/src/main/java/io/anonero/ui/home/settings/SecureWipe.kt`
- `app/src/main/java/io/anonero/ui/home/sign/SignVerifyScreen.kt`
- `app/src/main/java/io/anonero/ui/onboard/restore/RestorePreview.kt`
- `app/src/main/java/io/anonero/ui/util/AnonLogTree.kt`
- `app/src/main/java/io/anonero/util/MultiPaymentPolicy.kt`
- `app/src/main/java/io/anonero/util/Prefs.kt`
- `app/src/main/java/io/anonero/util/PrivacySettings.kt`
- `app/src/main/java/io/anonero/util/WalletProfiles.kt`
- `app/src/main/java/io/anonero/util/backup/BackupHelper.kt`
- `app/src/main/res/values-b+zh+Hans/strings_development.xml`
- `app/src/main/res/values-es-rES/strings_development.xml`
- `app/src/main/res/values-ru-rRU/strings_development.xml`
- `app/src/main/res/values/strings_development.xml`
- `app/src/test/java/io/anonero/model/RetainedWalletsTest.kt`
- `app/src/test/java/io/anonero/network/NodeSelectionPolicyTest.kt`
- `app/src/test/java/io/anonero/network/TransportPolicyTest.kt`
- `app/src/test/java/io/anonero/services/WalletStateSessionTest.kt`
- `app/src/test/java/io/anonero/store/DiagnosticPolicyTest.kt`
- `app/src/test/java/io/anonero/util/LwsConfigTest.kt`
- `app/src/test/java/io/anonero/util/MultiPaymentPolicyTest.kt`
- `scripts/check-anonymity-boundaries.py`
- `scripts/check-i18n.py`
- `scripts/check-jni-contract.py`
- `scripts/check-kotlin-cancellation.py`
- `scripts/check-native-diagnostics.py`
- `scripts/sync-release-test-jobs.py`
- `scripts/tests/test_i18n_catalogs.py`
- `scripts/tests/test_jni_constructor_contract.py`
- `scripts/tests/test_kotlin_cancellation.py`
- `scripts/tests/test_sync_release_jobs.py`

## Static verification recorded

Passed on the integrated working tree: ktlint formatting and lint on 44 changed/new Kotlin
files; clang-format on adapter/header; actionlint and yamllint on both workflows; offline
zizmor with no findings; JNI parity (131 externals/134 exports/three known orphans) plus four
constructor contracts; i18n (389 strings, two plurals, three translated locales); native,
Android anonymity, cancellation-import and shared-workflow source guards; Python AST/XML/
TOML parsing, bash syntax and git diff --check. No new dependency was installed. Ruff was
unavailable, so Python syntax/source checks are not claimed as full Python lint.

Compilation: pending for this uncommitted tree. JVM/Python/Android regression execution:
pending. Real-device/native/network validation: pending. Existing baseline CI is separately
identified above and does not validate these source changes.
