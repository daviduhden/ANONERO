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
  match exactly. The static script checks class/method names, not signatures; signatures
  and linkage require CI.
- JNI class names and constructor signatures are binary contracts. Update Kotlin and
  C++ together:
  - `io/anonero/model/TransactionInfo`
  - `io/anonero/model/Transfer`
  - `io/anonero/model/WalletListener`
  - `io/anonero/model/Wallet$Status`
  - `io/anonero/model/CoinsInfo`
- Handles are opaque `jlong` pointers. Never expose them to UI code or dereference an
  unchecked value.
- One owner per handle. `setListenerJ` must delete the old global reference and native
  listener before replacing it.
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
