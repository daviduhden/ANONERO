# JNI transaction review snapshot

- Identifier: JNI-PREVENTIVE-REVIEW-01. The latest [Tests run](https://github.com/daviduhden/ANONERO/actions/runs/45679643013) at `2ca0edb` passed the Android API 35 instrumentation, native archives, and debug APK link. No native exception escape was reported. This is a static finding in `app/src/main/cpp/anonero.cpp` and the transaction review view model.
- Protected assets are the authenticity of the reviewed transaction and the process holding wallet credentials. An untrusted unsigned transaction set crosses the import boundary into wallet2. Wallet2's summary and transaction accessors return through JNI. Allocating strings and vectors or calling the corresponding wallet2 methods may throw. Exceptions escaping JNI could terminate the app, and an empty confirmation sentence could leave a signable review with no authoritative details.
- Failure must raise fixed Java text without including a summary, address, amount, identifier, or native exception text. Reject empty summaries before publishing ownership or enabling signing, preserve cancellation and the nonempty raw-summary fallback, and never substitute advisory metadata. The patch adds no network request, identifier, persistent state, format change, or export signature. Imported transaction validation still depends on the pinned wallet2 implementation.
- Regression evidence requires source guards for the allocating review exports plus native link and API 35 instrumentation in both flavors. Exercise a missing summary, malformed import, and the successful raw-summary fallback on the reviewed object. Static checks cannot prove allocator-failure behavior, thread lifetime, or fund safety.
- Roll back the exception/caller changes together if needed. Do not enable signing from advisory fields or bypass summary validation. No persisted data migration is required.

## Native and runner warnings in the successful baseline

The native archives job records the NDK r30 API 29 wrappers for the two ARM
targets. The debug assemble job configured both ABI targets and checked that
the APKs contained `libanonero.so`. The available assemble and instrumentation
logs show no warning from a project-owned C++ source and show no sanitizer or
CheckJNI result. The emulator's protected-range unmap warning appears during
emulator startup, before the app tests. The SDK Manager CLI and Node Buffer
deprecation messages are runner/tooling diagnostics. See the
[baseline jobs](https://github.com/daviduhden/ANONERO/actions/runs/45679643013).

In the same baseline's ARM32 native compilation, Clang reported
`-Wshift-count-overflow` from a pinned bc-ur header when instantiating
`encodeTagAndValue` with `unsigned int` for a byte-string length.
The [pinned header](https://github.com/MrCyjaneK/bc-ur/blob/d82e7c753e710b8000706dc3383b498438795208/src/cbor-lite.hpp)
and the checked [upstream header](https://github.com/BlockchainCommons/bc-ur/blob/master/src/cbor-lite.hpp)
have the same implementation. The instantiated 32-bit `length(t)` returns
at most four bytes, so source inspection makes the warned eight-byte branch
unreachable for that instantiation. The JNI adapter exposes no call to the
wallet2 native UR encode APIs, and Android uses its own Kotlin UR path.
A proposed upstream fix is to widen the value in the eight-byte branch before
shifting, retaining byte-exact CBOR output. Validate any backport against
both ARM jobs and encoded-byte fixtures before updating the pin or patches.

The descriptor audit covers the four callbacks actually delivered to Kotlin.
Both the method names and descriptor argument types are derived from the
source and compared to native `GetMethodID` declarations. CI mutations cover
a changed descriptor and boxed primitive. Remaining export bytecode and
callback execution require the real link and device checks.

## JNI-PREVENTIVE-STATUS-02 — wallet status, credentials and persistence

Static inspection at `bf1cf25` found unguarded wallet2 status reads, password
verification/device queries, re-key and store calls. The combined wallet status
copies a native string. Password and path conversions also allocate. A native
exception during any of these calls could terminate a process holding an open
wallet, including during a migration. This finding has no observed CI crash.
Callers include transaction review, wallet refresh, backup and PIN migration.
The assets are the process, wallet files and credentials. Input comes from local
wallet files and the active session, and allocation can fail under memory
pressure. Preserve the existing failure contracts and use fixed error text
when native status itself is unavailable. The change creates no new external
recipient, record, identifier, JNI signature or persisted format.

The manual JNI diagnostics job checks the native exception boundaries. Both
ARM builds and device tests with injected wallet2/allocation failures are
required for runtime evidence. A false re-key or store result cannot establish
that the files on disk are untouched. Keep the caller's rollback logic and
snapshots when revisiting these exports. Rollback of the adapter and its source
guards requires no data migration.

## JNI-PREVENTIVE-TX-METADATA-01

Static inspection of `Wallet_setUserNote`, `Wallet_getUserNote` and
`Wallet_getTxKey` found wallet2 calls and implicit string allocations outside
a C++ exception boundary. A failure could terminate the wallet process while
loading history, opening transaction details or saving a note. No CI run or
crash log established that this had happened. The adapter now returns false for
a failed write and null for unavailable data. Wallet2 can still return a valid
empty string for a successful read. The Kotlin callers accept nullable strings,
and a failed write short-circuits the following store. Nothing is logged or
sent externally. Message signing and verification already catch native
exceptions around byte conversion and the wallet2 call.

The manual native diagnostics gate checks all three calls. Failure injection
on both ARM targets is outstanding. These guards do not serialize wallet close
with a concurrent reader. Rolling back the adapter and its static guards
requires no wallet file or ABI migration.
