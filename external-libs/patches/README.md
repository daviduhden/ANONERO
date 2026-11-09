# ANONERO Monero wallet-API patch series

These patches extend the official Monero wallet API with the calls ANONERO's JNI
adapter uses. `apply.sh` applies every `*.patch` here in lexical order with
`git apply --index --3way`.

## Base

- Repository: `https://github.com/monero-project/monero.git`
- Commit: `85f036ba7019cd36f08e8a38a27c37ea7b522618` (v0.18.5.4)

## Origin and licence

The series is adapted from the Monero patches in
`MrCyjaneK/monero_c` (`patches/monero/`, `develop`):

- `0004-use-proper-error-handling-in-get_seed.patch`
- `0005-UR-functions.patch`
- `0006-add-dummy-device-for-ledger.patch`
- `0007-polyseed.patch`
- `0008-coin-control.patch`

`monero_c` is licensed under LGPL-3.0; the licence text is in
`LICENSE-LGPL-3.0`. The patches modify Monero, which remains under its own
BSD-3-Clause licence. Keep this directory's licensing separate from the
Apache-2.0 application code.

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

## Nested submodules

The patch series adds three gitlinks. `apply.sh` initializes them after the
patches are applied:

| Path | Commit | Upstream |
| --- | --- | --- |
| `external/bc-ur` | `d82e7c753e710b8000706dc3383b498438795208` | `MrCyjaneK/bc-ur` |
| `external/polyseed` | `bd79f5014c331273357277ed8a3d756fb61b9fa1` | `tevador/polyseed` |
| `external/utf8proc` | `3de4596fbe28956855df2ecb3c11c0bbc3535838` | `JuliaStrings/utf8proc` |

## Validation

- Every patch applies with `git apply --index --3way` on a clean checkout of the
  pinned base.
- `git submodule update --init --recursive --depth 1` fetched all three new
  submodules at the commits above.
- `apply.sh` and the `Containerfile` use `struct CoinsInfo` and
  `viewOnlyBalance` as markers; both are present after the series.
- The native compile and link still require the manual `Tests` workflow. A
  successful patch application is not proof that the native build works.
