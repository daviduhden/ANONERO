# Historical GitHub Actions failures

## Run inventory

[Job inventory](run-jobs.tsv) records the API result of each of the 406 jobs
across these 53 runs, including skips and cancellations, with exact commit and
job IDs. The platform and dependency pins for those revisions are described below.

| Run | Workflow | Commit | Event | Result | Failed jobs | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| [45706626778](https://github.com/daviduhden/ANONERO/actions/runs/45706626778) | Agent Knowledge Validation | `b09f9dc` | `workflow_dispatch` | success | none | Both jobs passed, including nine validator regression tests. |
| [45706621403](https://github.com/daviduhden/ANONERO/actions/runs/45706621403) | Tests | `b09f9dc` | `workflow_dispatch` | success | none | Nine jobs passed, with 143 JVM and ten API 35 cases per flavor. Both lint reports retain ten warnings. Instrumented Kotlin compilation reported W-013. |
| [45691880970](https://github.com/daviduhden/ANONERO/actions/runs/45691880970) | Agent Knowledge Validation | `bf1cf25` | `workflow_dispatch` | success | none | Both owner and validation jobs passed. |
| [45691880945](https://github.com/daviduhden/ANONERO/actions/runs/45691880945) | Tests | `bf1cf25` | `workflow_dispatch` | failure | Android Lint; Instrumented tests (API 35) | CI-039 recurred during Gradle configuration in both jobs. Neither produced test or lint reports. Seven jobs passed, including both ARM links and 133 JVM cases per flavor. |
| [`45679643727`](https://github.com/daviduhden/ANONERO/actions/runs/45679643727) | Agent Knowledge Validation | `2ca0edb` | `workflow_dispatch` | success | none | Owner authorization, nine Python regressions, and worktree and committed-range checks passed. |
| [`45679643013`](https://github.com/daviduhden/ANONERO/actions/runs/45679643013) | Tests | `2ca0edb` | `workflow_dispatch` | success | none | All nine jobs passed. Archived XML confirms 130 JVM and nine API 35 tests per flavor, with no failures, errors, or skips. Lint: 0 errors, 13 warnings per flavor. Maven retry was not exercised. |
| [`45677087065`](https://github.com/daviduhden/ANONERO/actions/runs/45677087065) | Agent Knowledge Validation | `b85919b` | `workflow_dispatch` | success | none | Owner authorization and Python and committed-range checks passed. |
| [`45677086262`](https://github.com/daviduhden/ANONERO/actions/runs/45677086262) | Tests | `b85919b` | `workflow_dispatch` | cancelled | JVM unit tests; native, assemble and instrumented cancelled | Maven Central HTTP 429 blocked Gradle classpath configuration before test execution. Lint and static gates passed. See CI-039. |
| [`44949188473`](https://github.com/daviduhden/ANONERO/actions/runs/44949188473) | Agent Knowledge Validation | `27ea36b` | `workflow_dispatch` | success | none | Owner authorization and Python validation passed. |
| [`44949185924`](https://github.com/daviduhden/ANONERO/actions/runs/44949185924) | Tests | `27ea36b` | `workflow_dispatch` | success | none | All nine jobs passed, including native ARM archives, debug assembly and API 35 instrumentation. |
| [`44946813106`](https://github.com/daviduhden/ANONERO/actions/runs/44946813106) | Agent Knowledge Validation | `7207f08` | `workflow_dispatch` | success | none | Owner authorization and Python validation passed. |
| [`44946813069`](https://github.com/daviduhden/ANONERO/actions/runs/44946813069) | Tests | `7207f08` | `workflow_dispatch` | success | none | All nine jobs passed, including native ARM archives, debug assembly and API 35 instrumentation. |
| [`44932222747`](https://github.com/daviduhden/ANONERO/actions/runs/44932222747) | Agent Knowledge Validation | `81f17ef` | `workflow_dispatch` | success | none | Owner authorization and nine agent-knowledge Python regressions passed. |
| [`44932222742`](https://github.com/daviduhden/ANONERO/actions/runs/44932222742) | Tests | `81f17ef` | `workflow_dispatch` | success | none | All nine jobs passed. Hardware-accelerated API 35 boot took about 45 s. Archived reports show 113 JVM tests and four instrumented tests per flavor, all passing. Kotlin source warnings absent; lint retains 11 warnings per flavor. |
| [`44927810481`](https://github.com/daviduhden/ANONERO/actions/runs/44927810481) | Agent Knowledge Validation | `75939ea` | `workflow_dispatch` | success | none | Owner authorization and agent-knowledge Python checks passed. No Android build or device execution in this workflow. |
| [`44927810332`](https://github.com/daviduhden/ANONERO/actions/runs/44927810332) | Tests | `75939ea` | `workflow_dispatch` | cancelled | Instrumented tests (API 35), cancelled | The eight other jobs passed, including both flavors and ARM native/assemble. The software emulator started with TCG and was cancelled during boot polling after about one minute, before tests. See CI-037. |
| [`44924483639`](https://github.com/daviduhden/ANONERO/actions/runs/44924483639) | Agent Knowledge Validation | `0a44b76` | `workflow_dispatch` | success | none | Owner authorization and agent-knowledge Python checks passed. No Android build or device execution in this workflow. |
| [`44924483602`](https://github.com/daviduhden/ANONERO/actions/runs/44924483602) | Tests | `0a44b76` | `workflow_dispatch` | cancelled | Instrumented tests (API 35), cancelled | Eight jobs passed. Emulator 37.3.10 refused software startup, then the action polled for boot until cancellation. See CI-036. |
| [`43640612517`](https://github.com/daviduhden/ANONERO/actions/runs/43640612517) | Tests | `dff2ecb` | `workflow_dispatch` | success | none | Full suite passed. The archived XML reports record four API 35 tests per flavor, all passing. Kotlin warnings remained. See W-007. |
| [`43632817394`](https://github.com/daviduhden/ANONERO/actions/runs/43632817394) | Tests | `1faa159` | `workflow_dispatch` | cancelled | JVM unit tests | Kotlin compilation passes; `NodeSelectionPolicyTest` fails in both flavors because `Node()` loads `WalletManager`, which initializes the unavailable `libanonero` in JVM tests. Android Lint passes; native/assemble were cancelled by the superseding run `43633496677`. |
| [`43633496677`](https://github.com/daviduhden/ANONERO/actions/runs/43633496677) | Tests | `a2c9cfb` | `workflow_dispatch` | failure | Instrumented tests (API 35) | All JVM, lint, JNI, i18n, architecture, native and assemble jobs pass. The API 35 emulator fails before the test script with adb exit 224 and produces no reports; the same KVM-less runner instability as CI-032/035. |
| [`43606679969`](https://github.com/daviduhden/ANONERO/actions/runs/43606679969) | Tests | `f5e43a5` | `workflow_dispatch` | failure | JVM unit tests; Android Lint; Assemble | Native archives (including TLS patch `0008`) succeed; Kotlin compile fails on nullable host/credentials, a removed `wallet` reference, and a missing Koin import; instrumented skipped |
| [`43528256395`](https://github.com/daviduhden/ANONERO/actions/runs/43528256395) | Tests | `0cf9db0` | `workflow_dispatch` | success | none | All jobs pass; instrumented anon and nero each ran 1 test with 0 failures; boot 919 s; 62 JVM tests per flavor; lint 0/0; no adapter warnings under `-Werror` |
| [`43521054641`](https://github.com/daviduhden/ANONERO/actions/runs/43521054641) | Tests | `feb985e` | `workflow_dispatch` | failure | instrumented | All code/native/assemble/lint/JNI gates pass (62 tests per flavor, lint 0/0, `-Werror` clean); boot took 630 s, then `adb`/input/overlay services broke before the test script; no reports |
| [`43122308945`](https://github.com/daviduhden/ANONERO/actions/runs/43122308945) | Tests | `381d8ab` | `workflow_dispatch` | failure | instrumented | All code gates plus native and assemble pass; `system_server` SIGABRT while Gradle dexed the test APKs; both XML `tests=0`; health gate failed closed |
| [`43108046671`](https://github.com/daviduhden/ANONERO/actions/runs/43108046671) | Tests | `02b97b8` | `workflow_dispatch` | failure | instrumented | Guest lost `system_server` during install/uninstall; both XML `tests=0`; all other jobs pass |
| [`43058769285`](https://github.com/daviduhden/ANONERO/actions/runs/43058769285) | Tests | `76fcb79` | `workflow_dispatch` | failure | instrumented | Gradle ran both flavors successfully and XML validation passed; the inline retry loop failed because the emulator runner executes `script` line by line |
| [`43045996049`](https://github.com/daviduhden/ANONERO/actions/runs/43045996049) | Tests | `452f4b6` | `workflow_dispatch` | failure | instrumented | 3 GB + 60 s settle: first flavor (anon) still failed to attach; XML validation correctly failed the job |
| [`43034549647`](https://github.com/daviduhden/ANONERO/actions/runs/43034549647) | Tests | `9fa5c8e` | `workflow_dispatch` | failure | instrumented | All code gates, native, and assemble pass; the first instrumented flavor (anon) failed to attach, the second (nero) passed |
| [`42874760135`](https://github.com/daviduhden/ANONERO/actions/runs/42874760135) | Tests | `c228e36` | `workflow_dispatch` | success | none | All jobs pass; instrumented XML validation confirms anon and nero each ran 1 test with 0 failures; lint 1 external Gradle advisory, suppressed afterwards |
| [`42862824199`](https://github.com/daviduhden/ANONERO/actions/runs/42862824199) | Tests | `30075de` | `workflow_dispatch` | success (masked failure) | none | All jobs reported success, but the NERO XML contained `failures=1` with `failed to attach`; the runner returned 0. XML validation added afterwards |
| [`42854503254`](https://github.com/daviduhden/ANONERO/actions/runs/42854503254) | Tests | `578bd38` | `workflow_dispatch` | failure | instrumented | Native and assemble pass; emulator boots after 16m then dies with `Broken pipe`/adb 224 (no KVM, software x86_64) |
| [`42847493558`](https://github.com/daviduhden/ANONERO/actions/runs/42847493558) | Tests | `27c0987` | `workflow_dispatch` | failure | instrumented | Native and assemble pass; emulator boots; runner treated the `\` continuation as a Gradle task named `\` |
| [`42841238523`](https://github.com/daviduhden/ANONERO/actions/runs/42841238523) | Tests | `1044ec8` | `workflow_dispatch` | failure | assemble | Native, tests, and lint pass; APK produced as `app-anon-debug.apk`; minSdk extraction returned empty |
| [`42750259391`](https://github.com/daviduhden/ANONERO/actions/runs/42750259391) | Tests | `c059214` | `workflow_dispatch` | failure | assemble | Native passes; Gradle produces `app-<flavor>-debug.apk` but the workflow still expected `<flavor>-debug.apk` |
| [`42746361128`](https://github.com/daviduhden/ANONERO/actions/runs/42746361128) | Tests | `f4b6d6d` | `workflow_dispatch` | failure | assemble | Native and code gates passed. Assemble expected `anon-debug.apk` after the build succeeded. See CI-022. |
| [`42743643959`](https://github.com/daviduhden/ANONERO/actions/runs/42743643959) | Tests | `a80dbe7` | `workflow_dispatch` | failure | assemble | Native archives built successfully; the adapter failed to compile because `Get*ArrayRegion` returns void in C++ JNI |
| [`42741706801`](https://github.com/daviduhden/ANONERO/actions/runs/42741706801) | Tests | `de7fbb6` | `workflow_dispatch` | failure | native | JVM tests and lint pass; native compiled for 15m and failed in `wallet_rpc_server.cpp` after the coin-control signature change |
| [`42740103758`](https://github.com/daviduhden/ANONERO/actions/runs/42740103758) | Tests | `f99ade3` | `workflow_dispatch` | failure | JVM unit tests (SDK download); native (translations tool) | Patches applied; Monero tried to run an ARM64 translations binary on the host; Android SDK download timed out |
| [`42708047372`](https://github.com/daviduhden/ANONERO/actions/runs/42708047372) | Tests | `7fe1daa` | `workflow_dispatch` | failure | native | Patch preflight and all five patches pass; Debian snapshot 503 persisted through five apt retries |
| [`42707311457`](https://github.com/daviduhden/ANONERO/actions/runs/42707311457) | Tests | `ba3b3f6` | `workflow_dispatch` | failure | native | Patch series applied cleanly; Debian snapshot 503 blocked the container base stage |
| [`42700250719`](https://github.com/daviduhden/ANONERO/actions/runs/42700250719) | Tests | `e0afc7f` | `workflow_dispatch` | failure | native | JVM tests, lint (0/0), JNI, i18n, policy pass; zero Kotlin warnings; native failed before the series was restored |
| [`42699227920`](https://github.com/daviduhden/ANONERO/actions/runs/42699227920) | Tests | `0006659` | `workflow_dispatch` | failure | native | Lint 0 errors / 0 warnings; native blocked |
| [`42698560608`](https://github.com/daviduhden/ANONERO/actions/runs/42698560608) | Tests | `2788e92` | `workflow_dispatch` | failure | native | Lint had 2 warnings left; fixed afterwards |
| [`42697719108`](https://github.com/daviduhden/ANONERO/actions/runs/42697719108) | Tests | `dda6cde` | `workflow_dispatch` | failure | native | Lint clean except icon/vector warnings; fixed afterwards |
| [`42695062606`](https://github.com/daviduhden/ANONERO/actions/runs/42695062606) | Tests | `746ecf4` | `workflow_dispatch` | failure | native | Lint errors cleared; structural warnings reduced |
| [`42694063725`](https://github.com/daviduhden/ANONERO/actions/runs/42694063725) | Tests | `dd692a8` | `workflow_dispatch` | failure | native | JVM tests and lint pass; compiler fixes validated |
| [`42693644972`](https://github.com/daviduhden/ANONERO/actions/runs/42693644972) | Tests | `47f4a5e` | `workflow_dispatch` | failure | JVM unit tests; Android Lint; native | Regression introduced by the first lint fix; corrected in `dd692a8` |
| [`42692380022`](https://github.com/daviduhden/ANONERO/actions/runs/42692380022) | Tests | `672a1cc` | `workflow_dispatch` | failure | Android Lint; native | First lint run: 35 errors, 92 warnings |
| [`42692271873`](https://github.com/daviduhden/ANONERO/actions/runs/42692271873) | Tests | `672a1cc` | `workflow_dispatch` | cancelled | none recorded | Authorization succeeded. The other jobs were cancelled. Full-run logs unavailable for a cancelled assemble job. |
| [`42686398569`](https://github.com/daviduhden/ANONERO/actions/runs/42686398569) | Tests | `52d67d4` | `workflow_dispatch` | failure | JVM unit tests; native | Manual owner run; architecture policy, JNI contract, localization passed; instrumented skipped |
| [`42552892586`](https://github.com/daviduhden/ANONERO/actions/runs/42552892586) | Tests | `ed8b097` | `push` | failure | JVM unit tests; native | Historical automatic run; instrumented skipped |
| [`42547686992`](https://github.com/daviduhden/ANONERO/actions/runs/42547686992) | Tests | `c385630` | `push` | failure | JVM unit tests; native | Historical automatic run; instrumented skipped |

No release, sanitizer, or fuzz run is recorded in the API history. Current full Tests
evidence is on `b09f9dc`. Earlier green and cancelled runs remain historical baselines.
The two historical `push` triggers are no longer present.

### Provenance and platform coverage

The paginated API returned 53 runs on 2026-11-26: 44 Tests runs (30 failed,
nine succeeded and five cancelled) and nine successful Agent Knowledge
Validation runs. Every returned record is listed above, including the two
historical push events. All records show attempt 1. The release workflow has no
returned runs. The API supplied job inventories and readable log archives for
the previous 49 records, but archives cannot supply steps that were skipped, cancelled
before logging, or never uploaded. During the prior audit `gh run view --log`
could not retrieve particular cancelled job logs for `42692271873` and
`43632817394`. The JVM failure in the latter was available by job ID. The latest API listing counts 51 artifacts, including five for Tests 45706621403. Previously listed
artifacts absent from this snapshot are unavailable via the current listing.
Logs and selected reports were kept outside the repository.

The two Tests push runs at the bottom of the table pinned Gradle 9.7.0, AGP 9.3.3,
Kotlin 2.4.21 and app NDK 29.0.14206865. Their native recipe pinned r27c separately.
All other listed Tests commits pin Gradle 9.9.0, AGP 9.5.1, Kotlin 2.4.21,
app NDK 30.0.16248370 and native r30. These are **source pins** from each run's
commit. A failing setup or skipped job does not establish that its tools ran.
Tests build ANON and NERO debug variants on Linux. The native/archive and assemble
coverage is `arm64-v8a` and `armeabi-v7a` when those jobs succeed. The APK manifest
floor is API 29, while guest execution evidence comes from the API 35 x86_64 AVD.
There is no x86_64 release ABI. Older skipped or cancelled emulator jobs are not
device evidence.

The two older source configurations also differ in native build dependencies:
the push-era revisions use CMake 3.31.12, Boost 1.86.0 and zlib 1.3.2, and manually
dispatched Tests revisions use CMake 4.4.5, Boost 1.92.0 and zlib 1.4. Both pin
OpenSSL 3.5.9. The individual run's exact inputs can be resolved from the linked
SHA using `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`,
`app/build.gradle.kts`, `external-libs/build.sh` and the Monero gitlink.
The [last fully green Tests log](https://github.com/daviduhden/ANONERO/actions/runs/45679643013)
records Clang 21.0.0 for native compilation and API 29 ARM target wrappers. The
emulator used API 35 `google_apis` x86_64 and completed boot in 30.9 seconds.
Agent Knowledge Validation has two jobs and runs Python only, so Android and
native toolchain coverage does not apply. Job results belong to the linked
runs and cannot establish that a configured tool was reached in a cancelled job.

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

### CI-033 — Kotlin compile errors after transport/LWS refactor

- **Run:** `43606679969` (`f5e43a5`), <https://github.com/daviduhden/ANONERO/actions/runs/43606679969>.
- **Jobs:** JVM unit tests, Android Lint, Assemble debug APKs and link JNI.
- **Diagnostic:** both `compileAnonDebugKotlin` and `compileNeroDebugKotlin` failed with:
  - `AnonWalletHandler.kt:329/330`: `Argument type mismatch: actual type is 'String?', but 'String' was expected.`
  - `SignVerifyScreen.kt:214`: `Unresolved reference 'wallet'.`
  - `ReviewTransaction.kt:135`: `Unresolved reference 'inject'.`
- **Root cause:** `SharedPreferences.getString()` returns `String?` and the transport refactor
  passed the node username/password directly to a non-null helper; the sign/verify refactor
  removed the local `wallet` but left one address reference; the new `by inject` in
  `ReviewTransactionViewModel` lacked the Koin Java import. The `host` local was also
  nullable before normalization.
- **Pattern search:** all `nodeFromPrefs` call sites, all `by inject` call sites, all
  `wallet?.address` references in `SignVerifyScreen`, and all preference reads passed to
  non-null helpers. The other `by inject` users already import the correct Koin extension;
  no second unresolved local remained.
- **Correction:** normalize LWS/RPC host, username, and password with `orEmpty()` before
  `nodeFromPrefs`; add a dedicated `primaryAddress` state loaded on IO in sign/verify; add
  `org.koin.java.KoinJavaComponent.inject` to `ReviewTransaction`.
- **Regression check:** local `ktlint`, i18n parity, and JNI parity; the compile jobs are
  the authoritative type/import gate. CI does not yet run `ktlint`.
- **Status:** fixed in `e203549`; Kotlin compilation passed in `43632817394`. That run then
  failed JVM tests for the separate CI-034 model/native issue; `43633496677` validates the
  final tree. The native job from `43606679969` succeeded with patch `0008`.

### CI-034 — JVM tests load `libanonero` through `Node()` construction

- **Run:** `43632817394` (`1faa159`), <https://github.com/daviduhden/ANONERO/actions/runs/43632817394>.
- **Job:** JVM unit tests, both `anon` and `nero` debug variants.
- **Diagnostic:** `NodeSelectionPolicyTest` fails in all three tests with
  `java.lang.UnsatisfiedLinkError: no anonero in java.library.path` at
  `WalletManager.<clinit>` and then
  `NoClassDefFoundError: Could not initialize class io.anonero.model.WalletManager`.
- **Root cause:** the test constructs `Node()` as a pure model. The empty constructor read
  `WalletManager.instance?.networkType`; touching `WalletManager` initializes the native
  library, which is not available in JVM tests. The unit-test job never reached this code
  before CI-033 because the main Kotlin compile failed first.
- **Pattern search:** `Node()` call sites are `NodeSelectionPolicyTest` and the v1 backup
  fallback in `BackupHelper`; the latter immediately overwrites `networkType`. `Node(
  socketAddress)` also delegates to the empty constructor. `Node(JSONObject)` and
  `Node(nodeString)` do not use the native singleton.
- **Correction:** initialize the empty `Node()` from `AnonConfig.getNetworkType()`, the same
  build property `WalletManager` uses, so model construction has no native dependency.
- **Regression check:** the existing `NodeSelectionPolicyTest` is now a JVM-only regression
  for model construction without `libanonero`; the unit job still exercises the full test
  suite for both flavors.
- **Status:** fixed in `500a1a9`; `43633496677` passed JVM, lint, native, assemble and all other non-instrumented jobs. Only the API 35 emulator failed (CI-035).
- **Superseding run:** the old run's native and assemble jobs were cancelled by the
  workflow concurrency group when `43633496677` started; Android Lint passed.

### CI-035 — API 35 emulator adb failure after all code gates pass

- **Run:** `43633496677` (`a2c9cfb`), <https://github.com/daviduhden/ANONERO/actions/runs/43633496677>.
- **Job:** Instrumented tests (API 35).
- **Diagnostic:** owner authorization, JVM unit tests, Android Lint, JNI contract,
  localization, architecture policy, native archives and assemble/link all pass. The
  emulator job fails in `Run instrumented tests on API 35` with adb exit code 224; no XML
  reports or diagnostics are uploaded, and the result verifier fails closed.
- **Root cause:** the hosted runner has no KVM; the software x86_64 emulator/guest adb
  transport fails before the project test script. This is the same runner family as
  CI-032.
- **Correction:** none applied. Do not add blind retries or remove the result gate; use a
  KVM runner or device farm for deterministic instrumented evidence.
- **Regression check:** all non-instrumented jobs green on this run; next instrumented run
  must execute both flavor reports or fail with guest diagnostics.
- **Status:** infrastructure failure, not a project-code failure. The commit contains the
  CI-034 model-constructor fix and passed every code gate.

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
  ("No best alternative for ... install-libraries-shared") and cross-toolchain
  configure warnings. See W-008 for the separately identified API macro warning.
- **Origin:** vendored third-party build systems and headers, not `app/src/main/cpp`.
- **Impact:** noisy native log; the archives build and the project-owned adapter compiles
  without warnings under `-Wall -Wextra -Wpedantic`.
- **Correction:** do not impose project `-Werror` on third-party headers; the adapter keeps
  `SYSTEM` includes and now uses `-Werror` only for its own target. Upstream fixes are the
  correct route for the vendored warnings.
- **Status:** external; monitored, not suppressed. The `43606679969` native job also
  succeeded with the new `0008` TLS patch; the adapter itself was not compiled because the
  Kotlin job failed first.

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
- `43606679969` (`f5e43a5`) failed the Kotlin compile step in unit, lint, and assemble
  (CI-033). The same run's native job succeeded, including the new TLS-enforcement patch.
  The instrumented job was skipped because assemble did not produce APKs.
- `43632817394` (`1faa159`) compiled Kotlin and reached JVM tests, but
  `NodeSelectionPolicyTest` loaded `WalletManager`/`libanonero` through `Node()` (CI-034).
  Android Lint passed; native/assemble were cancelled by the superseding run. The fix keeps
  model construction on `AnonConfig.getNetworkType()`; `43633496677` is the validation run.
- No release, signing, sanitizer, or fuzz run.


## 2026-11-14 — static anonymity diagnosis (no Actions run)

Source review found malformed-proxy DNS risk, LWS TLS-prefix bypass, RPC URL-authority
spoofing, shared LWS consent, stale authorization/session callbacks and arbitrary diagnostic
text. Root causes and sibling pattern searches, corrections and pending evidence are in
[`source audit`](../architecture/anonymity-audit-2026-11.md). The manual owner-gated JNI job
now also checks native diagnostics and selected Android anonymity source boundaries.
JVM/instrumented regression sources were added but not executed. Static validation is not
build, runtime, network-anonymity or fund-safety evidence.


## 2026-11-14 — continued-development static diagnoses and 43633496677

Existing a2c9cfb run passed JVM, Android Lint, native, assemble and static jobs, validating
the earlier CI-033/CI-034 baseline fixes. Instrumentation booted in about 605 seconds,
then failed settings animator_duration_scale with Broken pipe (32), adb exit 224, before
the repository test script. No test reports were produced. Optional animation mutation
is disabled; test readiness/result gates remain mandatory. Root guest instability is
unproven by these logs and still requires runner/device evidence.

New static diagnoses: asynchronous recipient validation indexed stale lists and mixed
current addresses with older amounts; snapshots now match and submission recaptures the
whole request. Void freeze APIs could report success without observed state; refreshed
postconditions now guard both methods. Failed native constructors previously skipped
records; snapshots now fail atomically. False auxiliary close previously discarded
ownership before deleting files; retained ownership and retry precede cleanup/success.

Integration caught an unqualified cancellation catch without an import; the equivalent
pattern scan found no sibling unresolved catches after correction. A source guard and
CI-only regression cases now cover it. i18n's explicit catalog list initially missed new
feature strings; discovery now covers strings*.xml in every locale, keeps primary files
mandatory and has CI-only regression sources. See the [report](../development-iteration-2026-11.md)
for root causes, files, limits and unexecuted tests. No local compilation or test occurred.

### CI-036 — API 35 emulator refuses software CPU emulation

[Run 44924483602](https://github.com/daviduhden/ANONERO/actions/runs/44924483602/job/136914394319)
on `0a44b76` passed the eight other jobs. The runner selected `-accel off` and
the Android Emulator rejected startup without an explicit TCG opt-in. The action
continued polling `sys.boot_completed` after the emulator exited. The repository's
instrumentation script never ran and no test reports were generated. The run was
cancelled manually.

The shared Tests job now sets `ANDROID_I_WANT_MY_TCG=yes` on the emulator action
step, as described in the [Android command-line documentation](https://developer.android.com/studio/run/emulator-commandline).
The generated release job carries the same setting. Verify synchronization
statically and confirm boot plus both instrumented test reports in the next
authorized run. Software emulation remains slow and unsupported by Android.

### CI-037 — TCG startup succeeds and the API 35 run is stopped during boot

[Tests run 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332/job/136925466159)
on `75939ea` exercised the CI-036 fix. The environment included
`ANDROID_I_WANT_MY_TCG=yes`. Emulator 37.3.10 started with `-accel off` and
reported that the runner user lacked permission to use KVM. TCG reported missing
AVX/F16C and adb initially could not connect, then the action polled
`sys.boot_completed`. GitHub cancelled the job about 61 seconds after emulator
launch, before the 1,200-second boot limit. The log establishes TCG startup and
cancellation before tests. It does not establish a guest crash, an exhausted boot
timeout, or a passing test.

- **Pattern:** Tests and Release share the embedded instrumented job. The historic
  Linux path assumed software emulation whenever acceleration was unavailable,
  without separating a missing KVM device from inaccessible permission bits.
- **Local correction:** prepare device access only for the runner user when
  `/dev/kvm` exists as a character device and is inaccessible. The permission
  diagnostic alone does not prove the device is present or usable. Retain bounded
  TCG fallback when the device is absent.
- **Regression evidence:** check generated-suite synchronization, then require boot,
  the repository instrumented script and both result sets on an authorized Linux CI
  run. The [instrumented job in 44932222742](https://github.com/daviduhden/ANONERO/actions/runs/44932222742/job/136938186389)
  reports hardware acceleration enabled. Emulator 37.3.10 booted in about 45 seconds.
  Both XML reports contain four tests with zero failures, errors or skips. This
  confirms the prepared Linux path on that runner.

### CI-038 — Preventive review of instrumented fallback exit accounting

The current source was reviewed against CI-027 and CI-029. The combined Gradle
invocation could fail for both flavors, and a successful isolated retry of the
first could reset the aggregate result to zero. Empty reports also admitted retry
without evidence of an instrumentation attach failure. XML and exit-code gates
limit this failure mode, but do not justify incorrect aggregation.

- **Pattern:** review the result writer and verifier in
  [run-instrumented.sh](../../.github/scripts/run-instrumented.sh) and
  [Tests](../../.github/workflows/test.yml), including the synchronized Release copy.
- **Correction in this audit:** preserve independent flavor outcomes and require
  an explicit attach diagnosis for a retry. A passing retry for one flavor must
  not clear the other flavor's error.
- **Regression:** the CI-only runner fixture exercises an attach failure and a
  successful retry while the other flavor reports a Gradle failure. It checks that
  the aggregate remains failed even if both final XML files report success. The
  `43034549647` artifact preserves the original ANON shape: `tests=1 failures=1
  errors=0 skipped=0`, with a `failure` element reporting an attach error. Confirm
  that missing results or unrelated install/Gradle errors fail. The Python regression
  suite passed in [44932222742](https://github.com/daviduhden/ANONERO/actions/runs/44932222742/job/136931166407).
  Both flavor jobs completed normally in the emulator run without exercising a
  real attach retry.

### W-007 — Kotlin safe calls after a proven non-null receiver

[Tests run 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332)
on `75939ea`, the preceding `44924483602`, and the green `43640612517`
reported five distinct warnings in
[NetworkSettings.kt](../../app/src/main/java/io/anonero/ui/home/settings/NetworkSettings.kt):
four safe calls on `Wallet` and one on `WalletManager` in branches whose guards
already prove the receiver non-null. The warning appeared in successful
compilation of both flavors. Earlier logs show the same family in other files,
including Send, NodeSettings and BackupHelper. The present warning correction
removes the five redundant safe calls and keeps the guards around genuinely
nullable state. Both flavors compiled in `44932222742` with no captured Kotlin
source warnings. No Gradle compiler warning policy was weakened.

### W-008 — OpenSSL configuration duplicates the NDK Android API definition

[Tests run 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332/job/136917924387)
compiled the dependency with API 29 target wrappers and
`-D__ANDROID_API__=29`. Clang already defines the macro from
`__ANDROID_MIN_SDK_VERSION__` and warns with `-Wmacro-redefined`. This is an
invocation from the project-owned [build recipe](../../external-libs/build.sh),
even though OpenSSL source units emit the diagnostics. Both ARM target wrappers
show the pattern. The pin's OpenSSL Android Configure target parses that option
to select its compiler API level. Removing it without replacing that selection
could silently target the NDK's highest API. The local fix adds a
separate API selector to the pinned OpenSSL configuration and checks that
Configure selected the exact NDK wrapper without redefining the built-in
macro. [Tests 45679643013](https://github.com/daviduhden/ANONERO/actions/runs/45679643013)
confirms the effective OpenSSL targets `android-arm64` and `android-arm`, the
`aarch64-linux-android29-clang` and `armv7a-linux-androideabi29-clang` wrappers,
and no duplicate Android API definition in the compiler log. Both archives
built and linked in the debug APKs. See [configuration and rollback](openssl-android-api.md).

### W-009 — Lint findings from a successful job

The [lint artifacts in 44927810332](https://github.com/daviduhden/ANONERO/actions/runs/44927810332)
contain 0 errors and 24 warnings for each debug flavor. The reports include 12
unused resources from the older proxy settings flow, four `UseKtx` findings,
and eight `GradleDependency` advisories for Lifecycle, Compose BOM and CameraX.
The latter are update notices rather than runtime compatibility failures. Three
`UseKtx` sites are persistence paths that must observe the Boolean
`SharedPreferences.Editor.commit()` result. Replacing them with a unit-returning
extension would hide failed writes. Any change must preserve checked commit
semantics, remove legacy strings only after searching references and all locales,
and verify a new lint XML report for both flavors. This audit removes the unused
string definitions across all four catalogs and migrates the one URI call. The
three commit-result call sites stay explicit, and no dependency pin changed.
In [44932222742](https://github.com/daviduhden/ANONERO/actions/runs/44932222742)
the archived lint XML contains zero errors and 11 warnings per flavor, all from
the eight dependency notices and the three checked editor calls. The unused
resource and URI warnings are absent. See the [preventive audit](preventive-audit.md).

### CI-039 — Maven Central throttled Gradle configuration on b85919b

- [Tests 45677086262](https://github.com/daviduhden/ANONERO/actions/runs/45677086262)
  returned HTTP 429 when resolving the Kotlin Gradle plugin from Maven Central
  during root buildscript classpath configuration. The 61 further failures
  shared that cause. No JVM test ran. The native job was cancelled during
  Monero compilation. Debug assembly and instrumentation did not run to
  completion, so this run cannot verify the JNI or device fixes.
- [Tests 44949185924](https://github.com/daviduhden/ANONERO/actions/runs/44949185924)
  resolved the same pinned toolchain and both JVM suites passed. The newer
  run's Android Lint job also configured and ran on its own runner.
  [Agent Knowledge Validation 45677087065](https://github.com/daviduhden/ANONERO/actions/runs/45677087065)
  passed on the same revision.
- All pre-emulator Gradle invocations can encounter the same classpath failure.
  Tests and Release now use a shared wrapper that keeps the original output
  and waits 60 seconds before at most one retry, limited to Maven Central
  HTTP 429 during root classpath resolution before any task starts. A retry
  still has to succeed. The wrapper uses the same pinned inputs, and connected
  instrumentation keeps its own retry policy. CI's static-checker regressions
  exercise eligible and ineligible failures and preserve the second exit code.
- The available lint reports contain zero errors and 16 warnings per flavor.
  Eight are dependency update notices. Five are explicit preference editor
  sites that rely on the Boolean commit result (including two in the PIN rate
  limiter). The remaining three are unused labels for the retired signed-file
  broadcast path, removed from every locale after checking code and XML
  references. Recheck both lint XML reports on the next run. No dependency
  pin has changed.
- The cancelled native log contains upstream warnings including easylogging++
  non-null diagnostics, Boost timer deprecations in Monero, and warnings from
  autotools and libtool. It has no completed JNI link evidence. Upstream
  diagnostics need comparison against a completed build and are outside this
  Gradle failure fix.


### W-010 — Boost Histogram shared-install diagnostic in static ARM builds

- **Evidence:** [native job 139436525653](https://github.com/daviduhden/ANONERO/actions/runs/45679643013/job/139436525653)
  on `2ca0edb` logs `No best alternative` for
  `libs/histogram/install-libraries-shared` under `link=static` for ARM32 and
  ARM64. The earlier cancelled `b85919b` run shows the same diagnostic for its
  ARM64 build. The completed run produced both ARM archives and the two debug
  APKs successfully linked.
- **Attribution:** The [pinned Histogram build definition](https://github.com/boostorg/histogram/blob/boost-1.92.0/build.jam)
  registers the module with Boost.Build despite providing a header-only alias.
  ANONERO requests selected compiled components in `dep_boost` and does not
  select Histogram. This is emitted by the dependency's install graph rather
  than the ANONERO JNI compiler. [Boost.Build #784](https://github.com/boostorg/build/issues/784)
  contains the same diagnostic on a different toolset.
- **Status:** Keep static linkage and the required archive checks. No validated
  Android fix for the pinned dependency was found in the material reviewed.
  A candidate upstream change needs successful native and assemble jobs for
  both ABIs and archive/link comparison. Do not suppress the diagnostic.

### W-011 — Emulator action SDK XML parser mismatch

- **Evidence:** [instrumented job 139452383333](https://github.com/daviduhden/ANONERO/actions/runs/45679643013/job/139452383333)
  on `2ca0edb` warns that its SDK XML reader supports version 3 while reading
  version 4. The warning accompanies `sdkmanager --licenses`, package installs
  and `avdmanager create avd`, before Gradle runs the instrumented tests.
- **Attribution:** The [pinned SDK installer](https://github.com/ReactiveCircus/android-emulator-runner/blob/a421e43855164a8197daf9d8d40fe71c6996bb0d/src/sdk-installer.ts)
  reuses the existing command-line tools directory and prepends its
  `latest/bin` path. The preceding SDK setup logs identify the preinstalled
  `latest` as 12.0 and invoke the downloaded sibling 22.0 explicitly, without
  the XML mismatch. Reselecting the stale alias explains the warning, but the
  emulator step does not print its resolved executables. This is a runner
  SDK metadata warning, not Android Lint. The separate deprecation message
  from the SDK 22.0 CLI is W-001.
- **Status:** Verify the effective executable and command-line tools revision
  before changing the installer or a pin. Any update needs both APK assembly
  and nonzero passing XML and exit codes for both flavors. The tested job
  passed with nine tests per flavor despite the diagnostic.

### W-012 — Artifact action Node.js Buffer deprecation

- **Evidence:** [assemble job 139448814102](https://github.com/daviduhden/ANONERO/actions/runs/45679643013/job/139448814102)
  and the instrumented job on `2ca0edb` emit Node `DEP0005` during native
  artifact download. Both jobs completed and consumed the native artifact.
- **Attribution:** The warning comes from the download action runtime, outside
  the Android and native compilation steps. [Actions Toolkit #2003](https://github.com/actions/toolkit/issues/2003)
  tracks that warning in the action's ZIP extraction path.
- **Status:** Compare a future pinned action with the existing artifact
  verification and both platform jobs before replacing it. Keep the warning
  visible and do not skip integrity checks.

### CI-039 follow-up — Latest report and regressions on 2ca0edb

[Tests 45679643013](https://github.com/daviduhden/ANONERO/actions/runs/45679643013)
passed with the new Gradle wrapper. The JNI job ran 36 Python regressions,
including the synthetic rate-limit case. No live Maven throttle or retry
appears in the job log. Archived lint XML confirms that the unused strings
were removed across the variants. The 13 remaining warnings are eight
dependency update advisories and five explicit preference editor calls that
preserve Boolean commit results. The Android and native jobs contain no
project Kotlin compiler warning or adapter source diagnostic. The five
`UseKtx` positions are four files: two calls in `PinThrottle.kt` and one
each in `AnonWalletHandler.kt`, `PrivacySettings.kt` and `TransportModels.kt`.
The existing Unit-returning extension cannot observe write failure.
This does not establish warning-free third-party builds. Native ASan/UBSan/TSan/LSan,
fuzzing, ARM hardware execution, API 29 device execution and production
signing remain without positive Actions evidence.

The five W-009 preference write sites now share an editor helper returning the
synchronous commit result. PIN throttling, transport persistence, LWS and node
selection still observe storage failure. A clock-rebase write failure is covered
by an instrumented regression. The eight dependency advisories remain under
upstream review. The next completed lint XML must confirm the new counts.


### W-013 — Deprecated Compose test rule in instrumented sources

The [API 35 job](https://github.com/daviduhden/ANONERO/actions/runs/45706621403/job/139546185283)
on `b09f9dc4953465a294521db8fda89a27cb66198d` compiled the Android test
sources for each flavor. Both emitted the same warning in
`AnimatedQrCodeSessionTest.kt:31` for the old JUnit4 `createComposeRule`.
The compiler recommends the v2 rule, which schedules coroutines on a standard
test dispatcher. Review synchronization when migrating callers. The existing
animated QR test passed on this run. A future instrumented compilation and both
flavor reports are required to validate a migration.

### W-009 and CI-039 — Follow-up on b09f9dc

The [lint job](https://github.com/daviduhden/ANONERO/actions/runs/45706621403/job/139533120768)
produced both variant XML reports with zero errors, eight `GradleDependency`
and two `UseKtx` warnings each. The eight are three Lifecycle modules, one
Compose BOM and four CameraX modules. The remaining project-source sites
are the editor acquisition in `Prefs.kt:67` and the secure wipe cleanup in
`SecureWipe.kt:160`. Any fix must preserve the persistence verdict for
security-sensitive writes. The prior five locations no longer appear.

The module cache saved by the successful JVM job was restored by assembly
and instrumentation in the same run. Both Gradle tasks completed. This
exercises a warm cache on downstream jobs. Availability after eviction or
during a live external rate limit remains unverified. The JNI job passed
41 Python regressions. No project adapter warning was identified in the
assembly log. The native archives still report W-006/W-010 dependency
warnings, including `-Wnonnull`, deprecated declarations and 32-bit shift
diagnostics. SDK metadata and Node Buffer warnings W-011/W-012 still occur.

### REPORT-002 — Instrumentation evidence from separate output trees

Static review found that the final verdict pooled XML and device exit codes from every
output directory for each flavor. An incomplete report tree could therefore borrow a
successful exit from another tree. The checker now requires both pieces of evidence in
each flavor output. A split-directory fixture covers this case in the existing manual
Python regression job. Both archived flavor reports in run 45848781860 satisfy the revised
checker. No historical incorrect verdict has been demonstrated.
