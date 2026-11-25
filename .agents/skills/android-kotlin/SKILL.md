---
name: android-kotlin
description: "Use when changing ANONERO's Android/Kotlin application layer: ViewModels, coroutines and Flow, Koin wiring, lifecycle, services, DataStore or SharedPreferences, or wallet-state plumbing. Not for Compose-only UI work or native JNI changes."
---

# Android and Kotlin application layer

## When to use

- Adding or changing a ViewModel, screen state holder, service, repository, or Koin
  binding.
- Changing coroutine/Flow behavior, background sync, wallet lifecycle, or persistence.
- Reviewing Kotlin code for threading, lifecycle, state-ownership, or profile-isolation
  defects.

Do not use for pure Compose layout changes (use `jetpack-compose`) or C++/JNI changes
(use `native-jni`).

## Read first

- `AGENTS.md`
- `app/AGENTS.md`
- `app/src/main/java/io/anonero/di/AppModule.kt`
- `app/src/main/java/io/anonero/AnonApplication.kt`
- `app/src/main/java/io/anonero/services/WalletState.kt`
- `app/src/main/java/io/anonero/services/AnonWalletHandler.kt`
- `app/src/main/java/io/anonero/services/MoneroHandlerThread.kt`
- `app/src/main/java/io/anonero/model/WalletManager.kt`
- `app/src/main/java/io/anonero/util/Prefs.kt`
- `app/src/main/java/io/anonero/util/WalletProfiles.kt`

## Invariants

- `WalletState` is the single UI-facing source of truth for balances, history,
  accounts, subaddresses, coins, sync progress, connection status, and offline state.
- `WalletManager` manages exactly one live wallet. Unmanaged wallets are only for
  copies (backup re-keying) and decoy provisioning, and must be closed.
- Every wallet2 call is off the main thread. This includes `init`, `refresh`,
  `store`, `setPassword`, `setProxy`, `setDaemon`, `lightWalletLogin`, balances,
  history, coins, and transaction construction.
- JNI calls `MoneroHandlerThread` synchronously on the calling wallet2 thread. Its
  legacy Kotlin `Thread` wrapper has an empty `run()`; its stack hint does not apply
  to native callbacks. Keep callback work bounded and publish through `WalletState`.
- Do not introduce `GlobalScope`, `runBlocking`, `Thread.sleep`, or a second wallet
  state cache.
- Catch `CancellationException` and rethrow it before generic `Exception` handling.
- When correctness depends on a successful disk write, use `SharedPreferences.commitEdit`
  and handle its Boolean result. Android can update in-memory preferences before reporting
  disk failure. The AndroidX Unit-returning edit extension cannot report that failure.
  Commit the same editor that received the mutations, including when a wrapper delegates
  fluent setter calls to another editor.
- Never log seeds, passphrases, PINs, wallet passwords, Keystore material, or node
  credentials. `Timber` output is persisted.
- No analytics, telemetry, crash reporting, remote config, tracking IDs, or new
  persistent identifiers. Review every new preference key, log line, and network value
  for linkability and metadata exposure.
- Main/decoy profiles must never read, copy, or wipe each other implicitly.

## Procedure

1. **Identify the owner.** Decide whether the change belongs in `WalletState`, a
   screen ViewModel, `AnonWalletHandler`, `TorService`, or a repository. Do not add a
   parallel state holder.
2. **Trace the data flow.** Kotlin UI -> ViewModel -> `WalletState` -> `Wallet` ->
   JNI. Note whether the path touches the wallet thread, disk, Tor, or a profile.
3. **Add the smallest API.** Prefer a `StateFlow`/`SharedFlow` in `WalletState` or a
   `LiveData`/`StateFlow` in the ViewModel. Keep wallet mutation methods `@Synchronized`
   or serialized where they change account/profile/wallet identity.
4. **Wire Koin.** Add the dependency in `AppModule.kt`, using an existing qualifier for
   `WALLET_PREFERENCES`. Do not create a second `SharedPreferences` instance with a
   different name.
5. **Handle failure.** Return a typed failure or a safe default that cannot be mistaken
   for success. Do not fall through to a direct connection or an unverified state.
6. **Persist deliberately.** Add keys to `util/Prefs.kt`, decide profile-awareness,
   default value, and wipe behavior. If the value is security-relevant, use `commit`.
7. **Test only in CI.** Add a JVM unit test if the logic is pure Kotlin (formatting,
   parsing, migration). Do not run it locally. Instrumented/device behavior needs CI.
8. **Static review.** See checks below; then state what remains unverified.

## Static checks

```sh
grep -rn "GlobalScope\|runBlocking\|Thread.sleep" app/src/main/java
grep -rn "android.util.Log" app/src/main/java
grep -rn "WalletManager.instance" app/src/main/java/io/anonero/ui
grep -rn "!!" app/src/main/java/io/anonero/services app/src/main/java/io/anonero/util
grep -rn "putString\|putBoolean\|putLong\|putInt" app/src/main/java/io/anonero | grep -v Prefs.kt
python3 scripts/check-i18n.py
python3 scripts/check-jni-contract.py
```

Review every new `catch` for swallowed cancellation or swallowed wallet failure.
Review every new preference key for profile isolation and wipe coverage.

## Completion criteria

- [ ] State owner identified; no duplicate source of truth.
- [ ] Wallet calls are off the main thread.
- [ ] Cancellation is preserved.
- [ ] Security-relevant writes are durable and redacted.
- [ ] Main/decoy isolation is unchanged or explicitly extended.
- [ ] New persisted keys have defaults, profile behavior, and wipe coverage.
- [ ] Static checks pass.
- [ ] Runtime claims are marked unverified until CI.


For auxiliary wallets use retained ownership and an atomic closed-path deletion action.
Keep resource ownership after false/throwing close; do not merely log and forget it. Account
and output mutation methods reject locked sessions, preserve cancellation and surface failure.
Use immutable snapshots across IO dispatch, bind asynchronous validation to checked inputs,
and discard results after wallet/input changes. `scripts/check-kotlin-cancellation.py`
guards unqualified catch imports; it is not Kotlin compilation.

Serialize MAIN onboarding and backup restore from the empty-target check through
wallet/credential setup. When nested, take the MAIN provisioning mutex before the
backup mutex. Do not treat a temporarily hidden closing wallet as an empty slot.

Bind send and startup inputs to the captured wallet/profile before suspending. Recheck eligibility before using snapshots for a native operation. Parse saved node configuration using the build network without initializing JNI.

Sensitive UI work must authenticate against an explicit profile and call only the captured
wallet after checking it still owns that profile's path. Recheck before publishing a
secret result or starting destructive work. Scope secure wipe to the authenticated
path and stop shared cleanup when a new or closing managed wallet has reserved the slot.
Snapshot guards complement native lifetime checks. They do not provide JNI quiescence.

## Maintenance & Continuous Learning

When this subsystem changes, reconcile this procedure with the implementation and
record reusable constraints, root causes and remaining verification limits. Check the
closest AGENTS.md and the skills index for stale guidance. Run the read-only
`python3 scripts/validate-agent-knowledge.py --scope worktree` before review; inspect
AK004 warnings for relevance and run regression tests only in authorized CI.
