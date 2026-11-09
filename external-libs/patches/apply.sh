#!/usr/bin/env bash
#
# Copyright (c) 2026, David Uhden Collado
# Copyright (c) 2022-2026, r4v3r23
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

cd "$(dirname "$0")"

# `sudo make` runs this script as root, but the checkout and its .git metadata
# belong to the invoking user.  Drop back to that user so patching does not
# leave root-owned refs, logs, or working-tree files behind.
if [ "$(id -u)" -eq 0 ] && [ -n "${SUDO_UID:-}" ]; then
    exec setpriv --reuid="$SUDO_UID" --regid="${SUDO_GID:-$SUDO_UID}" \
        --init-groups "$0" "$@"
fi

ROOT=$(cd ../.. && pwd)
MONERO="$ROOT/external-libs/monero"
API="$MONERO/src/wallet/api/wallet2_api.h"
MARKER="struct CoinsInfo"

# Keep working even if the checkout was produced by an earlier root-owned run.
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0=safe.directory
export GIT_CONFIG_VALUE_0='*'

if [ ! -f "$MONERO/Makefile" ]; then
    cat >&2 <<'EOF'
Monero submodule is not initialised.

Run this from the repository root:
    git submodule update --init --recursive
EOF
    exit 1
fi

if grep -q "$MARKER" "$API"; then
    echo "Monero patches already applied; ensuring nested submodules are present."
else
    if [ -n "$(git -C "$MONERO" status --porcelain)" ]; then
        cat >&2 <<'EOF'
The Monero submodule has local changes, so the pinned patch series was not
applied.  Reset it first if those changes are not yours:
    git -C external-libs/monero reset --hard
    git -C external-libs/monero clean -fd
EOF
        exit 1
    fi

    echo "Applying ANONERO wallet-API patches to official Monero v0.18.5.4..."
    # `apply --index` keeps the submodule at the tagged v0.18.5.4 commit, so
    # `git describe`/VERSION stay deterministic; no local commits are created.
    for patch in "$PWD"/*.patch; do
        git -C "$MONERO" apply --index --3way "$patch"
    done
fi

# The patch series adds external/{bc-ur,polyseed,utf8proc}.  Initialise them
# (and Monero's existing nested submodules) at the exact pinned commits.
git -C "$MONERO" submodule update --init --recursive --depth 1

echo "Monero API ready: $(git -C "$MONERO" describe --tags --always 2>/dev/null || echo unknown)"
