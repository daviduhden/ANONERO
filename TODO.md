# TODO

Actionable work items. Keep this list short; move historical detail to `PROGRESS.md` and
the rationale to `ROADMAP.md`.

## Critical

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
- [ ] Create the protected `release` environment with required reviewers and the four
      signing secrets; confirm no other collaborator can run workflows from a modified ref.
- [ ] Protect `.github/workflows/` and `main` with branch protection/CODEOWNERS.
- [ ] Pin the expected release certificate SHA-256 and verify every signed APK against it.
- [ ] Verify every archive imported by `app/CMakeLists.txt`, not only `libwallet_api.a`;
      the native archives and adapter link now build in CI.
- [ ] Validate the Android toolchain (AGP 9.5.1, Gradle 9.9.0, API 37.2, NDK 30, CMake 4.1.2).
- [ ] Run instrumented tests for lock/unlock, background sync, decoy/duress, and wipe.

## Correctness and tests

- [ ] Run the manual Tests workflow on the audit commit (formatting, lint, and CI changes)
      and collect the new instrumented diagnostics.
- [ ] Device-test the corrupt `nodes_store` path: add/remove must fail visibly, must not
      overwrite the file, and backup export must fail instead of omitting nodes.
- [ ] Decide the instrumented runner from the captured logcat/meminfo: self-hosted KVM or
      device farm. Do not keep tuning RAM/timeouts without data.
- [ ] Turn on `-Werror` for the project-owned C++ target once the formatted tree builds
      warning-free in CI.
- [ ] Add a format/lint CI gate with pinned ktlint, clang-format, Ruff, shfmt, shellcheck,
      yamllint, zizmor, and actionlint versions.
- [ ] Add sanitizer/fuzz jobs (ASan/UBSan/TSan/LeakSanitizer) after native inputs are
      restored; document emulator/runtime limits.
- [ ] Make `check-jni-contract.py` class-aware; suffix matching can pair
      `Wallet_getStatusJ` with `PendingTransaction.getStatusJ` and hide a class mismatch.
- [ ] Extend JNI contract checks to signatures/constructor descriptors where derivable
      statically.
- [ ] Add a restore fixture for a v2 PIN-keyed backup whose external passphrase differs
      from the PIN, and verify both open attempts and the rollback path.
- [ ] Move remaining main-thread wallet reads out of composition (`HomeGraph`, seed/key
      screens, `SeedSettings` address/NERO export, `Send` coin reads, transaction detail)
      into `Dispatchers.IO` state producers.
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
- [ ] Convert remaining raw JNI string paths to `ScopedUtfChars` where practical.

## Security and privacy

- [ ] Validate Tor restart/SOCKS re-application and LWS trust reset on a device.
- [ ] Decide whether a failed `startBackgroundSync` should close/reset the wallet or keep
      the lock overlay and document the chosen recovery path.
- [ ] Review the cleartext manifest flag and confirm no new unproxied client is introduced.
- [ ] Add a device test for stealth notifications, clipboard clearing, and `FLAG_SECURE`.
- [ ] Harden v1/v2 backup deserialization validation for node host/port and other untrusted
      fields, including negative heights and malformed hosts.
- [ ] Add a Gradle dependency lockfile/verification metadata in a CI-generated change.
- [ ] Verify the Kotlin/C++ architecture policy check in CI.
- [ ] Decide whether to keep the narrow IPv6 proxy validator or broaden it safely.

## Features and UX

- [ ] Validate NERO view-only unsigned/signed transaction exchange end to end.
- [ ] Validate pay-to-many, coin control, freeze/thaw, and message sign/verify.
- [ ] Audit accessibility labels, touch targets, and adaptive layouts.
- [ ] Review the fixed targetSdk 37.0 vs compileSdk 37.2 decision.

## Documentation and maintenance

- [ ] Keep `docs/architecture/dependencies.md` current after every dependency change.
- [ ] Update `PROGRESS.md` at the end of each development cycle.
- [ ] Remove the `ui/NavGraph.kt` stub in a dedicated cleanup change if it remains unused.
- [ ] Do not commit or push without explicit supervisor authorization.
