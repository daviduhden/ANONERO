# ANONERO documentation

This directory is for developers, reviewers, and operators who need to understand how the
project works. ANONERO is an anonymity-focused wallet: privacy and security are required
pillars, but anonymity is the primary product goal. These documents complement the
repository-level [`AGENTS.md`](../AGENTS.md) and the skills under `.agents/`; they do not
replace them.

## Start here

| Document | Audience | Contents |
| --- | --- | --- |
| [`README.md`](../README.md) | Users and new contributors | What ANONERO is, design priorities, features, platform, downloads, build entry points |
| [`docs/architecture/overview.md`](architecture/overview.md) | Android and wallet developers | Modules, state flow, lifecycle, persistence, transports, build boundaries |
| [`docs/architecture/network-transport.md`](architecture/network-transport.md) | Android, wallet, and anonymity reviewers | Transport providers, anonymity/trust model, proxy policy, node-network matrix, VPN/I2P limits |
| [`docs/architecture/anonymity-audit-2026-11.md`](architecture/anonymity-audit-2026-11.md) | Anonymity and security reviewers | Source-audit findings, implemented controls, regression requirements, residual risks |
| [`docs/architecture/jni-boundary.md`](architecture/jni-boundary.md) | Native and Kotlin developers | Kotlin/C++/wallet2 contracts, handles, memory, errors, callbacks |
| [`docs/security-model.md`](security-model.md) | Security and anonymity reviewers | Priority hierarchy, adversaries, assets, trust boundaries, invariants, limits |
| [`docs/features.md`](features.md) | Product and QA | Implemented wallet features, anonymity/privacy/security contribution, limitations |
| [`docs/development.md`](development.md) | Contributors | Repository layout, conventions, static checks, extension recipes |
| [`docs/build-and-release.md`](build-and-release.md) | Build and release engineers | Gradle, native archives, CI, signing, releases |
| [`docs/android-release-signing.md`](android-release-signing.md) | Release owners | Android signing custody, recovery and developer registration |
| [`docs/ci/README.md`](ci/README.md) | CI owners and maintainers | Historical failures, warnings, root causes, regression checks |
| [`docs/development-iteration-approved-safety.md`](development-iteration-approved-safety.md) | Maintainers | Approved safety proposals, source changes and validation still required |
| [`docs/architecture/repository-state.md`](architecture/repository-state.md) | Maintainers | Audited state, evidence, decisions, known gaps |
| [`docs/architecture/dependencies.md`](architecture/dependencies.md) | Maintainers | Dependency inventory, update record, blocked upgrades |
| [`PROGRESS.md`](../PROGRESS.md) | Maintainers | Current cycle progress, fixed issues, pending validation |
| [`ROADMAP.md`](../ROADMAP.md) | Maintainers | Prioritized themes and out-of-scope work |
| [`TODO.md`](../TODO.md) | Contributors | Actionable checkboxes |

## Current architecture in one paragraph

ANONERO is a Kotlin/Jetpack Compose Android application. UI state is derived from a single
managed wallet through `WalletState`; `AnonWalletHandler` owns wallet lifecycle, profile
selection, proxy application, LWS login, and wipe. Wallet operations cross a C++ JNI adapter
into the official Monero `wallet2` static libraries built by `external-libs/`. Network
traffic is routed through the selected transport (embedded Tor by default) and the wallet's
proxy setting; the optional LWS path uses that same endpoint and treats the server as an
untrusted index. Secrets are protected at rest by a Keystore-wrapped wallet password and a
PBKDF2 PIN gate. The design goal is to reduce linkability and metadata exposure; it does
not guarantee anonymity.

## Agent knowledge validation

The read-only [Python validator](agent-knowledge-validation.md) checks instruction
structure, local links and substantive maintenance alongside project changes.
Its regression module is CI-only; local invocation is static analysis.

## Documentation rules

- English is used for technical documentation, comments, and commit messages.
- Paths are relative to the repository root unless stated otherwise. In documentation
  tables, always display each document’s repository-root-relative path as its link text.
- Implemented behavior is described as implemented. Planned or blocked work is marked as
  such.
- Static analysis cannot prove runtime behavior; anonymity, privacy, and security claims
  cite the code and the CI check that would validate a change.
- When architecture, dependencies, formats, or security boundaries change, update the
  affected document and the relevant `AGENTS.md`/skill in the same change.

## Known repository caveats

The current checkout is not a complete native build environment by itself: the Monero
submodule is uninitialized locally. The wallet-API patch series is checked in under
`external-libs/patches/` and applies cleanly to the pinned commit, but the native compile
still requires the manual CI `native` job or local submodule initialization. See
[`architecture/repository-state.md`](architecture/repository-state.md) and
[`../external-libs/patches/README.md`](../external-libs/patches/README.md).


- [November source anonymity audit](architecture/anonymity-audit-2026-11.md): findings,
  implemented corrections, regression sources and dynamic/independent-review requirements.


- [Continued-development iteration](development-iteration-2026-11.md): core/auxiliary/native/
  network/CI changes, feature status, regression sources and read-only CI evidence.

- [Kotlin, JNI and transport hardening](development-iteration-modernization.md) records the source changes and pending device evidence.
