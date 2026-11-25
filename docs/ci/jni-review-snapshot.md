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
