---
name: native-jni
description: "Use when touching ANONERO's C++ JNI adapter or the Kotlin/native boundary: external declarations, JNI exports, handles, callbacks, native memory, or app/CMakeLists.txt. Also use when reviewing JNI symbol parity."
---

# Native JNI boundary

## When to use

- Adding, changing, or reviewing a Kotlin `external` method and its C++ export.
- Changing a Kotlin model whose constructor is called from JNI.
- Reviewing native handle lifetime, callbacks, local/global references, exceptions, or
  memory.
- Changing `app/CMakeLists.txt` linkage.
- Auditing `scripts/check-jni-contract.py` output.

Do not use for wallet policy (use `monero-wallet`) or pure C++ upstream work
(`external-libs/AGENTS.md`).

## Read first

- `AGENTS.md`
- `app/AGENTS.md`
- `app/src/main/cpp/AGENTS.md`
- `app/src/main/cpp/anonero.cpp`
- `app/src/main/cpp/anonero.h`
- `app/CMakeLists.txt`
- `app/src/main/java/io/anonero/model/Wallet.kt`
- `app/src/main/java/io/anonero/model/WalletManager.kt`
- `app/src/main/java/io/anonero/model/Coins.kt`
- `app/src/main/java/io/anonero/model/PendingTransaction.kt`
- `app/src/main/java/io/anonero/model/UnsignedTransaction.kt`
- `scripts/check-jni-contract.py`

## Invariants

- No alternate native runtime or FFI layer. No new C++ file unless the JNI boundary
  truly requires it.
- Kotlin `external` name and `Java_io_anonero_<package>_<Class>_<method>` export must
  match exactly. The static script checks external symbols and four reviewed model
  constructors/order and four listener callback descriptors. Remaining export
  signatures, bytecode and linkage require CI.
- JNI class names and constructor signatures are binary contracts. Update Kotlin and
  C++ together:
  - `io/anonero/model/TransactionInfo`
  - `io/anonero/model/Transfer`
  - `io/anonero/model/WalletListener`
  - `io/anonero/model/Wallet$Status`
  - `io/anonero/model/CoinsInfo`
- Handles are opaque `jlong` pointers. Never expose them to UI code or dereference an
  unchecked value.
- Keep one native listener adapter installed from startup through successful wallet2 close.
  `setListenerJ` replaces or clears the global Java target under the listener mutex. An
  in-flight callback retains a local reference to its original target. Kotlin serializes
  listener updates with close on its listener lifecycle monitor. Failed close preserves
  both handles.
- Never let a C++ exception cross into Kotlin. Catch at the boundary and return a
  fail-closed result.
- Check and release every JNI string/array/local reference; check `ExceptionCheck()`.
- No secrets or credentials in `LOGD`/`LOGE`.
- Validate array lengths and integer ranges before casting.

## Procedure

1. **Confirm the boundary is necessary.** Can wallet2 or Kotlin already do it? If not,
   proceed.
2. **Add the Kotlin declaration.** Use a stable name; add a Kotlin wrapper that
   validates inputs and maps failure to a safe value.
3. **Add the export.** Match the JNI signature exactly. Use existing helpers
   (`ScopedUtfChars`, `ScopedLocalRef`, `ScopedCallbackEnv`, `java2cpp_*`).
4. **Check the contract.** If a Kotlin model constructor is involved, update the
   `GetMethodID` signature and argument order in `newCoinsInfo` /
   `newTransactionInfo` / `newTransferInstance`.
5. **Handle failure.** Return `null`, `0`, `false`, or a status string that Kotlin
   already treats as failure. Do not return a plausible success on error.
6. **Document lifetime.** State who owns the returned handle and how it is released.
7. **Update the parity script only for intentional orphans.** Add a comment explaining
   why the export/declaration is unmatched.
8. **Do not build locally.** Require CI `jni-contract` and `native`; device behavior
   requires instrumented CI.

## Static checks

```sh
python3 scripts/check-jni-contract.py
grep -n "GetStringUTFChars" app/src/main/cpp/anonero.cpp
grep -n "ReleaseStringUTFChars" app/src/main/cpp/anonero.cpp
grep -n "NewGlobalRef\|DeleteGlobalRef" app/src/main/cpp/anonero.cpp
grep -n "ExceptionCheck" app/src/main/cpp/anonero.cpp
grep -n "reinterpret_cast<jlong>\|static_cast<jlong>" app/src/main/cpp/anonero.cpp
grep -n "LOGD\|LOGE" app/src/main/cpp/anonero.cpp
```

For each `GetStringUTFChars`, confirm a null check and a release on every path. For
each `GetMethodID`, confirm the class and signature still match Kotlin.

## Completion criteria

- [ ] Kotlin declaration and export names match.
- [ ] Constructor signatures and field order match the model.
- [ ] All JNI resources checked and released.
- [ ] No C++ exception crosses the boundary.
- [ ] Handle ownership and lifetime documented.
- [ ] Failure is fail-closed.
- [ ] No secret logging.
- [ ] `check-jni-contract.py` passes; native/CI evidence is required.


Project-owned native diagnostics permit fixed warning/error literals only; verbose/debug/
info lifecycle logging and arbitrary `.what()` disclosure are forbidden. wallet2 logging
initializes silent at JNI load and logger init. Keep `scripts/check-native-diagnostics.py`
passing, without claiming full exception containment or callback/handle lifetime proof.


`check-jni-contract.py` now validates CoinsInfo, Transfer, TransactionInfo and Wallet.Status
constructor parameter order plus derived JVM descriptors. It does not prove every export
signature. Output/history snapshot conversion fails atomically, checks allocations and uses
ScopedLocalRef; refresh errors preserve JVM failures or raise a fixed Java error. Keep the
CI-only mutation tests under scripts/tests; never execute them locally under agent policy.

A pending JNI read error must stop the current operation. Preserve the distinction between an empty message and unreadable bytes, check erased key-image element types, and place native input allocations within exception handling.

If a conversion leaves a JVM exception pending, stop the operation before
another JNI string acquisition or result allocation. A normal null input can
still use an established fallback when no exception is pending. Include CheckJNI
in the failure-path verification for changes to this boundary.

Keep seed offset conversion and wallet2 seed/key reads inside the JNI catch
boundary. Use a fixed JVM error on native failure and preserve pending JVM
exceptions. A failed Polyseed lookup may reach the legacy mnemonic only when
wallet2 returns normally with false. Secret display callers must surface the
localized failure without logging material or treating the failed read as an
unlock. See [seed JNI failure paths](../../../docs/ci/seed-jni-boundary.md).

Keep allocating transaction review accessors inside a native exception boundary.
A failed read must raise a fixed Java error rather than collapse into an ordinary
zero, empty string, or empty summary. Publish review ownership only after the
review model is built from the authoritative transaction data.

Native status snapshots must also work on the error path. Keep wallet2 status
reads and native string copies inside the exception boundary. On native
failure use a critical status or disconnected result. Credential verification,
re-key and store must preserve the existing failure signal so callers can
recover without assuming the persisted file is unchanged.

## Maintenance & Continuous Learning

When this subsystem changes, reconcile this procedure with the implementation and
record reusable constraints, root causes and remaining verification limits. Check the
closest AGENTS.md and the skills index for stale guidance. Run the read-only
`python3 scripts/validate-agent-knowledge.py --scope worktree` before review; inspect
AK004 warnings for relevance and run regression tests only in authorized CI.

Range-check every wallet2 atomic amount, balance, fee and dust value against the
signed Kotlin Long domain before publishing a model or transaction review. Cover
the historical callback and both account and all-account paths. Check sums at
both the payment-array boundary and Kotlin consumers. Reject negative metadata
before converting it to wallet2's unsigned formatter. The native compile-time
boundary assertion and formatter device tests do not exercise a live large wallet.

Address/account readers and mutations must preserve pending JVM errors and
translate wallet2 failures into a fixed Java exception. Check account and
subaddress counts before narrowing to Int. wallet2's void account creation
and label setters can return after a rejected operation, so verify count or
raw label before the Kotlin caller stores and publishes success.

Transaction-note reads and transaction-key reads return nullable Kotlin strings.
Keep missing inputs, unavailable handles and native exceptions distinguishable
from a successful empty wallet2 value. Note writes must report failure before
their caller stores the wallet. Keep note text, transaction IDs and keys out of
diagnostics.
