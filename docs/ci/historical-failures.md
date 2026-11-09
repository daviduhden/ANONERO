# Historical GitHub Actions failures

## Run inventory

| Run | Commit | Event | Result | Failed jobs | Notes |
| --- | --- | --- | --- | --- | --- |
| `42686398569` | `52d67d4` | `workflow_dispatch` | failure | JVM unit tests; native | Manual owner run; architecture policy, JNI contract, localization passed; instrumented skipped |
| `42552892586` | `ed8b097` | `push` | failure | JVM unit tests; native | Historical automatic run; instrumented skipped |
| `42547686992` | `c385630` | `push` | failure | JVM unit tests; native | Historical automatic run; instrumented skipped |

No other accessible runs, no artifacts, no release run, no instrumented run, and no
successful build. The two historical `push` triggers are no longer present; workflows are
manual-only as of the current tree.

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
- **Root cause:** the repository does not contain the `.patch` series. In CI the submodule
  checkout step succeeds; in the local audit checkout the submodule is uninitialized. The
  patch series is missing in both cases.
- **Pattern search:** the native build, assemble/link, instrumented tests, release build,
  signing, and publication all depend on these inputs.
- **Correction:** fail-fast preflight with an explicit error; the native input itself must
  still be restored.
- **Regression check:** native preflight requires an initialized submodule and at least one
  patch file; `assemble` now links the JNI library even when instrumented tests are skipped.
- **Status:** blocked at repository level; native and instrumented evidence unavailable.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; CI pending.

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
- **Status:** implemented; native CI blocked.

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
- **Status:** implemented; native CI blocked.

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

## Missing evidence

- No successful JVM run, so Kotlin warnings and test results are unknown.
- Android Lint and the assemble/link job exist but have never run.
- No C++ warning report.
- No native link, instrumented, release, signing, sanitizer, or fuzz run.
- No unit-test artifacts were retained because no test reports were produced.
- No run has ever executed a unit test: two runs failed before Gradle started and one
  failed in Kotlin compilation.
