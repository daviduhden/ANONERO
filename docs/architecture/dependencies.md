# Dependency inventory and update record

Audit date: 2026-11-09. Refresh: 2026-11-13 (lint-driven serialization and Bouncy Castle
updates). Base commit: `ed8b097`.

This record covers direct and relevant transitive dependencies, toolchains, native
pins, GitHub Actions, and container inputs. Versions were checked against official
sources only. No build, Gradle resolution, or test was executed
locally.

Sources used:

- Google Maven metadata (`dl.google.com/dl/android/maven2`).
- Maven Central metadata (`repo1.maven.org`).
- Android SDK repository XML (`dl.google.com/android/repository/repository2-3.xml`).
- Gradle release service (`services.gradle.org/versions/current`).
- GitHub Releases and Git tag objects (`api.github.com`).
- Official project download/checksum files (OpenSSL, Unbound, CMake, Boost, zlib).
- OSV API for the Maven artifacts listed below.

Inventory size: 57 components or pins — 34 Android/Kotlin/SDK entries, 16 native or
container entries, and 7 GitHub Actions. Transitive dependencies are not fully
enumerated; `kotlinx.coroutines` was promoted to a direct declaration.

## 1. Android, Kotlin, Gradle, and Jetpack

| Component | Before | After | Latest stable checked | Status |
| --- | --- | --- | --- | --- |
| Android Gradle Plugin | 9.3.3 | **9.5.1** | 9.5.1 | Updated |
| Gradle wrapper | 9.7.0 | **9.9.0** | 9.9.0 | Updated, SHA-256 pinned |
| Kotlin / KGP / Compose compiler / serialization plugin | 2.4.21 | 2.4.21 | 2.4.21 (2.5.0-Beta2 excluded) | Already latest stable |
| Compose BOM | 2026.09.00 | 2026.09.00 | 2026.09.00 | Already latest stable |
| `androidx.core:core-ktx` | 1.19.1 | 1.19.1 | 1.19.1 | Already latest stable |
| `androidx.lifecycle` | 2.11.0 | 2.11.0 | 2.11.0 (2.12.0-alpha05 excluded) | Already latest stable |
| `androidx.activity:activity-compose` | 1.13.0 | 1.13.0 | 1.13.0 (1.14.0-alpha03 excluded) | Already latest stable |
| `androidx.navigation:navigation-compose` | 2.10.2 | 2.10.2 | 2.10.2 | Already latest stable |
| `androidx.datastore:datastore-preferences` | 1.3.0 | 1.3.0 | 1.3.0 | Already latest stable |
| `androidx.core:core-splashscreen` | 1.2.0 | 1.2.0 | 1.2.0 | Already latest stable |
| CameraX | 1.6.2 | 1.6.2 | 1.6.2 (1.7.0-rc02 excluded) | Already latest stable |
| `androidx.test.ext:junit` | 1.3.0 | 1.3.0 | 1.3.0 (1.4.0-alpha03 excluded) | Already latest stable |
| Espresso | 3.7.0 | 3.7.0 | 3.7.0 (3.8.0-alpha03 excluded) | Already latest stable |
| JUnit 4 | 4.13.2 | 4.13.2 | 4.13.2 | Already latest stable |
| Koin BOM | 4.2.2 | 4.2.2 | 4.2.2 (4.3.0-Beta1 excluded) | Already latest stable |
| kotlinx.serialization JSON | 1.12.0 | **1.13.0** | 1.13.0 | Updated; built on Kotlin 2.4.10 (project uses 2.4.21) and reverts the 1.12 exception-wrapping change |
| kotlinx.coroutines | transitive | **1.11.0 (explicit)** | 1.11.0 | Declared directly; was previously resolved transitively |
| kmp-tor runtime | 2.6.0 | 2.6.0 | 2.6.0 | Already latest stable |
| kmp-tor resource-exec-tor | 409.5.0 | 409.5.0 | 409.5.0 | Already latest stable |
| Haze / Haze Blur | 2.1.0 | 2.1.0 | 2.1.0 | Already latest stable |
| Hummingbird UR | 1.7.6 | 1.7.6 | 1.7.6 | Already latest stable |
| Sonner | 0.4.0 | 0.4.0 | 0.4.0 | Already latest stable |
| BoofCV core | 1.6.0 | 1.6.0 | 1.6.0 | Already latest stable |
| ZXing core | 3.5.4 | 3.5.4 | 3.5.4 | Already latest stable |
| Timber | 5.0.1 | 5.0.1 | 5.0.1 | Already latest stable |
| Bouncy Castle `bcprov-jdk18on` | 1.86 | **1.87** | 1.87 | Security release (12 CVEs); ANONERO uses only `SHA256Digest`, `PKCS5S2ParametersGenerator`, `KeyParameter`, and `Base64` |
| Accompanist Permissions | 0.37.3 | 0.37.3 | 0.37.3 | Maintained but in maintenance mode; not replaced without device validation |

### Android SDK and toolchain

| Component | Before | After | Latest stable checked | Status |
| --- | --- | --- | --- | --- |
| `compileSdk` | 37.0 | **37.2** (`compileSdkMinor = 2`) | 37.2 | Updated; `targetSdk` remains 37.0 |
| `targetSdk` | 37 | 37 | 37 | Unchanged by design; behavior changes need device tests |
| `minSdk` | 29 | 29 | 29 | Unchanged |
| Build Tools | 37.0.0 | 37.0.0 | 37.0.0 | Already latest stable |
| NDK (app) | 29.0.14206865 | **30.0.16248370** | 30.0.16248370 | Updated |
| CMake (Android SDK, app JNI) | 3.31.6 | **4.1.2** | 4.1.2 | Updated; `app/CMakeLists.txt` minimum raised to 3.22 |
| JDK (CI) | 17 | 17 | 17 (AGP 9.5 min/default); JDK 25 is the latest LTS | Retained; JDK 21/25 not adopted without a supported-toolchain decision |

`compileSdkMinor` is the AGP 9.5 DSL property for minor API levels. CI now installs
`platforms;android-37.2` instead of `platforms;android-37.0`.

## 2. Native dependencies

| Component | Before | After | Latest stable checked | Status |
| --- | --- | --- | --- | --- |
| Monero | v0.18.5.4 / `85f036ba…` | v0.18.5.4 / `85f036ba…` | v0.18.5.4 | Already latest stable |
| CMake (external-libs) | 3.31.12 | **4.4.5** | 4.4.5 | Updated, SHA-256 pinned |
| NDK (external-libs) | r27c / 27.2.12479018 | **r30 / 30.0.16248370** | r30 | Updated, SHA-1 pinned |
| Boost | 1.86.0 | **1.92.0** | 1.92.0 | Updated, SHA-256 pinned |
| zlib | 1.3.2 | **1.4** | 1.4 | Updated, SHA-256 pinned; includes CVE-2026-85091 fix |
| OpenSSL | 3.5.9 | 3.5.9 | 3.5.9 (3.5 LTS); 4.1.0 is latest major | Retained; 4.x blocked |
| libsodium | 1.0.22 | 1.0.22 | 1.0.22 | Already latest stable |
| Expat | 2.10.0 | 2.10.0 | 2.10.0 | Already latest stable; security release |
| Unbound | 1.26.2 | 1.26.2 | 1.26.2 | Already latest stable; security release |
| libzmq | 4.3.5 / `622fc6d…` | 4.3.5 / `622fc6d…` | 4.3.5 | Already latest stable |
| libiconv | 1.19.1 | 1.19.1 | 1.19.1 | Already latest stable |
| Polyseed | `bd79f50…` | `bd79f50…` | v3.0.2 | Pinned by the restored Monero patch series; native archives built in CI |
| utf8proc | `3de4596…` | `3de4596…` | v2.12.0 | Pinned by the restored Monero patch series; native archives built in CI |
| bc-ur | `d82e7c7…` | `d82e7c7…` | `MrCyjaneK/bc-ur` | Pinned by the restored Monero patch series; native archives built in CI |
| Debian base | bookworm digest `6ebd97f…` | unchanged | latest bookworm digest not verified | Blocked; digest/snapshot update needs a verified build |
| Debian snapshot | `20260824T000000Z` | unchanged | newer snapshots exist | Blocked for reproducibility review |

Checksums were taken from official release metadata: GitHub asset digests for
CMake/Boost/zlib/utf8proc, the OpenSSL `.sha256` asset, the Unbound `.sha256` file, and
the Google SDK repository XML for NDK/CMake/build-tools/platform. No checksum was
invented.

## 3. GitHub Actions

All actions remain pinned to full commit SHAs. Existing pins were verified against the
current release tag commit.

| Action | Version | SHA | Status |
| --- | --- | --- | --- |
| `actions/checkout` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | Already latest |
| `actions/download-artifact` | v8.0.2 | `9000827ccba6bdab643e8b6fd33ac0654aef8333` | Already latest |
| `actions/setup-java` | v6.2.0 | `77dd5b2ce225ea11469928ac317850245a74ae78` | Already latest |
| `android-actions/setup-android` | v4.0.4 | `be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd` | Already latest |
| gradle/actions | v6.4.1 | `8da46b539cd8fe6ab27ca75128919a18d757604c` | Already latest |
| `actions/upload-artifact` | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | Already latest |
| `ReactiveCircus/android-emulator-runner` | v2.9.0 → **v2.38.0** | `a421e43855164a8197daf9d8d40fe71c6996bb0d` | Updated |

`v2.38.0` is the release that adds Build Tools 37.0.0 support.

## 4. Code and configuration changes

- `gradle/libs.versions.toml`: AGP 9.5.1; explicit kotlinx-coroutines 1.11.0 entry.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.9.0 and official SHA-256.
- `gradle/wrapper/gradle-wrapper.jar`: official Gradle 9.9.0 wrapper JAR, verified
  against the published `gradle-9.9.0-wrapper.jar.sha256`
  (`621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8`).
- `app/build.gradle.kts`: `compileSdkMinor = 2`, NDK 30.0.16248370, CMake 4.1.2,
  explicit kotlinx-coroutines dependency, updated comments.
- `app/CMakeLists.txt`: minimum CMake 3.22 (compatible with CMake 4.x).
- `external-libs/build.sh`: CMake 4.4.5, NDK r30, Boost 1.92.0, zlib 1.4, and
  matching verified hashes.
- `.github/workflows/test.yml` and `release.yml`: Android platform 37.2, NDK 30,
  CMake 4.1.2, and emulator-runner v2.38.0.
- Documentation: this record and the Android/architecture references to compileSdk.

No application source API changes were required by these updates. The AGP 9.5 and
Gradle 9.9 APIs used by the project are the same APIs already used under AGP 9.3 and
Gradle 9.7.

## 4.1 Static analysis tooling (not build dependencies)

The repository checks in `.editorconfig`, `.clang-format`, `.yamllint`, `pyproject.toml`,
and `external-libs/.clang-format`. These tools are run manually and are **not** build
dependencies:

| Tool | Scope | Version used for the formatting audit |
| --- | --- | --- |
| ktlint | `*.kt`, `*.kts` | 1.8.0 |
| clang-format | `app/src/main/cpp/*` | 23.1.5 |
| Ruff | `scripts/*.py` | 0.17.4 |
| shfmt | `*.sh` | 3.14.1 |
| ShellCheck | `*.sh` | 0.11.0 |
| yamllint | `.github/workflows` | 1.38.0 |
| actionlint | `.github/workflows` | 1.7.12 |
| zizmor | `.github/workflows` | 1.32.0 |

The versions are not pinned by the repository and no CI job enforces the formatting yet;
a future gate must pin them to avoid spurious diffs between formatter releases. The
checked-in `external-libs/.clang-format` sets `DisableFormat: true` so a recursive
invocation cannot reformat the vendored Monero tree.

## 5. Security review

- OSV queries for the pinned Maven artifacts (Bouncy Castle, ZXing, Timber, BoofCV,
  Hummingbird, Koin, kotlinx.serialization, kotlinx.coroutines, kmp-tor, Haze,
  Accompanist) returned no advisories on 2026-11-09. This is not a transitive
  dependency scan.
- zlib 1.3.2 → 1.4 includes the non-blocking `gzwrite()` buffer-overflow fix
  (CVE-2026-85091).
- OpenSSL 3.5.9 is a security-patch release and is retained as the latest 3.5 LTS
  patch. OpenSSL 4.1.0 is a new major line and was not adopted without a Monero/native
  ABI and crypto-behavior validation.
- Expat 2.10.0 is a security release (four vulnerabilities fixed).
- Unbound 1.26.2 is a security release (CVE-2026-35501, CVE-2026-50106,
  CVE-2026-96405, CVE-2026-97206, CVE-2026-109125).
- Bouncy Castle 1.87 (2026-11-11) is a security release fixing twelve CVEs in OpenPGP,
  RFC 3161, X.500 name handling, CMS/MIME, PKIX revocation, and the NTRU-family KEMs.
  The 1.86-incompatible areas listed upstream (HQC, Merkle Tree Certificates, C509,
  ML-KEM key derivation, removed XMSS/LMS) are not used by ANONERO; the project imports
  only `SHA256Digest`, `PKCS5S2ParametersGenerator`, `KeyParameter`, and `Base64`.
- kotlinx.serialization 1.13.0 is built on Kotlin 2.4.10 and reverts the exception
  wrapping introduced in 1.12.0; the project uses Kotlin 2.4.21 and still catches
  `SerializationException` in the node and backup serializers.
- No connection, proxy, LWS, Keystore, or Monero protocol behavior was changed by
  these dependency updates.

## 6. Blocked or deferred dependencies

| Dependency | Latest stable | Why not updated |
| --- | --- | --- |
| OpenSSL 4.1.0 | 4.1.0 | New major line; needs Monero, libzmq, Unbound, and native ABI/crypto validation. Current 3.5.9 is the latest 3.5 LTS patch and is a security release. |
| Polyseed v3.0.2 | v3.0.2 | `build.sh` and the restored Monero patch series pin `bd79f50…`. Native compile still needs CI. |
| utf8proc v2.12.0 | v2.12.0 | `build.sh` and the restored Monero patch series pin `3de4596…`. Native compile still needs CI. |
| bc-ur | `MrCyjaneK/bc-ur` `d82e7c7…` | Added by the restored patch series; native compile still needs CI. |
| Debian base/snapshot | unverified | A base digest or snapshot change must be accompanied by a reproducible native build and verified package availability. |
| Gradle dependency lockfile | n/a | Generating a lockfile requires Gradle resolution, which is prohibited locally. CI must generate/validate it in a dedicated change. |
| JDK 21/25 | 21/25 LTS | AGP 9.5 documents JDK 17 as its minimum/default. No project requirement or CI evidence justifies changing the Gradle JDK; source/target remain 17 for Android compatibility. |
| `targetSdk 37.2` | 37.2 | Only `compileSdk` was raised. Changing target behavior requires runtime/device validation. |
| Accompanist Permissions | 0.37.3 | Latest stable and still functional. Replacing it with the platform Activity Result API is a UI behavior change and needs device validation. |
| libzmq 4.3.5 | 4.3.5 | Already the latest stable release, despite its age. |

## 7. Required CI evidence

These updates are **not** validated until GitHub Actions succeeds:

- `unit`, `lint`, `assemble`, `jni-contract`, `i18n`, and `native-language-policy` jobs.
- `native`: CMake 4.4.5 + NDK r30 + Boost 1.92 + zlib 1.4 must produce the Monero
  archives; this is the highest-risk update.
- `instrumented`: API 35 emulator with compileSdk 37.2/NDK 30.
- `release`: AGP 9.5.1 + Gradle 9.9.0 must build six APKs, with the existing
  verification of IDs, versions, minSdk, ABIs, `libanonero.so`, signatures, and
  checksums.
- A future dependency lockfile/verification task should be added in CI if reproducible
  resolution is required.

Static analysis cannot prove that the native toolchain update links or that the APK
runs. Treat all updates as pending CI verification.
