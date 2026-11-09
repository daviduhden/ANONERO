# Development guide

This guide is for contributors changing the project. It assumes the repository-level
`AGENTS.md` and the relevant skill under `.agents/skills/` have been read. The instructions
there are part of the project contract.

## 1. Repository layout

| Path | Contents |
| --- | --- |
| `app/` | Android application module |
| `app/src/main/java/io/anonero/` | Kotlin application, services, models, UI, utilities |
| `app/src/main/cpp/` | C++ JNI adapter |
| `app/src/main/res/` | Strings, themes, drawables, XML rules |
| `app/src/test/` | JVM unit tests (CI only for agent work) |
| `app/src/androidTest/` | Instrumented tests (CI only) |
| `external-libs/` | Pinned Monero and native dependency build |
| `scripts/` | Static JNI and localization checks |
| `.github/workflows/` | Test and release pipelines |
| `docs/` | Architecture, security, features, build, state, and dependency documentation |
| `.agents/` | Agent skills and index |

The Android module is the only application module. The native engine is the C++/JNI
adapter plus the prebuilt Monero archives.

## 2. Requirements and build policy

- Android 10 (API 29) is the supported floor; compile/target are independent.
- JDK 17 is the Gradle build JDK documented by AGP 9.5.
- Gradle is provided by the wrapper. Do not add a second wrapper.
- Native archives are produced by `external-libs/`; the app compiles only its JNI adapter.
- **Agent policy:** do not compile, run Gradle, run tests, start emulators, or execute
  the app locally. Use static analysis and CI.
- **Human contributors:** the README build path is available, but CI is authoritative for
  merges and releases. The native path requires the pinned Monero submodule and patch series.

## 3. Code conventions

### Kotlin

- Official Kotlin style; four-space indentation; explicit imports.
- Keep wallet2 calls off the main thread. Use `viewModelScope`, service scopes, or
  `Dispatchers.IO` as the surrounding code does.
- Preserve `CancellationException`; do not swallow it in a generic `catch`.
- Use `Timber` for logging. Do not add `android.util.Log` outside the existing `AnonLogTree`
  recursion fallback and Logs-screen severity constants.
- Do not log seeds, passphrases, PINs, wallet passwords, Keystore material, node
  credentials, or proxy credentials.
- Use string resources for user-visible text. Add every new string to all supported locales
  and preserve format placeholders.
- Comments explain *why*, contracts, invariants, or non-obvious failure behavior. Do not
  restate a line of code.
- Add KDoc to public/non-obvious APIs, especially state owners, services, native facades,
  migrations, and security-sensitive methods.

### Compose

- Hoist state out of composables; collect it from the ViewModel/`WalletState`.
- Keep composables free of wallet, Tor, and cryptographic logic.
- Use the existing routes, theme tokens, and reusable components.
- Keep `FLAG_SECURE` and the lock overlay intact.
- Do not put real wallet data in previews.

### C++/JNI

- Follow `app/src/main/cpp/AGENTS.md`.
- Use the existing scoped helpers; check and release every JNI resource.
- Never let a C++ exception cross the boundary.
- Keep Kotlin `external` declarations, JNI exports, and constructor signatures in sync.
- C++/JNI is the permanent native boundary; do not add another native runtime or FFI layer.

### Shell, Gradle, workflows

- Keep pins and checksums together.
- Do not add an unverified download or a mutable action tag.
- Keep workflow permissions minimal and release signing inside the protected environment.
- Do not add a cache to the publishing job.

## 4. State, services, and threading

- `WalletState` is the UI-facing source of truth. Add flows there or in a screen ViewModel;
  do not create a parallel wallet cache.
- `WalletManager` owns one managed wallet. Use unmanaged wallets only for the decoy and
  backup copy, and close them.
- `AnonWalletHandler` owns open/close, profile resolution, proxy, daemon, LWS, and wipe.
- `MoneroHandlerThread` owns wallet callbacks and refresh decisions.
- `AnonNeroService` keeps sync alive and publishes notifications; it does not own the wallet.
- `TorService` owns the embedded daemon and publishes the SOCKS endpoint.
- Security-sensitive operations must be serialized or guarded; a failed close/switch must
  not leave two profiles referenced at once.

## 5. Persistence rules

- Use `AnonConfig.PREFS` (`anonPref`) for the wallet preferences file. The Koin qualifier is
  `WALLET_PREFERENCES`; the string `walletPref` is the qualifier, not the file name.
- Use `commit = true` for security-relevant writes that must be durable before the next step.
- Put profile-dependent values behind `WalletProfileStore`.
- Add new cache files to the startup/wipe cleanup paths.
- Never write wallet files, keys, seeds, or decrypted backups outside app-private storage.
- Preserve v1/v2/v3 backup compatibility; do not remove a reader without a deprecation
  decision and fixtures.
- Do not change Keystore aliases, prefixes, PIN format, or profile file names casually.

## 6. Common change recipes

### Add a screen

1. Add a serializable route in `ui/home/graph/routes/` or the onboarding graph.
2. Register it in `HomeGraph.kt`/`Home.kt` or `OnboardingGraph.kt`.
3. Add a ViewModel if the screen has state; register it in `di/AppModule.kt`.
4. Add strings to base, `es-rES`, `ru-rRU`, and `b+zh+Hans`.
5. Run `python3 scripts/check-i18n.py`.
6. Check lock/stealth/back behavior.

### Add a preference

1. Add a named constant in `util/Prefs.kt`.
2. Decide whether it is profile-dependent; if so, add a key mapping in
   `WalletProfileStore`.
3. Give it a default and a migration note.
4. Add it to the correct wipe/remove path.
5. Use `commit = true` if it is security-relevant.

### Add a native method

1. Follow `app/src/main/cpp/AGENTS.md` and the `native-jni` skill.
2. Add the Kotlin `external` declaration and a safe wrapper.
3. Add the `Java_io_anonero_*` export with the exact signature.
4. Update constructor signatures if a model is involved.
5. Run `python3 scripts/check-jni-contract.py`.
6. Require CI `native` and, for behavior, instrumented tests.

### Add a dependency

1. Add it to `gradle/libs.versions.toml`.
2. Review license, maintenance, transitive dependencies, and Android compatibility.
3. Do not add a direct dependency in `app/build.gradle.kts` without a catalog entry.
4. Record the reason and pending validation in `docs/architecture/dependencies.md`.
5. Do not add another native runtime or FFI layer; native code stays C++/JNI.

### Change a persisted format

Use the `compatibility-migrations` skill. Freeze a fixture, add the read path first, make the
migration idempotent and rollback-safe, and keep the old restore path until a deprecation
decision exists.

## 7. Static validation

Allowed for agent work:

```sh
python3 scripts/check-jni-contract.py
python3 scripts/check-i18n.py
bash -n external-libs/build.sh external-libs/patches/apply.sh
git diff --check
grep/rg, file inspection, XML/TOML parsing
```

Forbidden for agent work: `./gradlew`, `make`, `cmake`, `docker`/`podman`, `adb`, emulators,
or executing the app.

`check-i18n.py` also rejects unescaped Android string apostrophes and invalid string escape
sequences; a passing XML parse alone is not sufficient.

CI jobs (manual dispatch only; every job other than `authorize` needs the owner gate):

- `authorize`: owner gate.
- `unit`: JVM unit tests for both flavors.
- `lint`: Android Lint for both flavors with HTML/XML reports.
- `assemble`: links `libanonero.so` and checks the API 29 floor.
- `jni-contract`: symbol parity.
- `i18n`: resource parity and hardcoded text scan.
- `native-language-policy`: rejects project-owned Java sources.
- `native`: builds Monero/JNI archives (blocked by missing patches in this checkout).
- `instrumented`: API 35 emulator tests.
- `release`: test, build, sign, verify, publish; `publish` is a separate boolean input.

## 8. Testing

- Pure Kotlin logic (formatting, parsing, migrations, archive rules) belongs in
  `app/src/test`. `SigningSummaryTest` covers the wallet2 review-summary parser.
- Native linkage and behavior need the `native` job and a device/instrumented test.
- UI rendering, permission flows, Tor transitions, LWS reconnect, lock screen, and decoy
  switching need device validation.
- Do not run tests locally as an agent; add the test and let CI run it.

## 9. Git and documentation

- Use the configured Git identity.
- Never add the agent as author/co-author.
- Do not commit without explicit supervisor authorization; do not push without separate
  authorization.
- Keep commits small and grouped by component.
- Update the affected docs and `AGENTS.md`/skill when behavior, architecture, formats, or
  security boundaries change.
- Remove stale documentation rather than leaving contradictory instructions.

## 10. Current tooling oddities

- `ui/NavGraph.kt` has no executable code; navigation is implemented in `HomeGraph.kt` and
  `OnboardingGraph.kt`.
- The Monero submodule and patch series are not present in the checkout.
- The Gradle wrapper JAR is checked in at `gradle/wrapper/gradle-wrapper.jar`; the
  distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`.
- No Gradle dependency lockfile or verification metadata is checked in.
- `kotlinx.coroutines` is a direct declaration at 1.11.0, even though earlier versions
  resolved it transitively.
