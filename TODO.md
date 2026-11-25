# TODO

Actionable work items. Keep this list short; move historical detail to `PROGRESS.md` and
the rationale to `ROADMAP.md`.

## Critical

- [ ] Validate managed-wallet close/replacement and incomplete profile deletion with
      the JVM and device cases in [MW-001](docs/ci/managed-wallet-lifetime.md).

- [ ] Review the LGPL-3.0 licensing implications of the restored `monero_c` patch series
      before redistribution; see `external-libs/patches/README.md` and
      `LICENSE-LGPL-3.0`.

- [ ] Bind an imported signed NERO transaction to the reviewed unsigned object. wallet2
      currently exposes no parser for the signed file, so a compromised external signer can
      return a different destination/amount. Requires a patched native signed-file getter
      (txids/outputs) and a regression fixture before claiming fund safety.
- [ ] Decide and enforce a backup-passphrase policy independent of the seed passphrase;
      short/empty seed passphrases currently produce weakly keyed backups.
- [ ] Validate the restored wallet-API patch series in the manual `native` CI job once the
      Debian snapshot backend recovers; the series applies to `85f036ba` but the native
      archives build in CI.
- [x] Configure separate signing environments and certificate pins for ANON and NERO.
      Signed release execution remains unverified.
- [ ] Protect `.github/workflows/` and `main` with branch protection/CODEOWNERS.
- [ ] Document and test native full-node trust separately from saved node ranking.
- [ ] Verify every archive imported by `app/CMakeLists.txt`, not only `libwallet_api.a`;
      the native archives and adapter link now build in CI.
- [ ] Validate the Android toolchain (AGP 9.5.1, Gradle 9.9.0, API 37.2, NDK 30, CMake 4.1.2).
- [ ] Run instrumented tests for lock/unlock, background sync, decoy/duress, and wipe.

## Network transports

- [ ] Device-test embedded Tor, Orbot, InviZible Tor, system VPN, i2pd, InviZible I2P,
      official I2P for Android, and custom SOCKS5 against real `.onion`/`.i2p`/clearnet
      nodes.
- [ ] Validate the official I2P Java I2PTunnel SOCKS setup, package detection, SAM/I2CP
      availability differences, and interactive SAM authorization without assuming a
      default SOCKS port.
- [ ] Verify Android VPN routing for ANONERO specifically and document that
      `TRANSPORT_VPN` does not prove per-app inclusion or DNS protection.
- [ ] Validate wallet2 `socks5://` remote resolution and the opt-in `https://` daemon path
      against the pinned native build.
- [x] Move onboarding proxy setup onto the common Network & Nodes editor. Legacy direct
      proxy/preference mutation APIs were removed; device validation remains pending.
- [ ] Add automatic recovery/re-apply for external Orbot, InviZible, i2pd, and custom proxy
      failures; today the user must re-apply after a startup failure.
- [ ] Add a vetted, privacy-preserving node-discovery source or keep the saved-node
      catalog as the documented discovery boundary.
- [ ] Add instrumented tests for transport changes during sync, VPN loss, and a
      transaction broadcast attempt while the transport is unavailable.

## Correctness and tests

- [x] Re-run the `Tests` workflow after the CI-033/CI-034 fixes. `43633496677`
      (`a2c9cfb`) passed all non-instrumented jobs; only the API 35 emulator failed with
      adb exit 224 (CI-035).

- [ ] Decide the instrumented runner: `43528256395` passed on hosted, but `43521054641`
      failed before the script. Use a self-hosted KVM runner or a device farm for
      deterministic gates; do not add blind retries.
- [ ] Device-test the corrupt `nodes_store` path: add/remove must fail visibly, must not
      overwrite the file, and backup export must fail instead of omitting nodes.
- [ ] Add a format/lint CI gate with pinned ktlint, clang-format, Ruff, shfmt, shellcheck,
      yamllint, zizmor, and actionlint versions.
- [ ] Add sanitizer/fuzz jobs (ASan/UBSan/TSan/LeakSanitizer) after native inputs are
      restored; document emulator/runtime limits.
- [x] Check four JNI model constructor descriptors and parameter order statically.
- [ ] Extend signature checking to remaining exports and verify bytecode/linkage in CI.
- [ ] Add a restore fixture for a v2 PIN-keyed backup whose external passphrase differs
      from the PIN, and verify both open attempts and the rollback path.
- [x] Move remaining main-thread wallet reads out of composition (`HomeGraph`, seed/key
      screens, `SeedSettings` address/NERO export, `Send` coin reads, transaction detail)
      into `Dispatchers.IO` state producers. `HomeGraph` now uses the cached
      `WalletState.walletStatus`; sign/verify, seed/address export, pin change, and coin
      selection run on IO with cancellation preserved.
- [ ] Add a native/JNI test fixture for signed-transaction binding once the wallet2 getter
      exists.
- [ ] Test backup interruption between ZIP and encryption and assert no plaintext remains.
- [ ] Test malformed/oversized backup archives, truncated v2 headers, and v1 without
      `Salted__`.
- [ ] Add tests for failed create/restore cleanup and interrupted Keystore migration.
- [ ] Add a backup restore test for the v1 node fallback and failed bind cleanup.
- [ ] Add tests for a broadcast without a pending transaction and post-broadcast refresh
      failure.
- [ ] Add JNI boundary tests for null strings, zero handles, length mismatches, and listener
      replacement.
- [x] Convert remaining raw JNI string paths to `ScopedUtfChars` (array and `ArrayList`
      readers now use `ScopedUtfChars` + `ScopedLocalRef`; native CI still required).

## Anonymity, privacy, and security

- [ ] Add an anonymity regression checklist/test where practical: no new persistent
      identifiers, no transport bypass, no DNS leak, no silent LWS operator switch, and no
      new correlation metadata.
- [ ] Validate Tor restart/SOCKS re-application and LWS trust reset on a device, including
      LWS over external Tor/I2P, system VPN, and a custom proxy.
- [ ] Decide whether a failed `startBackgroundSync` should close/reset the wallet or keep
      the lock overlay and document the chosen recovery path.
- [ ] Review the cleartext manifest flag and confirm no new unproxied client is introduced.
- [ ] Add a device test for stealth notifications, clipboard clearing, and `FLAG_SECURE`.
- [x] Harden v1/v2 backup deserialization validation for node host/port and other
      untrusted fields, including negative heights and malformed hosts. Backup node
      records now pass `BackupNodePolicy`; invalid records are dropped and the active node
      is written with the build's network type. Restore height was already bounded.
- [ ] Add a Gradle dependency lockfile/verification metadata in a CI-generated change.
- [ ] Verify the Kotlin/C++ architecture policy check in CI.
- [x] Broaden the legacy proxy validator: it now delegates to
      `ProxyEndpoint.isNumericOrLoopback`, including compressed IPv6 and loopback names.

## Features and UX

- [ ] Validate NERO view-only unsigned/signed transaction exchange end to end.
- [ ] Validate pay-to-many, coin control, freeze/thaw, and message sign/verify.
- [ ] Audit accessibility labels, touch targets, and adaptive layouts.
- [ ] Review the fixed targetSdk 37.0 vs compileSdk 37.2 decision.

## Documentation and maintenance

- [ ] Keep `docs/architecture/dependencies.md` current after every dependency change.
- [ ] Update `PROGRESS.md` at the end of each development cycle.
- [x] Remove the `ui/NavGraph.kt` stub; the active graphs are `HomeGraph.kt` and
      `OnboardingGraph.kt`.
- [ ] Do not commit or push without explicit supervisor authorization.


## November source-audit follow-up

- [ ] Obtain explicitly authorized CI evidence for the new anonymity regressions; no run
      was started. Validate API 29+, packet-level DNS/VPN behavior and stale socket closure.
- [ ] Stress delayed callbacks and profile/transport transitions; entry guards are not
      native lifetime synchronization.
- [ ] Redesign RPC trust with upstream fixtures while preserving trusted-daemon-dependent
      UR key-image import; do not blindly flip all RPC trust flags.
- [x] Retain/recover unmanaged wallets on close failure and guard profile deletion;
      failure-injection regression sources are prepared, execution remains pending.
- [ ] Assess authenticated SOCKS circuit isolation against actual wallet2/Tor capabilities.
      Current shared transport/RPC contexts can still link profiles.

## Continued-development validation

- [ ] Exercise retained-close retries, cancelled provisioning, atomic JNI snapshot failure,
      payment edits during address validation and failed freeze persistence on devices.
- [ ] Validate the dispatch-only embedded release suite and Python checker regressions.
- [ ] Resolve hosted-emulator system-service instability; optional animation writes were
      removed, but no passing instrumented result is claimed for this change.


## Current CI audit

- [ ] Validate the KVM permission preparation and the retained software path in a
      manual API 35 run. Check a passing XML and exit-code report for each flavor.
- [ ] Validate the JNI exception guards under CheckJNI and run the coin selection
      regressions in CI.
- [ ] Review remaining build-tool/dependency advisories and commit-result lint findings
      individually. Preserve API 29 and explicit persistence failure handling.

## Follow-up after Tests 44932222742

- [x] Confirm accelerated API 35 boot and both instrumented flavor reports on
      `81f17ef`. TCG-only boot and later local changes remain unverified.
- [ ] Verify W-008 is absent on both native ABI builds after the OpenSSL patch.
- [ ] Exercise wallet2 JNI failures and accepted broadcast with failed local
      refresh or persistence in CI or on a device.

- [ ] Validate the [approved safety changes](docs/development-iteration-approved-safety.md)
      on CI for their own revision. Exercise stale payment review, transport loss,
      listener replacement/close and the release instrumentation gate.
