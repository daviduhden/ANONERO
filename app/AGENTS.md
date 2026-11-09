# Android application — Agent Instructions

These instructions apply to `app/`. The root `AGENTS.md` remains authoritative for
security, language, build, and Git policy. Read it first.

## 1. Module facts

- Single module: `:app`. Namespace and application ID root: `io.anonero`.
- `minSdk = 29`, `compileSdk = 37` + `compileSdkMinor = 2` (API 37.2),
  `targetSdk = 37`.
- Flavors: `anon` (full wallet) and `nero` (view-only, `BuildConfig.VIEW_ONLY = true`).
- Build types: `debug`, `release`, `stageNet` (debuggable, stagenet suffix).
- ABIs: `arm64-v8a` and `armeabi-v7a`. Do not add `x86_64`.
- The JNI adapter is built by `app/CMakeLists.txt`; Monero static archives come from
  `external-libs/<abi>/`.
- Current JNI toolchain pins: Android SDK CMake 4.1.2 and NDK 30.0.16248370. The
  external archives are built with CMake 4.4.5 and NDK r30; keep them coordinated.
- Dependencies are declared in `gradle/libs.versions.toml`. Do not add a dependency
  directly in `app/build.gradle.kts` without a catalog entry.
- Release builds are unsigned by design; signing happens only in the protected
  `.github/workflows/release.yml` environment. Never configure a debug signing key for
  release.

## 2. Source layout

| Path | Responsibility |
| --- | --- |
| `src/main/java/io/anonero/AnonApplication.kt` | Process initialization, Koin, logging, crash handler |
| `src/main/java/io/anonero/AnonConfig.kt` | App context, paths, network, file names |
| `src/main/java/io/anonero/di/AppModule.kt` | Koin graph |
| `src/main/java/io/anonero/model/` | Wallet2 models, `external` methods, wallet handles |
| `src/main/java/io/anonero/services/` | Tor, wallet lifecycle, sync, state, foreground service |
| `src/main/java/io/anonero/store/` | DataStore repositories (logs, nodes) |
| `src/main/java/io/anonero/ui/` | Compose UI, navigation, theme, components |
| `src/main/java/io/anonero/util/` | Security, backup, formatting, profile, locale utilities |
| `src/main/cpp/` | C++ JNI adapter (see its own `AGENTS.md`) |
| `src/main/res/` | Strings, themes, drawables, file-provider paths |
| `src/test/` | JVM unit tests, executed only in CI |
| `src/androidTest/` | Instrumented tests, executed only in CI |

## 3. State and concurrency rules

- `WalletState` is the single UI-facing state owner. Add flows there or in a
  screen-scoped ViewModel; do not duplicate wallet balances, history, or sync state.
- `WalletManager` owns one managed wallet. Use `openWalletUnmanaged` /
  `createWalletUnmanaged` only for explicit copy/decoy work, and always close them.
- Never call wallet2 from the main thread. Balance, history, coins, store, refresh,
  init, proxy, login, and transaction methods block on native I/O.
- `MoneroHandlerThread` callbacks run on a native wallet thread with a 5 MB stack.
  Keep callbacks short; do not perform UI work or blocking disk I/O there.
- Use structured concurrency. Do not use `GlobalScope`, `runBlocking`, or
  `Thread.sleep` in application code.
- Catch `CancellationException` and rethrow it before generic exception handling.
- Do not hold `Activity`, `Fragment`, `View`, or composable state in long-lived
  singletons. `AnonConfig.context` is the application context only.
- Security-sensitive state changes (wallet open/close, profile switch, proxy change,
  lock/unlock, wipe) must be serialized or guarded; concurrent calls can leave the
  process with keys or a proxy from the wrong profile.

## 4. Persistence

- The `SharedPreferences` file is `anonPref` (`AnonConfig.PREFS`), injected under the
  Koin qualifier `WALLET_PREFERENCES`. It holds wallet flags, hashes, proxy/LWS settings,
  restore heights, and profile-specific keys.
- Use `commit = true` for security-relevant writes that must be durable before the
  next step (PIN hash, Keystore migration, profile removal, LWS config).
- Node and log lists use DataStore (`store/NodeDataStore.kt`, `store/LogsDataStore.kt`).
- Cache files are ephemeral. Any new airgapped export/import file must be covered by
  `AnonConfig.clearSpendCacheFiles` and the wipe path.
- Never write wallet files, keys, seeds, or decrypted backups outside app-private
  storage. The `FileProvider` exposes only `cache/logs`, `cache/backup`, and
  `cache/exports`.
- Adding a persisted key requires:
  - a constant in `util/Prefs.kt` or `model/node/Node.kt`;
  - profile-awareness if it can differ between main and decoy;
  - a clear/default value and a migration note;
  - inclusion in the correct wipe/remove path.

## 5. Security in the Android layer

- Keep `FLAG_SECURE` set on the main window. Do not remove it for screenshots,
  debugging, or “sharing”.
- Keep `android:allowBackup="false"` and both backup-exclusion XML files intact.
- Keep the foreground service `exported="false"` and `stopWithTask="true"`.
- Do not add exported components, deep links, implicit intents carrying wallet data,
  or new permissions without a review.
- Do not add analytics, telemetry, crash reporting, or remote config.
- Notification content must go through `StealthMode.notificationBody/title` where
  applicable. Do not include balances, heights, addresses, or transaction counts in
  stealth mode.
- Logs are persisted and user-shareable. Use `Timber`, never `android.util.Log`, except
  for the existing `AnonLogTree` recursion fallback and the `Log` severity constants
  used by the Logs screen. Redact credentials and sensitive wallet data.
  `AnonLogTree` must not log in stealth mode.
- The lock screen must remain on top until background sync has actually exited. Do
  not hide it on a PIN match alone.
- On unlock, clear the previous session's logs (`AppViewModel.openWallet`) and cancel
  stale notifications.
- Profile switching must close the previous wallet and reset `WalletSession` before
  opening another. If close fails, refuse the switch.

## 6. UI and localization

- All user-visible text belongs in `res/values/strings.xml` (or `strings_i18n.xml`)
  with translations in `es-rES`, `ru-rRU`, and `b+zh+Hans`.
- Run the static check `python3 scripts/check-i18n.py` before handing off. It verifies
  key parity, plural quantities, placeholder parity, unresolved `R.string` references,
  and hardcoded Compose text.
- Use `stringResource` / `pluralStringResource`; do not concatenate translated
  sentences. Use placeholders.
- Keep accessibility labels for actionable icons. Decorative images may use `null`,
  but document why.
- Prefer existing theme tokens and components over new hardcoded colors or one-off
  widgets.
- UI must not expose secrets in previews, screenshot tests, or error toasts.

## 7. Error handling

- Convert native/wallet2 failures into stable, localized UI errors. Do not show raw
  wallet2 error strings unless they are known to be safe and non-secret.
- Never silently proceed after a failed `setProxy`, `init`, `lightWalletLogin`,
  `store`, `setPassword`, or `commit`.
- A failed security migration must roll back or leave the previous credential usable.
- Preserve the existing meaning of `Boolean`/sentinel native returns; if a new native
  call can fail in more than one way, prefer a typed result or document every value.

## 8. Validation

- Do not run Gradle, unit tests, instrumented tests, or the emulator locally.
- Allowed: reading, `grep`/`rg`, `git diff`, XML/TOML parsing, `python3
  scripts/check-i18n.py`, `python3 scripts/check-jni-contract.py`, `bash -n`.
- CI is authoritative. Relevant jobs: `unit`, `jni-contract`, `i18n`,
  `native-language-policy`, `native`, `instrumented`.
- For UI-only changes, state that runtime rendering and accessibility behavior remain
  unverified until CI/device testing.

## 9. Checklist

- [ ] No main-thread wallet calls.
- [ ] No new state owner duplicating `WalletState`.
- [ ] Both `anon` and `nero` flavors considered, including `VIEW_ONLY`.
- [ ] Main/decoy profile isolation preserved.
- [ ] No new plaintext secret, log, notification, or cache leak.
- [ ] Strings added to every supported locale.
- [ ] Persisted keys have defaults, profile behavior, and wipe coverage.
- [ ] No new dependency outside the version catalog.
- [ ] Static checks pass; runtime claims are marked unverified.
