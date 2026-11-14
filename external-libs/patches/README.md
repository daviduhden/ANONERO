# ANONERO Monero wallet-API patch series

These patches extend the official Monero wallet API with the calls ANONERO's JNI
adapter uses. `apply.sh` applies every `*.patch` here in lexical order with
`git apply --index --3way`.

## Base

- Repository: `https://github.com/monero-project/monero.git`
- Commit: `85f036ba7019cd36f08e8a38a27c37ea7b522618` (v0.18.5.4)

## Origin and licence

Seven patches are adapted from the Monero patches in
`MrCyjaneK/monero_c` (`patches/monero/`, `develop`):

- `0004-use-proper-error-handling-in-get_seed.patch`
- `0005-UR-functions.patch`
- `0006-add-dummy-device-for-ledger.patch`
- `0007-polyseed.patch`
- `0008-coin-control.patch`
- `0017-drop-generate_translations_header.c-requirement.patch`
- `0011-add-monero-submodule-support.patch`

`monero_c` is licensed under LGPL-3.0; the licence text is in
`LICENSE-LGPL-3.0`. The patches modify Monero, which remains under its own
BSD-3-Clause licence. Keep this directory's licensing separate from the
Apache-2.0 application code.

`0008-enforce-tls-for-https-daemon-address.patch` is project-original and is
provided under Apache-2.0. It is not part of the `monero_c` series.

## Adaptations

- Rebased from the `monero_c` submodule commit
  `22578c3f7d7b4b6dd85ff7daa42a827d97cc53d0` onto the pinned
  `85f036ba7019cd36f08e8a38a27c37ea7b522618`.
- The `monero_c` RandomX-fork context in `.gitmodules` was replaced with the
  official `tevador/RandomX` entry. ANONERO does not need the iOS RandomX fork.
- No application source, wallet format, or consensus rule is changed. The
  patches add wallet-API surface and wire it to existing wallet2 code.

## What the series adds

- Polyseed creation, recovery, language list, and seed export.
- Coin control: `Monero::Coins`, `Monero::CoinsInfo`, `Wallet::coins()`,
  `Wallet::viewOnlyBalance()`, and preferred-input parameters on
  `createTransaction`/`createTransactionMultDest`.
- UR signing/export helpers and `Wallet::hasUnknownKeyImages()`.
- A dummy hardware-device implementation so `createWalletFromDevice` and
  `queryWalletDevice` build without a Ledger dependency.
- Proper `get_seed` error handling used by the legacy-mnemonic fallback.
- Removal of the cross-compiled `generate_translations_header` tool. Monero otherwise
  builds it for the Android ABI and then tries to execute it on the x86_64 build host.
  wallet2 embedded translations become an empty table; Android UI localization is
  unaffected.
- A `wallet_rpc_server` call-site update for the new preferred-input parameter, plus
  `CMAKE_CURRENT_SOURCE_DIR` paths so the submodule build does not touch the parent tree.
- Enforced TLS for `https://` daemon/LWS addresses: wallet2's autodetect mode would
  otherwise retry in cleartext when the TLS handshake fails. Plain `http://` endpoints
  keep the upstream autodetect behaviour; `.onion`/`.i2p` endpoints use `http://` and rely
  on their transport.

## Nested submodules

The patch series adds three gitlinks. `apply.sh` initializes them after the
patches are applied:

| Path | Commit | Upstream |
| --- | --- | --- |
| `external/bc-ur` | `d82e7c753e710b8000706dc3383b498438795208` | `MrCyjaneK/bc-ur` |
| `external/polyseed` | `bd79f5014c331273357277ed8a3d756fb61b9fa1` | `tevador/polyseed` |
| `external/utf8proc` | `3de4596fbe28956855df2ecb3c11c0bbc3535838` | `JuliaStrings/utf8proc` |

## Validation

- The original seven patches applied with `git apply --index --3way` on a clean checkout of
  the pinned base in CI before `0008` was added.
- `0008-enforce-tls-for-https-daemon-address.patch` has been checked with `git apply
  --check` against the pinned `src/wallet/api/wallet.cpp` and applies cleanly with
  `git apply --index --3way` after the earlier `wallet.cpp` hunks in a simulated sequence;
  a full eight-patch native CI run is still required.
- `git submodule update --init --recursive --depth 1` fetched all three new
  submodules at the commits above.
- `apply.sh` and the `Containerfile` use `struct CoinsInfo` and
  `viewOnlyBalance` as markers; both are present after the series.
- `apply.sh` also checks the TLS marker in `src/wallet/api/wallet.cpp`; an existing
  seven-patch checkout gets `0008` applied instead of being treated as complete.
- The native compile and link still require the manual `Tests` workflow. A
  successful patch application is not proof that the native build works.
