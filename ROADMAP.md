# ROADMAP

ANONERO is a Kotlin/Compose Android app with a C++/JNI Monero engine. C++ is the permanent
native language. Anonymity is the primary design goal, with privacy and fund safety as
required constraints, alongside correctness, compatibility and maintainability.

## 1. Fund-safety and lifecycle hardening

- Exercise failed password rollback, process restart and snapshot recovery on the same legacy wallet fixture before accepting device migration.

- Keep the wallet lifecycle transactional: no partial MAIN profile, wrapped secret, or
  credential set may survive a failed create/restore.
- Add tests for failed creation, interrupted Keystore migration, restore-target rejection,
  and profile switching.
- Exercise managed slot reservation and failed-close retention in both flavors.
  Continue JNI handle-consumer and stale-listener audits, including raw wrappers
  captured before a session closes.
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
- Require a green Kotlin compile/lint gate after structural refactors; CI-033 was a
  nullable-preference/import regression, not a runtime-only issue.
- Add instrumented coverage for the lock overlay, background sync, decoy/duress selection,
  stealth notifications, secure wipe, and backup restore.
- Review accessibility labels, touch targets, and adaptive layouts.
- Keep `FLAG_SECURE`, backup exclusions, and the non-exported service intact.

## 4. Tor, LWS, and anonymity

- Add deterministic tests for Tor restart and SOCKS re-application.
- Keep the Tor SOCKS endpoint transient; never persist it as a manual proxy. The flow is
  nullable and must emit `null` on stop/restart.
- Validate LWS login failure, trust reset, reconnect, host/port/TLS validation, and the
  per-transport LWS path. Trust must be re-cleared after a successful reconnect login.
- Verify that a transport change cancels in-flight LWS requests and never reuses the
  previous transport.
- Keep the cleartext manifest flag from becoming an implicit clearnet path.
- Add an anonymity regression checklist for new transports, LWS changes, logging, and UI:
  identifiers, DNS, operator switching, metadata, and correlation surfaces.
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


## Anonymity architecture gate

For each new feature document additional disclosure, identity/connection/activity linking,
and exposure reduction compatible with fund safety. The November source hardening is
implemented but requires explicit CI/device evidence. Prioritize callback/socket lifetime,
RPC trust consent and unmanaged-close recovery before claiming broader isolation. See
[`source audit`](docs/architecture/anonymity-audit-2026-11.md); native/transport correctness
and traffic correlation need independent review.


## Next evidence after the continued-development iteration

Validate retained ownership recovery, atomic JNI snapshots and payment/account/output
failure paths in CI/devices. Validate dispatch-only release job synchronization and the
static-checker mutation tests. Native listener lifetime/global lifecycle serialization,
RPC trust consent, signed-file binding, backup-passphrase policy and hosted emulator
reliability remain higher-risk work. The current toolchain built in existing baseline CI;
no unvalidated dependency upgrade is introduced by this iteration.

## Evidence after Kotlin, JNI and transport hardening

Prioritize listener lifetime and session serialization, then verify stale proxy/LWS
requests and explicit-coin sends on devices. Preserve the fail-closed decisions and
establish exact signed-transaction binding before claiming safe NERO broadcasts.
The [iteration record](docs/development-iteration-modernization.md) lists the
regressions and remaining evidence.


## CI audit follow-up

Confirm the latest warning fixes and independent instrumented test exit results in a
manual run. A stable KVM-backed API 35 smoke run will still leave archival API 29 build
coverage, native ARMv7 device runtime, release signing, sanitizers and Tor/LWS integration
as separate evidence gaps.

## Next CI evidence

Recheck both API 29 ARM wrapper selections, software-only emulator recovery,
and rejection of native failures after the current patch. Validate accepted
broadcast recovery independently from transaction authorization. Track the
remaining Lint persistence advisories, signed release execution and sanitizer
coverage separately from the green API 35 baseline.

The [approved safety proposals](docs/development-iteration-approved-safety.md)
track the remaining work on signed-file binding, native lifetime, backup
credentials, dependency verification and device coverage.
