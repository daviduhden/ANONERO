# C++ JNI adapter — Agent Instructions

These instructions apply to `app/src/main/cpp/` and `app/CMakeLists.txt`. The root
`AGENTS.md` remains authoritative for security, language, build, and Git policy.

## 1. Scope and language policy

- This directory contains the project-owned C++ JNI adapter (`anonero.cpp`,
  `anonero.h`).
- Kotlin is the primary language. C++ is the only project-owned native language and is
  retained for the JNI boundary and build glue.
- Do not add a new native runtime or FFI layer; JNI to C++ is the permanent boundary.
- Do not add new C++ source files unless the JNI boundary genuinely requires it. UI,
  wallet policy, formatting, validation, and cryptography belong in Kotlin or in the
  pinned Monero libraries.
- Do not edit `external-libs/monero` directly. Monero changes are patches under
  `external-libs/patches/` and are applied by `apply.sh`.

## 2. Contract with Kotlin

- Every Kotlin `external fun` / `external get` must have a matching
  `Java_io_anonero_*` export, and every export should have a Kotlin declaration.
  `scripts/check-jni-contract.py` checks symbol names only. It does **not** check
  signatures, types, or linkage.
- Known intentional orphans are listed in `scripts/check-jni-contract.py`
  (`Wallet_setSeedLanguage`, `Wallet_getStatusJ`, `Wallet_isSynchronizedJ`). Do not add
  to that list without a documented reason and a Kotlin-side review.
- JNI class names in `JNI_OnLoad` and in `GetMethodID` calls are a binary contract:
  - `io/anonero/model/TransactionInfo`
  - `io/anonero/model/Transfer`
  - `io/anonero/model/WalletListener`
  - `io/anonero/model/Wallet$Status`
  - `io/anonero/model/CoinsInfo`
- Constructor signatures are part of the contract. When changing a Kotlin model whose
  primary constructor is called from JNI, update the matching `GetMethodID` signature
  and the argument order together. Relevant sites:
  - `newCoinsInfo` in `anonero.cpp` and `model/CoinsInfo.kt`;
  - `newTransactionInfo` / `newTransferInstance` and `model/TransactionInfo.kt`,
    `model/Transfer.kt`;
  - status construction and `Wallet.Status`.
- Handles are `jlong` values holding raw C++ pointers. They are opaque to Kotlin.
  Never expose a raw pointer, dereference an unchecked handle, or keep a handle after
  the owning object is disposed.
- `PendingTransaction` and `UnsignedTransaction` handles are owned by wallet2. Dispose
  them through the existing wallet methods where provided; do not add a second owner
  or a finalizer that races with wallet2.
- `setListenerJ` installs a `MyWalletListener` with a global Java reference. Removing or
  replacing it must delete the old global reference and the native listener.

## 3. Memory and JNI rules

Use the existing helpers instead of open-coding:

- `ScopedUtfChars` for borrowed UTF-8; always check `get()` for null.
- `ScopedLocalRef` for local references that must be released before return.
- `ScopedCallbackEnv` for attaching/detaching native callbacks.
- `ScopedAtomicFlagReset` for re-entry guards.
- `java2cpp_long`, `java2cpp_bytes`, `java2cpp_string_array`, `java2cpp_set` for
  conversions.

Additional rules:

- Check every `GetStringUTFChars` result before constructing `std::string`.
- Release every `GetStringUTFChars` / `GetByteArrayElements` / `GetArrayLength`-paired
  resource. `JNI_ABORT` is correct for input byte arrays that must not be copied back.
- Delete local references in loops and for created `jstring`/`jobject` values.
- Check `env->ExceptionCheck()` after JNI calls that can throw; clear or propagate
  deliberately. Never leave a pending exception while returning a normal value.
- Never throw a C++ exception across the JNI boundary. Catch `std::exception` and
  `...` at the boundary, log a redacted message, and return a status/error string or
  a null/zero handle.
- Validate array lengths before indexing. The multi-destination and fee-estimate paths
  already reject empty or mismatched destination/amount arrays; preserve that.
- Validate `jint`/`jlong` ranges before casting to `uint32_t`, `uint64_t`, or enum
  values.
- Do not assume Java strings are non-null. Several existing methods check; new ones
  must too.
- Do not add `reinterpret_cast` from arithmetic values or use pointer values as
  integers beyond the handle pattern already in use.

## 4. Concurrency

- Native callbacks may arrive on wallet2 threads. `MyWalletListener` serializes
  callback delivery with `_listenerMutex`.
- `ScopedCallbackEnv` attaches the current thread and detaches only if it attached.
  Do not call `DetachCurrentThread` on a thread you did not attach.
- Do not hold `_listenerMutex` while calling into code that can re-enter the listener.
- Do not add blocking waits, sleeps, or network calls in JNI.
- Re-entry guards must be atomic and must reset only on the successful acquisition path
  (`ScopedAtomicFlagReset`).

## 5. Security

- Never log seeds, passphrases, PINs, wallet passwords, Keystore material, proxy
  credentials, or unredacted node credentials. `LOGD` output goes to logcat and may be
  persisted by the Kotlin logging tree.
- Do not add methods that expose the spend key or raw key material to Kotlin unless the
  existing wallet2 API and the Kotlin flow already require it and it is documented.
- Preserve the wallet2 status/error contract. If a native call fails, return a value
  that Kotlin will treat as failure; do not fabricate success.
- Keep `setTrustedDaemon`, `setProxy`, and light-wallet calls faithful to wallet2. Do
  not “fix” trust by hardcoding a safe-looking value on the native side.
- Do not weaken validation to make a test or UI path easier.

## 6. Build integration

- `app/CMakeLists.txt` links prebuilt static archives from
  `external-libs/<abi>/`. The app build does not build Monero.
- Monero static archives are linked in a `--start-group`/`--end-group` block because of
  cyclic references. Do not reorder or remove archives without a link review.
- `--exclude-libs,ALL` hides static-library symbols; keep it.
- The desktop path adds `-Wl,-z,noexecstack` for the x86-64 `wallet-crypto` archive.
  Do not apply Android-specific flags to desktop or vice versa without review.
- ABIs are `arm64-v8a` and `armeabi-v7a`. Do not add `x86_64` to the app without a
  corresponding `external-libs` target and CI artifact.
- Do not run CMake or the native build locally. The CI `native` job builds the
  archives; the Android build consumes them.

## 7. Procedure for a new native method

1. Confirm the operation cannot be done with an existing Kotlin/wallet2 method.
2. Add the Kotlin `external` declaration with an explicit, stable name.
3. Add the matching `Java_io_anonero_...` export with the correct `JNIEnv*`/`jobject`
   parameters.
4. Add a Kotlin wrapper that validates inputs, checks null/status, and maps failures to
   a safe result. Do not let callers see raw handles.
5. Update `scripts/check-jni-contract.py` only if a new intentional orphan is created.
6. If a Kotlin model constructor is involved, update both the constructor and the
   `GetMethodID` signature.
7. Add or update a JVM test only for pure Kotlin logic; native behavior needs CI and a
   device/instrumented test.
8. Require the CI `native` job and the `jni-contract` job before claiming success.

## 8. Static checks

Allowed:

```sh
python3 scripts/check-jni-contract.py
grep -n "GetStringUTFChars" app/src/main/cpp/anonero.cpp
grep -n "NewGlobalRef\|DeleteGlobalRef" app/src/main/cpp/anonero.cpp
grep -n "reinterpret_cast<jlong>" app/src/main/cpp/anonero.cpp
bash -n external-libs/build.sh
```

Forbidden: `cmake`, `make`, `./gradlew`, `docker`, emulator, `adb`.

## 9. Checklist

- [ ] No new C++ file without need; no alternate native runtime or FFI layer.
- [ ] Kotlin declaration and JNI export names match.
- [ ] Constructor signature and field order match the Kotlin model.
- [ ] All JNI strings/arrays/local refs are checked and released.
- [ ] No C++ exception crosses the boundary.
- [ ] Handles have one owner and a defined lifetime.
- [ ] No secret or credential logging.
- [ ] Failure returns are fail-closed and documented.
- [ ] CI `jni-contract` and `native` jobs are the evidence.
