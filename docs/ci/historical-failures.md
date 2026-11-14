# Historical GitHub Actions failures

## Run inventory

| Run | Commit | Event | Result | Failed jobs | Notes |
| --- | --- | --- | --- | --- | --- |
| `43528256395` | `0cf9db0` | `workflow_dispatch` | success | none | All jobs pass; instrumented anon and nero each ran 1 test with 0 failures; boot 919 s; 62 JVM tests per flavor; lint 0/0; no adapter warnings under `-Werror` |
| `43521054641` | `feb985e` | `workflow_dispatch` | failure | instrumented | All code/native/assemble/lint/JNI gates pass (62 tests per flavor, lint 0/0, `-Werror` clean); boot took 630 s, then `adb`/input/overlay services broke before the test script; no reports |
| `43122308945` | `381d8ab` | `workflow_dispatch` | failure | instrumented | All code gates plus native and assemble pass; `system_server` SIGABRT while Gradle dexed the test APKs; both XML `tests=0`; health gate failed closed |
| `43108046671` | `02b97b8` | `workflow_dispatch` | failure | instrumented | Guest lost `system_server` during install/uninstall; both XML `tests=0`; all other jobs pass |
| `43058769285` | `76fcb79` | `workflow_dispatch` | failure | instrumented | Gradle ran both flavors successfully and XML validation passed; the inline retry loop failed because the emulator runner executes `script` line by line |
| `43045996049` | `452f4b6` | `workflow_dispatch` | failure | instrumented | 3 GB + 60 s settle: first flavor (anon) still failed to attach; XML validation correctly failed the job |
| `43034549647` | `9fa5c8e` | `workflow_dispatch` | failure | instrumented | All code gates, native, and assemble pass; the first instrumented flavor (anon) failed to attach, the second (nero) passed |
| `42874760135` | `c228e36` | `workflow_dispatch` | success | none | All jobs pass; instrumented XML validation confirms anon and nero each ran 1 test with 0 failures; lint 1 external Gradle advisory, suppressed afterwards |
| `42862824199` | `30075de` | `workflow_dispatch` | success (masked failure) | none | All jobs reported success, but the NERO XML contained `failures=1` with `failed to attach`; the runner returned 0. XML validation added afterwards |
| `42854503254` | `578bd38` | `workflow_dispatch` | failure | instrumented | Native and assemble pass; emulator boots after 16m then dies with `Broken pipe`/adb 224 (no KVM, software x86_64) |
| `42847493558` | `27c0987` | `workflow_dispatch` | failure | instrumented | Native and assemble pass; emulator boots; runner treated the `\` continuation as a Gradle task named `\` |
| `42841238523` | `1044ec8` | `workflow_dispatch` | failure | assemble | Native, tests, and lint pass; APK produced as `app-anon-debug.apk`; minSdk extraction returned empty |
| `42750259391` | `c059214` | `workflow_dispatch` | failure | assemble | Native passes; Gradle produces `app-<flavor>-debug.apk` but the workflow still expected `<flavor>-debug.apk` |
| `42743643959` | `a80dbe7` | `workflow_dispatch` | failure | assemble | Native archives built successfully; the adapter failed to compile because `Get*ArrayRegion` returns void in C++ JNI |
| `42741706801` | `de7fbb6` | `workflow_dispatch` | failure | native | JVM tests and lint pass; native compiled for 15m and failed in `wallet_rpc_server.cpp` after the coin-control signature change |
| `42740103758` | `f99ade3` | `workflow_dispatch` | failure | JVM unit tests (SDK download); native (translations tool) | Patches applied; Monero tried to run an ARM64 translations binary on the host; Android SDK download timed out |
| `42708047372` | `7fe1daa` | `workflow_dispatch` | failure | native | Patch preflight and all five patches pass; Debian snapshot 503 persisted through five apt retries |
| `42707311457` | `ba3b3f6` | `workflow_dispatch` | failure | native | Patch series applied cleanly; Debian snapshot 503 blocked the container base stage |
| `42700250719` | `e0afc7f` | `workflow_dispatch` | failure | native | JVM tests, lint (0/0), JNI, i18n, policy pass; zero Kotlin warnings; native failed before the series was restored |
| `42699227920` | `0006659` | `workflow_dispatch` | failure | native | Lint 0 errors / 0 warnings; native blocked |
| `42698560608` | `2788e92` | `workflow_dispatch` | failure | native | Lint had 2 warnings left; fixed afterwards |
| `42697719108` | `dda6cde` | `workflow_dispatch` | failure | native | Lint clean except icon/vector warnings; fixed afterwards |
| `42695062606` | `746ecf4` | `workflow_dispatch` | failure | native | Lint errors cleared; structural warnings reduced |
| `42694063725` | `dd692a8` | `workflow_dispatch` | failure | native | JVM tests and lint pass; compiler fixes validated |
| `42693644972` | `47f4a5e` | `workflow_dispatch` | failure | JVM unit tests; Android Lint; native | Regression introduced by the first lint fix; corrected in `dd692a8` |
| `42692380022` | `672a1cc` | `workflow_dispatch` | failure | Android Lint; native | First lint run: 35 errors, 92 warnings |
| `42686398569` | `52d67d4` | `workflow_dispatch` | failure | JVM unit tests; native | Manual owner run; architecture policy, JNI contract, localization passed; instrumented skipped |
| `42552892586` | `ed8b097` | `push` | failure | JVM unit tests; native | Historical automatic run; instrumented skipped |
| `42547686992` | `c385630` | `push` | failure | JVM unit tests; native | Historical automatic run; instrumented skipped |

No release, sanitizer, or fuzz run exists. The native archives build, and instrumented
passed on `42874760135` and `43528256395` but is non-deterministic without KVM. The two
historical `push` triggers are no longer present; workflows are manual-only.

## Failure records

### CI-001 — Missing Gradle wrapper JAR

- **Runs:** `42552892586`, `42547686992`
- **Job:** JVM unit tests
- **Commit:** `ed8b097`, `c385630`
- **Diagnostic:** `Error: Unable to access jarfile .../gradle/wrapper/gradle-wrapper.jar`
- **Root cause:** the wrapper JAR was not tracked in Git; Gradle could not start.
- **Pattern search:** every job invoking `./gradlew` used the same wrapper. The JVM job
  failed before compilation; no tests could run.
- **Correction:** added the pinned Gradle 9.9.0 wrapper JAR and a preflight in every Gradle
  job. The wrapper JAR is now committed.
- **Regression check:** `test -s gradle/wrapper/gradle-wrapper.jar` before Gradle.
- **Status:** corrected; confirmed by the wrapper preflight in run `42686398569`.

### CI-002 — Missing Monero patch series / uninitialized local submodule

- **Runs:** all three accessible runs
- **Job:** Build native Monero/JNI archives
- **Diagnostic:** `Pinned ANONERO Monero patch series is missing from external-libs/patches/`;
  historical `error: can't open patch '.../external-libs/patches/*.patch'`.
- **Root cause:** the repository did not contain the `.patch` series. In CI the submodule
  checkout step succeeds; in the local audit checkout the submodule is uninitialized.
- **Pattern search:** the native build, assemble/link, instrumented tests, release build,
  signing, and publication all depend on these inputs.
- **Correction:** the seven wallet-API patches were reconstructed from the `monero_c`
  lineage, rebased onto the pinned `85f036ba` commit, and checked in with `apply.sh`,
  provenance, and licence. They apply with `git apply --index --3way`; the nested
  `bc-ur`, `polyseed`, and `utf8proc` gitlinks resolve.
- **Regression check:** native preflight requires an initialized submodule and at least one
  patch file; `assemble` now links the JNI library even when instrumented tests are skipped.
- **Status:** patch series restored; native archives and adapter link build in CI. The API
  35 smoke test passed on `42874760135` but is unstable on the KVM-less runner.

### CI-003 — Invalid resource string / apostrophe escaping

- **Run:** `42686398569`
- **Job:** JVM unit tests, both `anon` and `nero` variants
- **Diagnostic:** `Failed to flatten XML for resource 'securely_wipe_warning' with error:
  Invalid unicode escape sequence in string`; `string/securely_wipe_warning does not contain
  a valid string resource`.
- **Root cause:** the English resource contained an unescaped apostrophe in
  `wallet's` inside an Android string resource. AAPT rejected it. The reported
  `strings_i18n.xml` line was a merged-resource attribution; the source string is in
  `values/strings.xml`.
- **Pattern search:** scanned all `values*/` XML for unescaped apostrophes, invalid escape
  sequences, and malformed XML. No other unescaped apostrophes or invalid escapes were
  found. The Monero mnemonic dictionary is not an Android string resource and is untouched.
- **Correction:** changed the literal to `wallet\'s`.
- **Regression check:** `scripts/check-i18n.py` now rejects unescaped apostrophes and
  invalid backslash escapes in Android string resources (implemented; CI pending).
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-004 — Missing `CancellationException` import

- **Run:** `42686398569`
- **Job:** JVM unit tests, both flavors
- **Diagnostic:** `Unresolved reference 'CancellationException'` in
  `ReviewTransaction.kt:251`.
- **Root cause:** the catch clause was added without importing
  `kotlinx.coroutines.CancellationException`.
- **Pattern search:** all files using `CancellationException`, `NonCancellable`, `Job`,
  `withContext`, and `Dispatchers` were checked for the corresponding imports.
- **Correction:** added the import and retained the rethrow.
- **Regression check:** static import scan in `docs/ci/preventive-audit.md`; Kotlin CI.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-005 — Nullable manual proxy host

- **Run:** `42686398569`
- **Job:** JVM unit tests, both flavors
- **Diagnostic:** `Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable
  receiver of type 'String?'` and argument mismatch at `AnonWalletHandler.kt:364/368`.
- **Root cause:** `proxyHost` comes from `SharedPreferences.getString`, which is nullable;
  the manual-proxy branch called `isNotEmpty()` and passed it as a non-null `String`.
- **Pattern search:** all `proxyHost` uses and all preference reads were reviewed.
- **Correction:** use `isNullOrEmpty()` and bind a non-null `host` before constructing the
  endpoint.
- **Regression check:** Kotlin compilation in the JVM job.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-006 — Nullable compact number in a plural resource

- **Run:** `42686398569`
- **Job:** JVM unit tests, both flavors
- **Diagnostic:** `Argument type mismatch: actual type is 'String?', but 'Any' was expected`
  in `WalletProgressIndicator.kt:120`.
- **Root cause:** `Formats.convertNumber` returns `String?`; the plural formatter requires
  non-null arguments.
- **Pattern search:** checked both `convertNumber` call sites, including
  `AnonNeroService.showProgress`.
- **Correction:** fall back to the exact atomic value string when the compact formatter
  returns null.
- **Regression check:** Kotlin compilation in the JVM job.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-007 — Nullable transaction transfer list

- **Run:** `42686398569`
- **Job:** JVM unit tests, both flavors
- **Diagnostic:** `Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable
  receiver of type 'List<Transfer>?'` in `TransactionDetail.kt:129`.
- **Root cause:** `TransactionInfo.transfers` is nullable by contract and the detail screen
  called `forEach` directly.
- **Pattern search:** reviewed all `.transfers` consumers and the `TransactionInfo` contract.
- **Correction:** use `orEmpty()` before iterating.
- **Regression check:** Kotlin compilation and the existing transaction model tests.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-008 — Missing `SharedPreferences.edit` KTX import

- **Run:** `42686398569`
- **Job:** JVM unit tests, both flavors
- **Diagnostic:** `Named arguments are prohibited for non-Kotlin functions`, `No parameter
  with name 'commit'`, and unresolved `putString`/`putBoolean`/`putLong` in
  `DecoySettings.kt`.
- **Root cause:** `prefs.edit(commit = true) { ... }` is the AndroidX KTX extension, but the
  file imported only `SharedPreferences`; Kotlin resolved the platform `edit()` method.
- **Pattern search:** every file using `edit(commit = true)` now has the KTX import.
- **Correction:** import `androidx.core.content.edit`.
- **Regression check:** import scan and Kotlin CI.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-009 — ViewModel property resolution inside `launch`

- **Run:** `42686398569`
- **Job:** JVM unit tests, both flavors
- **Diagnostic:** `Unresolved reference 'unsignedTransaction'` and
  `'pendingTransaction'` on receiver `CoroutineScope` in `ReviewTransaction.kt`.
- **Root cause:** inside `viewModelScope.launch`, `this` is the coroutine scope, so
  `this.unsignedTransaction`/`this.pendingTransaction` did not resolve to the ViewModel.
- **Pattern search:** searched for `this.pendingTransaction` and `this.unsignedTransaction`
  in coroutine bodies.
- **Correction:** qualify the assignments with `this@ReviewTransactionViewModel`.
- **Regression check:** Kotlin compilation in the JVM job.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-010 — Stale test contradicts the restore-height cap

- **Runs:** none yet; predicted from source.
- **Job:** JVM unit tests, both flavors.
- **Evidence:** `SeedRestoreInputTest.kt` expected `Long.MAX_VALUE` to be accepted, while
  `SeedRestoreInput.isValidHeightInput`/`requireValid` cap values at
  `MAX_RESTORE_HEIGHT = 100_000_000L`.
- **Root cause:** the cap was introduced without updating the test. It was never observed
  because no run reached test execution.
- **Pattern search:** reviewed every test that exercises `RestoreTarget`,
  `SeedRestoreInput`, and restore-height parsing.
- **Correction:** the test now accepts `MAX_RESTORE_HEIGHT`, rejects `Long.MAX_VALUE`, and
  asserts `requireValid` throws above the cap.
- **Regression check:** the JVM test itself, once CI runs it.
- **Status:** validated in `42874760135`: compilation, JVM unit tests, and Android Lint pass.

### CI-011 — Coroutine cancellation swallowed by generic catches

- **Runs:** none yet; static finding while auditing CI-004.
- **Files:** `Send.kt` (prepare and compose paths), `PayToMany.kt` (prepare), and the
  post-broadcast refresh in `ReviewTransaction.kt`.
- **Root cause:** `catch (e: Exception)` also catches `CancellationException`, so a screen
  dismissal could publish a construction error or keep working after cancellation.
- **Pattern search:** every `catch (e: Exception)` in `app/src/main/java` was reviewed;
  these were the remaining suspend/`launch` paths without a cancellation rethrow.
- **Correction:** add `catch (cancelled: CancellationException) { throw cancelled }` before
  the generic catch, and reset the broadcast state to `STAGING` when cancellation happens
  before submission.
- **Regression check:** Kotlin compilation; coroutine cancellation behavior still needs a
  lifecycle test.
- **Status:** implemented; runtime unverified.

### CI-012 — `return <value>` inside a `void` JNI export

- **Runs:** none yet; static finding in the C++ audit.
- **File:** `anonero.cpp` `setDefaultMixin`.
- **Diagnostic:** `return wallet->setDefaultMixin(mixin);` in a function declared `void`.
  This is ill-formed C++ and would fail the native build once it reaches the adapter.
- **Root cause:** a copy of the old Monerujo body where the callee returned `bool`.
- **Pattern search:** scanned every `JNIEXPORT void` export for `return <expression>`.
- **Correction:** guard `mixin < 0` and the wallet handle, call the method, return.
- **Regression check:** native compilation with `-Wall -Wextra -Wpedantic`.
- **Status:** validated in `42874760135`: the native archives build and the adapter links. Runtime JNI behavior remains unverified.

### CI-013 — Handle and JNI-string paths missing fail-closed guards

- **Runs:** none yet; static finding in the C++/JNI audit.
- **Files:** `anonero.cpp` adapters for wallet, coins, and history.
- **Diagnostic:** `createSweepTransaction` lacked null/range/exception guards;
  `closeJ` did not invalidate the Kotlin handle; `setListenerJ` leaked the previous
  listener on the null-wallet path; `signAndExportJ` leaked the loaded unsigned
  transaction; 24 `GetStringUTFChars` sites did not check for allocation failure;
  `getElement` turned a failed array read into amount `0`.
- **Root cause:** hardening was applied to some transaction paths but not their siblings.
- **Pattern search:** every `getHandle`, `GetStringUTFChars`, `NewObject`, and
  `GetMethodID` site in the adapter; every Kotlin `external` that accepts `String?`.
- **Correction:** `ScopedUtfChars` with null checks, handle guards, listener/handle
  invalidation on close, `std::unique_ptr` for the unsigned transaction, rejection of
  zero/negative amounts in the array path, range checks before `jint → uint32_t`, and
  checked `GetMethodID`/`NewObject` in the object factories.
- **Regression check:** `scripts/check-jni-contract.py` (names only) plus the native build;
  signature/link validation still requires a real build.
- **Status:** validated in `42874760135`: the native archives build and the adapter links. Runtime JNI behavior remains unverified.

### CI-014 — Android Lint: 35 errors from Compose context rules

- **Run:** `42692380022`.
- **Jobs:** Android Lint (`anonDebug`, `neroDebug`).
- **Diagnostic:** `ContextCastToActivity` in `Settings.kt` and 34
  `LocalContextGetResourceValueCall` errors across 12 Compose files.
- **Root cause:** the new lint job surfaced pre-existing Compose rules: casting
  `LocalContext` to `Activity`, and reading resources through `LocalContext.current`
  instead of the configuration-aware composition locals.
- **Pattern search:** every `LocalContext.current as? Activity` and every
  `context.getString(...)` in the UI tree.
- **Correction:** use `LocalActivity.current` and `LocalResources.current`; also clean
  nullable-safe-call, indentation, Koin preview, KTX preference, haptic API, resource, and
  launcher-icon warnings.
- **Regression check:** the `lint` job is mandatory; final run `42700250719` reports zero
  errors and zero warnings for both flavors.
- **Status:** verified in CI.

### CI-015 — Kotlin compiler warnings in the first clean compile

- **Runs:** `42692380022`, `42694063725`.
- **Diagnostics:** elvis on a non-null `String`, unnecessary safe calls, redundant
  `toString`, deprecated `String.capitalize`, Koin `KoinApplication(application = ...)`,
  and deprecated framework overrides in `MainActivity`.
- **Root cause:** fixes in CI-003..CI-009 left redundant null handling, and modern Kotlin
  flags deprecated APIs that no earlier run had reached.
- **Pattern search:** every warning emitted by `compileAnonDebugKotlin` and
  `compileNeroDebugKotlin`, then a search for the same API usage.
- **Correction:** remove redundant checks, migrate the Koin preview to
  `koinConfiguration { }`, use `replaceFirstChar`, and suppress `DEPRECATION` plus
  `OVERRIDE_DEPRECATION` only on the three global framework overrides that cannot use the
  Activity Result API.
- **Regression check:** the `unit` job compiles both flavors; final run `42700250719`
  emits zero `w:` compiler diagnostics.
- **Status:** verified in CI.

### CI-016 — Lint fix briefly broke nullable sweep handling

- **Run:** `42693644972`.
- **Jobs:** JVM unit tests, Android Lint (compile phase).
- **Diagnostic:** `Only safe (?.) ... on a nullable receiver of type
  PendingTransaction?` in `Send.kt`.
- **Root cause:** removing "unnecessary" safe calls mechanically also removed them from
  the `createSweepTransaction` branch, which returns `PendingTransaction?`.
- **Pattern search:** both transaction-construction branches and the nullable return type.
- **Correction:** restore safe calls in the sweep branch only; the non-null
  `createTransaction` branch keeps direct calls. Add the missing `LocalActivity` import.
- **Regression check:** the next run (`42694063725`) compiled both flavors and passed JVM
  tests.
- **Status:** verified in CI.

### CI-017 — Debian snapshot 503 during the native container build

- **Runs:** `42707311457`, `42708047372`.
- **Job:** Build native Monero/JNI archives.
- **Diagnostic:** `E: Failed to fetch http://snapshot.debian.org/... 503 No healthy backends`
  for `flex`, `autotools-dev`, `bison`, `cpp`, `libtool`, and `python-is-python3`;
  the container base image stage exited 100/1.
- **Root cause:** the pinned snapshot object backend is down. The failure persisted across
  five apt retries and across both `snapshot.debian.org` and its Cloudflare mirror, while
  `deb.debian.org` still serves the same `flex` file. The Monero patch preflight and all
  five patches applied cleanly before this stage in both runs.
- **Pattern search:** the same container base stage is used by every `external-libs`
  target; the `linux` target has a separate `default-jdk-headless` install.
- **Correction:** retry the full `apt-get update`/`install` transaction up to five times
  with increasing backoff. The snapshot date, package set, and reproducibility inputs are
  unchanged.
- **Regression check:** the `native` job must reach the Monero build stage; a genuine
  package or patch failure still fails after the last attempt.
- **Status:** external infrastructure outage. The retry loop is in place; rerun the manual
  `native` job once the snapshot backend recovers.

### CI-018 — Cross-compiled translations generator executed on the build host

- **Run:** `42740103758`.
- **Job:** Build native Monero/JNI archives.
- **Diagnostic:** `./generate_translations_header: 4: Syntax error: word unexpected
  (expecting ")")`, preceded by `ELF` bytes being interpreted as shell input.
- **Root cause:** Monero's `generate_translations_header` helper is cross-compiled for
  the Android ABI and then executed on the x86_64 container host. The host cannot run the
  ARM64 ELF, so the shell falls back to interpreting it as a script.
- **Pattern search:** the failure occurs for every Android ABI; the host `linux` target
  builds a native helper and does not hit it.
- **Correction:** add `0006-drop-generate_translations_header.c-requirement.patch` from
  the same `monero_c` series, replacing the helper with a static empty translations table.
  Android UI localization is unaffected.
- **Regression check:** the seven patches apply with `git apply --index --3way` on the
  pinned base; the `native` job must pass the translations stage.
- **Status:** validated in `42874760135`: the native archives build in CI.

### CI-019 — Android command-line tools download timeout

- **Run:** `42740103758`.
- **Job:** JVM unit tests, `Set up Android SDK`.
- **Diagnostic:** `Request timeout:
  /android/repository/commandlinetools-linux-15859902_latest.zip` from
  `android-actions/setup-android`; the action retried and then aborted.
- **Root cause:** transient `dl.google.com` timeout. Not project code; the previous run
  and the lint job fetched the same tooling successfully.
- **Pattern search:** every Gradle job uses the same action; only one job failed.
- **Correction:** none in the repository. Rerun the workflow; a persistent failure would
  need a pinned tooling mirror or a preinstalled SDK strategy.
- **Status:** external infrastructure failure; monitor on rerun.

### CI-020 — `wallet_rpc_server` call site not updated for preferred inputs

- **Run:** `42741706801`.
- **Job:** Build native Monero/JNI archives.
- **Diagnostic:** `wallet_rpc_server.cpp:1285:155: error: no viable conversion from
  'const std::set<uint32_t>' to 'const std::vector<crypto::key_image>'`.
- **Root cause:** the coin-control patch inserts `preferred_input_list` before
  `subtract_fee_from_outputs` in `wallet2::create_transactions_2`. The 85f036
  `wallet_rpc_server.cpp` call still passes the old argument order. The `monero_c` base
  had already updated this call site in patch 0011.
- **Pattern search:** every `create_transactions_2` and `create_transactions_all` caller;
  `simplewallet` and `wallet_api` were already updated by patch 0008.
- **Correction:** add `0007-add-monero-submodule-support.patch`, which updates the RPC
  call site and makes CMake use `CMAKE_CURRENT_SOURCE_DIR` for submodule builds.
- **Regression check:** all seven patches apply with `git apply --index --3way`; the
  `native` job must compile `wallet_rpc_server.cpp`.
- **Status:** validated in `42874760135`: the native archives build in CI.

### CI-021 — C++ JNI array-region calls compared against an int

- **Run:** `42743643959`.
- **Job:** Assemble debug APKs and link JNI.
- **Diagnostic:** `anonero.cpp:412:61: error: invalid operands to binary expression
  ('void' and 'int')` and the same at line 443.
- **Root cause:** the C++ `JNIEnv::GetLongArrayRegion` and `GetByteArrayRegion` wrappers
  return `void` and signal failure with a pending exception; the adapter compared their
  result with `0` as if they were the C JNI functions.
- **Pattern search:** every `Get*ArrayRegion` and `Set*ArrayRegion` call in the adapter.
- **Correction:** call the function, then check `ExceptionCheck()` and clear the pending
  exception. `SetByteArrayRegion` is not compared.
- **Regression check:** the new `assemble` job compiles both ABIs; native unit tests alone
  would not have caught it because they did not link the adapter.
- **Status:** validated in `42874760135`: assemble/link passes for both ABIs.

### CI-022 — Debug APK names changed under AGP 9

- **Run:** `42750259391`.
- **Job:** Assemble debug APKs and link JNI.
- **Diagnostic:** `missing app/build/outputs/apk/anon/debug/anon-debug.apk`, while the
  diagnostic listing showed `app/build/outputs/apk/anon/debug/app-anon-debug.apk`.
- **Root cause:** AGP 9 names the module APK `app-<flavor>-<buildType>.apk`; the workflow
  still expected the older `<flavor>-<buildType>.apk` name. Gradle had succeeded.
- **Pattern search:** both `assemble` and `instrumented` used the same hardcoded path.
- **Correction:** use `app-${flavor}-debug.apk` in both jobs. The release verification
  already uses a directory glob and is unaffected.
- **Regression check:** the assemble job lists outputs on failure and verifies minSdk and
  `libanonero.so` in each APK.
- **Status:** validated in CI.

### CI-023 — `aapt2 dump badging` minSdk label mismatch

- **Run:** `42841238523`.
- **Job:** Assemble debug APKs and link JNI.
- **Diagnostic:** `app-anon-debug.apk reports minSdk '', expected '29'`.
- **Root cause:** the workflow only matched `sdkVersion:'…'`. The build-tools 37 badging
  output did not provide that label for this APK, so the extraction was empty even though
  the APK existed and Gradle succeeded.
- **Pattern search:** the same extraction existed in the assemble and instrumented jobs and
  in the release APK verification.
- **Correction:** accept `minSdkVersion` or `sdkVersion`, tolerate leading whitespace, and
  print the first badging lines if the value is still empty. Applied to all three sites.
- **Regression check:** the assemble job now fails with a diagnostic only when the APK is
  genuinely unreadable.
- **Status:** validated in CI.

### CI-024 — Emulator runner interpreted a backslash as a Gradle task

- **Run:** `42847493558`.
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** `Task '\' not found in root project 'anonero' and its subprojects.`
- **Root cause:** the `android-emulator-runner` `script` input passed the multi-line Gradle
  command without preserving the shell continuation, so the trailing `\` became a task
  argument. The emulator itself booted.
- **Pattern search:** the instrumented job was the only place using a `script:` block with
  a backslash continuation.
- **Correction:** put the two connected-test tasks on one line.
- **Regression check:** the next instrumented run must start the tests instead of failing
  task selection.
- **Status:** validated in CI.

### CI-025 — CMake warning from the missing project() declaration

- **Run:** `42841238523` (assemble log).
- **Job:** Assemble debug APKs and link JNI.
- **Diagnostic:** `CMake Warning (dev) ... No project() command is present` in
  `app/CMakeLists.txt`.
- **Root cause:** the adapter CMake file declared only `cmake_minimum_required`, so CMake
  invented a default project and emitted developer warnings.
- **Pattern search:** the project-owned CMake files; `app/CMakeLists.txt` was the only one
  without `project()`.
- **Correction:** add `project(anonero LANGUAGES C CXX)` immediately after
  `cmake_minimum_required`.
- **Regression check:** the assemble job's CMake configure output must be free of this
  warning.
- **Status:** validated in `42874760135`: the assemble CMake configure is warning-free.

### CI-026 — API 35 emulator dies after boot without KVM

- **Run:** `42854503254`.
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** emulator boot took `963951 ms`, then `settings put` and `input keyevent`
  failed with `Broken pipe`; adb exited 224. The log shows `/dev/kvm` is unavailable and
  the emulator runs with `-accel off`.
- **Root cause:** hosted Linux runners do not expose KVM, so the x86_64 image runs under
  software emulation. The 4 GB guest plus 4 cores on the runner is unstable and the
  emulator process died immediately after boot.
- **Pattern search:** the instrumented job is the only emulator consumer; API 29 and ARM64
  images are not viable on this runner either.
- **Correction:** the RAM experiments are 4096M (boot/post-boot crash), 2048M (one full
  green run), and 3072M (four failures). 2048M is the only configuration with a green
  run, but the sample is small and each run changed code. The diagnostics step now
  captures guest memory and `system_server` state before choosing a new value.
- **Regression check:** the next run must reach the tests and either pass both XML files
  or produce the guest diagnostics. This does not replace KVM; a self-hosted KVM runner
  or device farm is required for deterministic results.
- **Status:** mitigated; `43528256395` passed both flavors after a 919 s boot, but the
  hosted emulator remains non-deterministic without KVM.

### CI-027 — Instrumented job green while the NERO report had a failure

- **Run:** `42862824199`.
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** the job reported success, but
  `flavors/nero/TEST-test(AVD) - 15.xml` contained `tests="1" failures="1"` with
  `testRunFailed ... Instrumentation run failed due to failed to attach`.
  `test-result-exit-code.txt` was `0`. The ANON report passed.
- **Root cause:** the emulator runner/AGP returned success despite an instrumentation
  attach failure, so the workflow trusted the exit code and ignored the XML. NERO may have
  started while the software-emulated device was still settling, or its app process failed
  to start.
- **Pattern search:** the upload step only warns when reports are absent; no step parsed
  the report contents. This affected both flavors.
- **Correction:** add a required `Verify instrumented results` step that parses every
  `TEST-*.xml`, requires anon and nero reports with at least one test, and fails on any
  `failures`/`errors`. Add `adb wait-for-device` and a 10-second settle before Gradle.
- **Regression check:** the next instrumented run must either pass both XML reports or fail
  the job; a green job with a failed XML is no longer possible.
- **Status:** validated on `42874760135`: both anon and nero XML reports contain one test,
  zero failures, and zero errors. The 10-second settle resolved the cold-start attach
  failure.

### CI-028 — Gradle update advisory after the green run

- **Run:** `42874760135` (lint reports).
- **Diagnostic:** `AndroidGradlePluginVersion: A newer version of Gradle than 9.9.0 is
  available: 9.9.1` for both flavors.
- **Root cause:** external release metadata changed after the earlier zero-warning lint
  run. The project pins Gradle 9.9.0 deliberately.
- **Pattern search:** both flavors and the release lint use the same wrapper pin.
- **Correction:** document the targeted `AndroidGradlePluginVersion` ignore in
  `app/lint.xml`; toolchain upgrades remain a reviewed change and are recorded in
  `docs/architecture/dependencies.md`.
- **Regression check:** the next lint report must remain 0/0; a code warning is still
  reported.
- **Status:** validated in `42874760135`: Android Lint remains 0/0 with the targeted ignore.

### CI-029 — First instrumented flavor fails to attach on the KVM-less runner

- **Runs:** `42862824199` (nero first), `43034549647` (anon first).
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** the first flavor's XML contains `testRunFailed ... Instrumentation run
  failed due to failed to attach`; the second flavor passes about 12 minutes later.
- **Root cause:** the software-emulated device is slow immediately after boot; the first
  instrumentation attach times out while ART/package work is still settling. The failure
  alternates with task order, so it is not tied to one flavor's code.
- **Pattern search:** both flavors and both runs; only the first task fails.
- **Correction:** keep 2048M (the only configuration with a green run), replace the fixed
  sleep with a `system_server`/package/activity health gate, and retry only a flavor
  whose XML contains `failed to attach`, `tests="0"`, or `Failed to install` while the
  guest services remain healthy. Assertion, crash, and test failures are not retried; the
  XML, exit-code, and health checks remain mandatory.
- **Regression check:** the next run must show one passing test for each flavor; if the
  retry also fails, the emulator job needs a KVM-capable runner or a device farm.
- **Status:** retry implemented; run `43108046671` failed before it because the guest lost
  `system_server`; validation pending.

### CI-030 — Emulator runner executes `script` line by line

- **Run:** `43058769285`.
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** `[command]/usr/bin/sh -c for flavor in anon nero; do` followed by
  `Syntax error: end of file unexpected (expecting "done")`. Gradle had completed
  successfully and the XML validation step passed.
- **Root cause:** `ReactiveCircus/android-emulator-runner` runs each line of the `script`
  input as a separate shell command. Multi-line loops and backslash continuations cannot
  live there.
- **Pattern search:** the same input previously failed with `Task '\' not found` when a
  Gradle command used a backslash continuation.
- **Correction:** move the wait, Gradle invocation, attach-only retry, and exit status into
  `.github/scripts/run-instrumented.sh`; the workflow calls `bash` on that file in one
  line.
- **Regression check:** `bash -n` and actionlint validate the script; the XML validation
  remains mandatory.
- **Status:** validated in `43108046671`: the script ran as one line without a shell syntax
  error; the XML gate then caught the zero-test run.

### CI-031 — `system_server` lost during connected tests without KVM

- **Runs:** `43108046671` (`02b97b8`), `43122308945` (`381d8ab`).
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** both `TEST-*.xml` files contain `tests=0` while Gradle prints
  `BUILD SUCCESSFUL` and writes `test-result-exit-code.txt` with `0`. In
  `43108046671` the log shows `Can't find service: package`, `Broken pipe (32)`, and
  `Can't find service: activity`. In `43122308945` the guest diagnostics show
  `Fatal signal 6 (SIGABRT)` in `system_server` (`binder:2000_2`) at 00:37:30, while
  Gradle was running `compile*DebugAndroidTestJavaWithJavac`, `dexBuilder*DebugAndroidTest`,
  and `package*DebugAndroidTest`. Guest `MemAvailable` was about 1.9 GB, `/data` had
  5 GB free, and the host had 92 GB free, so memory and disk exhaustion are ruled out.
- **Root cause:** the x86_64 guest runs with `-accel off` because the hosted runner has no
  `/dev/kvm`. The test-APK build is CPU-heavy on the four-core host and starves the
  software-emulated guest; the leading hypothesis is a `system_server` watchdog abort, but
  the system buffer was not captured. The APK is 128 MiB with 24 dex files and 46 MB of
  native libraries, and `jniLibs.useLegacyPackaging = true` extracts them at install time.
- **Pattern search:** prior runs show attach timeouts, `Broken pipe`, adb 224, and a
  script-splitting failure; `42874760135` passed both flavors with 2048M, proving the
  smoke test itself is valid.
- **Correction:** build the app and AndroidTest APKs before the emulator starts, limit the
  connected Gradle invocation to two workers, keep the core-service health gate, capture
  crash/system/main logcat plus meminfo/df on failure, extend the XML gate to `skipped`,
  exit-code files, and failure markers, retry attach/zero-test only while the guest is
  healthy, free host disk without deleting the Android SDK, and return to the 2048M
  configuration.
- **Regression check:** the next instrumented run must either pass both flavors or fail
  with guest diagnostics; deterministic success still requires a KVM-capable runner or
  device farm.
- **Status:** resolved in `43528256395`: both flavors passed with the diagnostic,
  prebuild, and worker-limit changes. The hosted emulator remains non-deterministic, so
  KVM or a device farm is still recommended for repeatability.

### CI-032 — Guest services lost immediately after boot, before the test script

- **Run:** `43521054641` (`feb985e`).
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** all code, native, assemble, lint, and JNI jobs pass (62 JVM tests per
  flavor, lint 0/0, no `anonero.cpp`/`anonero.h` warning under `-Werror`). The emulator
  boots after 630 s; the runner's post-boot `adb shell input keyevent 82` then fails with
  `cmd: Failure calling service input: Broken pipe (32)`, `cmd overlay list` fails with
  `Failure calling service overlay`, and the action aborts with adb exit code 224.
  `run-instrumented.sh` never starts, so there are no XML reports or guest diagnostics.
- **Root cause:** the KVM-less x86_64 emulator runs with `-accel off`; the guest loses the
  `input`/`overlay` services immediately after boot. This is the same `system_server`
  instability family as CI-031, but it now fails inside the emulator action before our
  health gate. It is not a project-code failure.
- **Pattern search:** `43108046671` lost `system_server` during install; `43122308945`
  lost it during test-APK dexing; `43521054641` lost guest services immediately after boot.
  The prebuild and health-gate fixes are validated, but they cannot run if the action's
  own post-boot adb calls fail.
- **Correction:** none applied. The deterministic fix is a self-hosted KVM runner or a
  device farm; a blind job retry would hide the instability. Do not remove the XML gate or
  the instrumented job.
- **Regression check:** the next instrumented run must either execute both flavor reports
  or fail with guest diagnostics. A stable hosted-emulator path is not proven.
- **Status:** transient. Run `43528256395` (`0cf9db0`) booted in 919 s and passed both
  one-test flavor reports with the same script and image. The hosted emulator remains
  non-deterministic; KVM or a device farm is still the reliable option.

## Warning inventory

### W-001 — `sdkmanager` deprecation

- **Runs:** all accessible runs.
- **Diagnostic:** `WARNING: The SDK Manager CLI tool (sdkmanager) is deprecated. Use Android
  CLI instead.`
- **Origin:** Android command-line tools used by `android-actions/setup-android` and the
  SDK installation step.
- **Impact:** external-tool warning; no project-code warning and no observed failure.
- **Correction:** not applied. Migrating to “Android CLI” requires a validated setup and a
  new CI run; do not suppress it.
- **Status:** pending external-tool migration.

### W-002 — Test reports unavailable on compile failure

- **Runs:** `42686398569`, `42552892586`, `42547686992`.
- **Diagnostic:** `No files were found with the provided path:
  app/build/test-results/...`.
- **Root cause:** the build failed before tests generated XML/HTML reports; the upload step
  used `if: always()` without any Gradle problems report path.
- **Impact:** less diagnostic evidence.
- **Correction:** Gradle problems reports and test reports are now included, a separate
  `lint` job uploads HTML/XML/TXT reports, and test/lint Gradle invocations use
  `--continue --stacktrace`.
- **Status:** implemented; CI pending.

### W-003 — Runner Git default-branch hint

- **Runs:** all accessible runs.
- **Diagnostic:** Git’s `init.defaultBranch` advisory from `actions/checkout`.
- **Impact:** runner noise; not project code.
- **Correction:** none required.
- **Status:** external/runner noise.

### W-004 — `NewerVersionAvailable` lint advisories

- **Run:** `43122308945` (`381d8ab`), both flavors.
- **Diagnostic:** `kotlinx-serialization-json` 1.12.0 → 1.13.0 and
  `bcprov-jdk18on` 1.86 → 1.87 in `gradle/libs.versions.toml`.
- **Origin:** Android Lint release metadata, not project code.
- **Root cause:** upstream releases after the previous lint baseline.
- **Correction:** update to kotlinx.serialization 1.13.0 (Kotlin 2.4.x, reverts the 1.12
  exception wrapping) and Bouncy Castle 1.87 (security release, twelve CVEs). The project
  uses only `SHA256Digest`, `PKCS5S2ParametersGenerator`, `KeyParameter`, and `Base64`
  from Bouncy Castle, none in the documented 1.86-incompatible areas.
- **Regression check:** lint must remain at zero errors and zero warnings and both flavors
  must compile and run their JVM tests.
- **Status:** validated in `43521054641`: lint 0/0, 62 JVM tests per flavor, and both
  flavors compile with the new artifacts.

### W-005 — Runner, action, and emulator warnings

- **Runs:** all accessible runs.
- **Diagnostic:** `actions/download-artifact` emits Node
  `DeprecationWarning: Buffer() is deprecated`; the emulator emits
  `QuickbootFileBacked is disabled`, missing `libX11`/`libX11-xcb`, an unmap warning, and
  an `emu-update-last-check.ini` parse warning.
- **Origin:** third-party JavaScript actions and the Android emulator, not project code.
- **Impact:** log noise; no observed failure. The `Buffer()` warning is fixed only by an
  upstream action release.
- **Correction:** none applied; keep actions SHA-pinned and review upgrades through CI.
- **Status:** external; monitored, not suppressed.

### W-006 — Vendored native dependency warnings

- **Runs:** all runs that build the native archives.
- **Diagnostic:** about 3,000 warning lines from Boost, OpenSSL, ICU/Unicode, and b2
  ("No best alternative for ... install-libraries-shared"), plus `__ANDROID_API__`
  redefinition and cross-toolchain configure warnings.
- **Origin:** vendored third-party build systems and headers, not `app/src/main/cpp`.
- **Impact:** noisy native log; the archives build and the project-owned adapter compiles
  without warnings under `-Wall -Wextra -Wpedantic`.
- **Correction:** do not impose project `-Werror` on third-party headers; the adapter keeps
  `SYSTEM` includes and now uses `-Werror` only for its own target. Upstream fixes are the
  correct route for the vendored warnings.
- **Status:** external; monitored, not suppressed.

## CI evidence recorded

- JVM tests pass on `dd692a8` and later, including 62 tests per flavor on `43528256395`.
- Android Lint is 0/0 on `43528256395`; the W-004 advisories are cleared by the dependency
  update.
- Kotlin compilation emits zero `w:` diagnostics; `43528256395` has none in the unit,
  lint, or assemble logs.
- The native archives build successfully on `42743643959` and later.
- The assemble/link job passes on `42847493558`; it caught the JNI array-region compile
  error, AGP 9 APK naming, and minSdk extraction issues along the way.
- Instrumented smoke tests pass for both flavors on `42874760135` and `43528256395`
  (1 test, 0 failures each; boot 919 s on the latter).
- No `anonero.cpp`/`anonero.h` warning appears in `43528256395`; the adapter compiled under
  `-Wall -Wextra -Wpedantic -Werror`.
- `43521054641` lost the guest `input`/`overlay` services after boot and produced no
  instrumented reports (CI-032); `43528256395` passed with the same code.
- No release, signing, sanitizer, or fuzz run.
