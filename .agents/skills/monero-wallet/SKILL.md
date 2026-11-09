---
name: monero-wallet
description: "Use when implementing or reviewing Monero wallet behavior: create and restore, accounts and subaddresses, balances and history, UTXOs and coin control, transaction construction, fees, signing, key images and outputs, synchronization, or wallet file lifecycle."
---

# Monero wallet behavior

## When to use

- Wallet creation, seed restore, passphrase, restore height, or wallet file lifecycle.
- Accounts, subaddresses, labels, balances, history, notes, transactions.
- UTXO listing, freeze/thaw, coin control, send, sweep, pay-to-many, fee estimate.
- Message signing/verification, key-image/output export/import, unsigned/signed
  transaction exchange.
- Sync, background sync, light-wallet mode, or daemon connection behavior.

Do not use for UI-only changes, Keystore/PIN internals, or Tor proxying; use the
matching skill.

## Read first

- `AGENTS.md`
- `app/AGENTS.md`
- `app/src/main/java/io/anonero/model/Wallet.kt`
- `app/src/main/java/io/anonero/model/WalletManager.kt`
- `app/src/main/java/io/anonero/model/Coins.kt`
- `app/src/main/java/io/anonero/model/CoinsInfo.kt`
- `app/src/main/java/io/anonero/model/TransactionHistory.kt`
- `app/src/main/java/io/anonero/model/TransactionInfo.kt`
- `app/src/main/java/io/anonero/model/PendingTransaction.kt`
- `app/src/main/java/io/anonero/model/UnsignedTransaction.kt`
- `app/src/main/java/io/anonero/model/SigningSummary.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/services/MoneroHandlerThread.kt`
- `app/src/main/java/io/anonero/ui/onboard/OnboardViewModel.kt`
- `app/src/main/java/io/anonero/util/MoneroAmount.kt`

## Invariants

- Amounts are atomic units (`Long`) internally. Parse user input with
  `MoneroAmount.parseExact`, never `Double` or `getAmountFromDouble`, for new code.
  Reject zero, negative, over-precision, and overflow before constructing a
  transaction.
- Polyseed is 16 words; legacy mnemonics are 25 words. Passphrase handling and
  restore height are wallet2 semantics; do not reimplement them.
- Do not reuse subaddresses. Use `getLatestSubAddress` / `getNewAddress` and preserve
  the existing “no subaddress reuse” behavior.
- wallet2 is the authority for fee, ring size, output selection, change, and signing.
  Do not add a local fee formula or consensus rule.
- Multi-destination payments are one wallet2 transaction set, not several sends.
  Validate destination/amount counts and use exact addition (`Math.addExact`).
- Unsigned transaction review must use wallet2's `confirmationMessage` and
  `SigningSummary`. Do not build review rows from advisory destination getters.
- Sign the loaded `UnsignedTransaction` object (`UnsignedTransaction.sign`) when the
  user reviewed that object. Do not silently reload and sign a different file.
- Persist after mutation: `store()` after account/subaddress/label/note/freeze changes
  and after transaction broadcast. Pause refresh before a backup copy and resume after.
- View-only `nero` has no spend key. Use `viewOnlyBalance` paths and do not add spend,
  decoy, or backup-export behavior.
- Never log seeds, passphrases, wallet passwords, private keys, or full transaction
  details in sensitive paths.

## Procedure

1. **Identify the wallet2 operation.** Find the existing Kotlin method and JNI export.
   If it does not exist, use `native-jni` and add the boundary carefully.
2. **Trace the data flow.** UI -> ViewModel -> `WalletState`/`AnonWalletHandler` ->
   `Wallet` -> JNI -> wallet2. Note whether the path mutates the wallet or disk.
3. **Preserve value semantics.** Keep amounts atomic and exact. Keep direction,
   account, and address indices as wallet2 indices.
4. **Preserve transaction semantics.** Do not split a multi-destination payment,
   change the output set, or substitute a locally computed fee.
5. **Handle failure as failure.** A null/zero handle, empty txid, non-OK status, or
   wallet2 error string must not be shown or treated as success.
6. **Persist and refresh.** Call `store()`, `refreshHistory()`, and the appropriate
   coin refresh after mutations.
7. **Test pure logic in JVM.** Amount parsing, summary parsing, label rules, and
   migration logic can be unit-tested. Wallet2 behavior needs CI/device evidence.
8. **Check both flavors.** `anon` and `nero` differ in spend capability and balance
   source.

## Static checks

```sh
grep -rn "getAmountFromDouble\|toDouble()\|Double" app/src/main/java/io/anonero/ui app/src/main/java/io/anonero/model
grep -rn "createTransaction\|createSweepTransaction\|createTransactionMultDest" app/src/main/java
grep -rn "store()\|refreshHistory()\|disposePendingTransaction" app/src/main/java/io/anonero
grep -rn "confirmationMessage\|SigningSummary" app/src/main/java/io/anonero
grep -rn "SWEEP_ALL\|Long.MAX_VALUE" app/src/main/java/io/anonero
python3 scripts/check-jni-contract.py
```

## Completion criteria

- [ ] No floating-point amount path added.
- [ ] No subaddress reuse.
- [ ] wallet2 remains authoritative for fees, selection, and signing.
- [ ] Unsigned review uses wallet2's confirmation summary.
- [ ] Failure values cannot be mistaken for success.
- [ ] Mutations persist and refresh.
- [ ] `anon` and `nero` behavior considered.
- [ ] No secret logging.
- [ ] CI evidence required; static review is not fund-safety proof.
