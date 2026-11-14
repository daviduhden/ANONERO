# Native dependencies and Monero build — Agent Instructions

These instructions apply to `external-libs/`. The root `AGENTS.md` remains
authoritative for security, language, build, and Git policy.

## 1. Purpose

`external-libs/` builds the native inputs consumed by the Android app:

- official Monero wallet2 static archives (`libwallet_api.a`, `libwallet.a`, and the
  transitive archive set);
- libsodium, OpenSSL, Boost, zlib, libiconv, libzmq, expat, unbound, Polyseed,
  utf8proc, and bc-ur archives;
- `include/wallet2_api.h` for the JNI adapter.

The app's own JNI adapter is compiled separately by `app/CMakeLists.txt`. This
directory does not contain application logic.

## 2. Pinned inputs

The build is intended to be reproducible. Treat every pin as a security input.

- `external-libs/monero` is a git submodule. The gitlink is the authoritative Monero
  commit. Current gitlink: `85f036ba7019cd36f08e8a38a27c37ea7b522618` (README:
  `v0.18.5.4`).
- `build.sh` pins dependency versions and SHA-256/SHA-1 hashes together. Never change
  a version without changing its hash and verifying the source.
- `Containerfile` pins the base image by digest and the Debian snapshot date.
- `Makefile` pins the base image digest, Debian snapshot, container engine, and
  `SOURCE_DATE_EPOCH`.
- `build.sh` pins the CMake archive, NDK revision, dependency versions, and their
  hashes.
- The ANONERO patch series under `patches/` is part of the Monero input. Patches must
  be applied to the official submodule; do not commit a locally modified Monero tree.

**Current checkout state:** the eight wallet-API patches, `apply.sh`, `README.md`, and
`LICENSE-LGPL-3.0` are checked in under `patches/`. They apply cleanly to the pinned
`85f036ba` commit. `external-libs/monero` is uninitialized locally and the native archives
build in CI; the local submodule is still uninitialized, so local native work needs the submodule checkout.

Seven patches are adapted from `monero_c` and are LGPL-3.0; keep the licence file with
them and do not relicense them as Apache-2.0. `0008-enforce-tls-for-https-daemon-address.patch`
is project-original and Apache-2.0.

## 3. Hard rules

- Do not run `make`, `docker`/`podman`, `cmake`, or `build.sh` locally. CI is the
  build authority.
- Do not edit the Monero submodule working tree. Add a patch file and extend
  `patches/apply.sh` when changing the series.
- Do not remove `external-libs/.clang-format`; its `DisableFormat: true` prevents a
  recursive clang-format run from rewriting the vendored Monero tree.
- Do not modify a pinned dependency version or hash to “make it build”.
- Do not add a new native dependency unless it is required by Monero or the JNI
  adapter, is pinned, is license-compatible, and has a reproducible source URL.
- Do not add a new native runtime or FFI layer beyond the C++/JNI adapter.
- Do not commit `arm64-v8a/`, `armeabi-v7a/`, `linux-x86_64/`, `include/`, `VERSION`,
  `*.rej`, or `*.orig`.
- Do not change ABIs without updating the app's `ndk.abiFilters`, `splits`, CMake
  targets, and CI artifact checks.

## 4. Build model

The intended CI flow:

1. `make -C external-libs patches` applies the pinned patch series and initializes
   nested submodules.
2. `make -C external-libs android` builds `arm64-v8a` and `armeabi-v7a` in the pinned
   container.
3. `external-libs/<abi>/` receives the static archives and `monero/` receives the
   wallet2 archives.
4. CI stages those trees as the `anonero-native-<run_id>` artifact.
5. The Android build links `app/src/main/cpp/anonero.cpp` against them.

The desktop target (`linux-x86_64`) produces `libanonero.so` for desktop use and is not
part of Android releases.

## 5. Patch series

If restoring or changing the patch series:

- Keep the Monero submodule at the official pinned commit. Use `git apply --index`
  as `apply.sh` does so `git describe` remains deterministic.
- Add patches in application order with stable names.
- Update `apply.sh` only if the patch layout changes; it currently applies every
  `*.patch` in lexical order.
- Keep the marker check (`struct CoinsInfo`) consistent with the first/last patch.
  `apply.sh` also checks the TLS marker in `src/wallet/api/wallet.cpp` so an existing
  seven-patch checkout receives `0008` instead of skipping it.
- Do not create local commits in the submodule.
- Document the upstream methods the patches add and why upstream cannot provide them.
- Verify the Containerfile guard (`CoinsInfo`, `viewOnlyBalance`) matches the patch
  result.
- Native build success is not proof that wallet behavior is correct; it only proves
  linkage.

## 6. Reproducibility and supply chain

- Keep `SOURCE_DATE_EPOCH`, `ZERO_AR_DATE`, `TZ=UTC`, and `LC_ALL=C.UTF-8`.
- Keep the base image digest and Debian snapshot pinned.
- Keep dependency hashes next to versions.
- Do not add a network fetch that is not hash-verified.
- Do not add a build step that reads the host environment, host toolchain, or wall
  clock.
- Do not add a cache or artifact that can be poisoned between build and signing.
- Treat a failure to verify a hash as fatal.

## 7. Static checks

Allowed:

```sh
bash -n external-libs/build.sh
bash -n external-libs/patches/apply.sh
git ls-tree HEAD external-libs/monero
git -C external-libs/monero status --short
grep -n "SHA256\|SHA1\|commit\|VERSION" external-libs/build.sh
```

Forbidden locally: `make`, `docker`, `podman`, `cmake`, `build.sh` execution, and
downloading dependencies in order to build or execute them. Official release and
checksum metadata may be consulted for version research; never execute a downloaded
artifact locally.

## 8. Checklist

- [ ] Submodule gitlink, patch series, and README agree.
- [ ] Every dependency version has a matching hash.
- [ ] Base image and snapshot remain pinned.
- [ ] Containerfile guards match the patched API.
- [ ] ABIs match the app and CI artifact checks.
- [ ] No local build artifact or patched submodule change is committed.
- [ ] No alternate native runtime, no unpinned source, no unverified download.
- [ ] Native success is not claimed without a CI artifact.
