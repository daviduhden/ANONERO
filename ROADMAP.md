# ROADMAP

ANONERO is a Kotlin/Compose Android app with a C++/JNI Monero engine. C++ is the permanent
native language. Priorities are ordered by fund safety, correctness, compatibility, and
then maintainability.

## 1. Fund-safety and lifecycle hardening

- Keep the wallet lifecycle transactional: no partial MAIN profile, wrapped secret, or
  credential set may survive a failed create/restore.
- Add tests for failed creation, interrupted Keystore migration, restore-target rejection,
  and profile switching.
- Audit every `WalletManager` handle path for zero-handle, close-failure, and stale-listener
  behavior.
- Keep transaction review tied to the loaded unsigned object; do not reintroduce advisory
  destination data as a review source. The NERO signed-file binding API is still missing:
  do not claim the airgapped broadcast is verified until it can compare signed txids/outputs
  with the reviewed unsigned object.

## 2. Native build and JNI

- Validate the restored wallet-API patch series and obtain a green `native` CI run with
  CMake 4.4.5, NDK r30, Boost 1.92, zlib 1.4.
- Add a native/JNI test target for handle lifetime, null input, array-length mismatch, and
  listener replacement.
- Continue RAII modernization in the project-owned adapter; keep the vendored Monero tree
  untouched except through patches.
- Document every remaining raw JNI string path and either convert it to `ScopedUtfChars` or
  justify why it cannot fail.

## 3. Android and Compose

- Validate API 37.2/NDK 30/toolchain changes in CI.
- Add instrumented coverage for the lock overlay, background sync, decoy/duress selection,
  stealth notifications, secure wipe, and backup restore.
- Review accessibility labels, touch targets, and adaptive layouts.
- Keep `FLAG_SECURE`, backup exclusions, and the non-exported service intact.

## 4. Tor, LWS, and privacy

- Add deterministic tests for Tor restart and SOCKS re-application.
- Keep the Tor SOCKS endpoint transient; never persist it as a manual proxy. The flow is
  nullable and must emit `null` on stop/restart.
- Validate LWS login failure, trust reset, reconnect, host/port/TLS validation, and the
  per-transport LWS path. Trust must be re-cleared after a successful reconnect login.
- Verify that a transport change cancels in-flight LWS requests and never reuses the
  previous transport.
- Keep the cleartext manifest flag from becoming an implicit clearnet path.
- Document the remaining metadata exposure to daemons and LWS.

## 5. Dependencies and supply chain

- Keep all workflows manual-only and behind the owner `authorize` gate.
- Create/protect the `release` environment, branch protection, and workflow-file ownership.
- Keep the dependency record current.
- Add Gradle dependency locking/verification metadata in a CI-generated change.
- Re-evaluate OpenSSL 4.x, Polyseed/utf8proc, Debian base/snapshot, and targetSdk only with
  native/device evidence.
- Keep Actions pinned to full SHAs and release signing isolated.
- Keep `docs/ci/` current; every compile error, warning, crash, test failure, and sanitizer
  diagnosis requires a root cause, a global pattern search, and a regression check.
- Keep Android Lint green and the project-owned C++ `-Werror` gate green (validated in
  `43521054641`), then add sanitizer/fuzz coverage only with a working native matrix.
- Validate the multi-transport matrix (embedded/external Tor, i2pd/InviZible/official
  I2P Java, VPN, custom SOCKS5) on devices, including transport changes during sync and
  VPN-loss fail-closed behavior.
- Complete node discovery only with a vetted, privacy-preserving source; keep manual and
  saved-node selection as the safe baseline.
- Move instrumented tests to a KVM-capable runner or a device farm. The hosted Linux
  runner passed in `43528256395` but remains non-deterministic under TCG.
- Add a pinned format/lint gate for Kotlin, C++, Python, shell, and workflows.

## 6. Documentation and maintainability

- Keep `docs/` aligned with behavior, not intended behavior.
- Prefer tests and code references over duplicated prose.
- Record blocked work in `PROGRESS.md` and actionable items in `TODO.md`.
- Do not add speculative documentation.

## Out of scope

- Replacing the C++/JNI engine with another native runtime or FFI layer.
- Replacing Monero cryptography with custom primitives.
- Editing vendored Monero sources outside the patch series.
- Claiming runtime or security validation without CI/device evidence.
