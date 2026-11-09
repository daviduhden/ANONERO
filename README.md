# ANONERO

Privacy and security focused Monero wallet with advanced features in a slick UX.

### QUICKSTART

- Source code: <https://github.com/daviduhden/ANONERO>

### REQUIREMENTS

ANONERO requires **Android 10 (API 29) or later**. Releases built from this
source cannot be installed or updated on Android 9 or earlier; upgrading a
device to Android 10 or later is required before installing a new ANONERO build.

### DISCLAIMER

Be sure to back up your wallet recovery seed AND passphrase.

ANON enforces passphrase encryption on all seeds!

We are NOT responsible for lost or stolen funds.

### MAIN FEATURES

- Monero only
- Mandatory proxy
- No mandatory 3rd-party services (optional self-hosted LWS is opt-in)
- Polyseed mnemonic
- Passphrase encryption
- No subaddress reuse
- Encrypted backups
- UTXO management (freeze/thaw, coin control)
- Secure view-key syncing
- Airgapped transactions
- Multiple Monero accounts
- Pay to many
- Sign / verify messages
- Optional light-wallet server (LWS) over Tor
- Stealth mode (redacted notifications, hidden amounts, auto-lock)
- Separate decoy wallet with an optional duress PIN
- English, Spanish (Spain), Russian and Simplified Chinese UI with an in-app language selector

### HOW TO BUILD

ANONERO builds from GitHub against the **official Monero source tree**. Monero is
a pinned git submodule, not a fork:

- Repository: <https://github.com/monero-project/monero>
- Release: `v0.18.5.4`
- Commit: `85f036ba7019cd36f08e8a38a27c37ea7b522618`

Upstream Monero does not expose every wallet-API method ANONERO uses (Polyseed
with a passphrase, coin control / `CoinsInfo`, and the view-only key-image
helpers). A pinned compatibility patch series is applied automatically on top of
the official release.

#### 1. Clone the repository

Clone with submodules so the exact official Monero commit is checked out:

```sh
git clone --recurse-submodules https://github.com/daviduhden/ANONERO.git
cd ANONERO
```

If you already cloned without submodules:

```sh
git submodule update --init --recursive
```

#### 2. Build the native libraries

`sudo` is only needed because the build drives docker (or podman):

```sh
cd external-libs
sudo make android     # arm64-v8a + armeabi-v7a, for the APK
sudo make linux       # linux-x86_64, for the desktop build
sudo make             # all three
```

The first run applies the pinned patch series to `external-libs/monero`,
initialises the nested submodules it adds, and downloads the pinned dependency
sources. To run just the patch step:

```sh
cd external-libs
make patches
```

Builds run in a container, so the only host requirements are git, docker (or
`CONTAINER=podman`) and free disk. The first build downloads several hundred
megabytes. Parallelism defaults to `nproc`; override with `make NPROC=4`.

The native build is reproducible: from the pinned base image, SHA-pinned
sources, official Monero commit, and pinned patch series, a build of a given
commit produces byte-identical `.a`/`.so` files.

#### 3. Build the APK

Open the repository in Android Studio and build the `anon` or `nero` flavor.
The Android build consumes the native libraries produced in
`external-libs/arm64-v8a` and `external-libs/armeabi-v7a`.
