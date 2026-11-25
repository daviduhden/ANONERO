# Seed and private-key JNI failure paths

The seed accessors built native strings and called wallet2 outside a C++ exception
boundary. The same pattern was present in the legacy seed and capability lookups
and in the private view and spend key exports. String allocation or a thrown
wallet2 failure could leave the JNI frame by native unwinding.

The protected assets are the process, recovery flows, seeds, passphrases and private
keys. A caller can pass a nullable seed offset, request a seed from an unavailable
wallet, or run under memory pressure. Failures may contain sensitive upstream text
that could link the wallet or its owner if copied into diagnostics. The existing
reviewed seed-display and backup callers cross from Kotlin into the adapter, which
borrows the already-owned wallet handle and temporary JNI strings. The change adds
no new key reads, recipients, persistent data, or network path.

Allocate and call wallet2 within the catch boundary. Translate exceptions to a
fixed Java failure and preserve an existing JVM exception. A native exception
during Polyseed lookup must abort the call. The legacy mnemonic remains available
when wallet2 completes normally and reports that Polyseed is unavailable. Export
names, arguments, successful values and backup/mnemonic formats remain compatible.
The failure conversion does not validate handle lifetimes or erase existing secret
copies in wallet2, JNI or Kotlin memory. Onboarding and seed settings handle the
fixed failure locally and display a localized wallet operation error. Backup and
decoy creation retain their existing failure paths.

The native diagnostic source check guards the operation placement and failure
conversion. CI must still build and link both ABIs. CheckJNI and failure injection
on a device are needed to exercise native allocation/call failures and pending JVM
exceptions, including verification that an exception cannot trigger mnemonic
fallback. Existing successful Polyseed, legacy seed and key display/backup flows
also need device coverage. There is no data migration to reverse. Roll back the
adapter and static guard together and treat the original failure paths as open.
