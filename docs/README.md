# ANONERO documentation

This directory is for developers, reviewers, and operators who need to understand how the
project works. It complements the repository-level [`AGENTS.md`](../AGENTS.md) and the
skills under `.agents/`; it does not replace them.

## Start here

| Document | Audience | Contents |
| --- | --- | --- |
| [`../README.md`](../README.md) | Users and new contributors | What ANONERO is, features, platform, downloads, build entry points |
| [`architecture/overview.md`](architecture/overview.md) | Android and wallet developers | Modules, state flow, lifecycle, persistence, Tor/LWS, build boundaries |
| [`architecture/jni-boundary.md`](architecture/jni-boundary.md) | Native and Kotlin developers | Kotlin/C++/wallet2 contracts, handles, memory, errors, callbacks |
| [`security-model.md`](security-model.md) | Security reviewers | Assets, trust boundaries, invariants, privacy limits, known issues |
| [`features.md`](features.md) | Product and QA | Implemented wallet features, flows, and limitations |
| [`development.md`](development.md) | Contributors | Repository layout, conventions, static checks, extension recipes |
| [`build-and-release.md`](build-and-release.md) | Build and release engineers | Gradle, native archives, CI, signing, releases |
| [`architecture/repository-state.md`](architecture/repository-state.md) | Maintainers | Audited state, evidence, decisions, known gaps |
| [`architecture/dependencies.md`](architecture/dependencies.md) | Maintainers | Dependency inventory, update record, blocked upgrades |
| [`../PROGRESS.md`](../PROGRESS.md) | Maintainers | Current cycle progress, fixed issues, pending validation |
| [`../ROADMAP.md`](../ROADMAP.md) | Maintainers | Prioritized themes and out-of-scope work |
| [`../TODO.md`](../TODO.md) | Contributors | Actionable checkboxes |

## Current architecture in one paragraph

ANONERO is a Kotlin/Jetpack Compose Android application. UI state is derived from a single
managed wallet through `WalletState`; `AnonWalletHandler` owns wallet lifecycle, profile
selection, proxy application, LWS login, and wipe. Wallet operations cross a C++ JNI adapter
into the official Monero `wallet2` static libraries built by `external-libs/`. Network
traffic is proxied through an embedded Tor daemon; the optional LWS path uses the built-in
Tor endpoint and treats the server as an untrusted index. Secrets are protected at rest by
a Keystore-wrapped wallet password and a PBKDF2 PIN gate.

## Documentation rules

- English is used for technical documentation, comments, and commit messages.
- Paths are relative to the repository root unless stated otherwise.
- Implemented behavior is described as implemented. Planned or blocked work is marked as
  such.
- Static analysis cannot prove runtime behavior; security-sensitive claims cite the code
  and the CI check that would validate a change.
- When architecture, dependencies, formats, or security boundaries change, update the
  affected document and the relevant `AGENTS.md`/skill in the same change.

## Known repository caveats

The current checkout is not a complete native build environment: the Monero submodule is
uninitialized and the ANONERO wallet-API patch series is absent from
`external-libs/patches/`. The Android build and release pipeline are documented as designed,
but native reproducibility cannot be verified until those inputs are restored. See
[`architecture/repository-state.md`](architecture/repository-state.md).
