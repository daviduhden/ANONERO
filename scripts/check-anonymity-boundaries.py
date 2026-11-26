#!/usr/bin/env python3
#
# Copyright (c) 2026, David Uhden Collado
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

"""Check selected source policy boundaries; no application or test execution."""

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/io/anonero"
failures = []
application = (JAVA / "AnonApplication.kt").read_text(encoding="utf-8")
logs = (JAVA / "ui/home/settings/Logs.kt").read_text(encoding="utf-8")
repository = (JAVA / "store/LogsDataStore.kt").read_text(encoding="utf-8")
service = (JAVA / "services/AnonNeroService.kt").read_text(encoding="utf-8")

if "Timber.plant(DebugTree())" in application:
    failures.append("An independent logcat tree bypasses persistent diagnostic policy")
if "defaultHandler?.uncaughtException(t, e)" in application:
    failures.append("Original uncaught exceptions must not reach the system log handler")
for field in (
    "MODEL",
    "MANUFACTURER",
    "BRAND",
    "DEVICE",
    "PRODUCT",
    "HARDWARE",
    "BOARD",
    "HOST",
    "FINGERPRINT",
):
    if f"Build.{field}" in logs:
        failures.append(f"Log export must not fingerprint hardware via Build.{field}")
if (
    "DiagnosticPolicy.sanitize" not in repository
    or "expectedGeneration != generation" not in repository
):
    failures.append("Diagnostic persistence requires sanitization and stale-session rejection")
if service.count(".setVisibility(NotificationCompat.VISIBILITY_SECRET)") != 2:
    failures.append("Both wallet notification builders require secret lock-screen visibility")
# New consumers must use profile-aware LwsConfig rather than legacy MAIN keys.
for source in JAVA.rglob("*.kt"):
    if source.name in {"Prefs.kt", "PrivacySettings.kt"}:
        continue
    content = source.read_text(encoding="utf-8")
    if ".submitTransaction(" in content:
        failures.append(
            f"Unverified signed-file submission is disabled: {source.relative_to(ROOT)}"
        )
    if "PREFS_LWS_" in content:
        failures.append(
            f"Raw MAIN LWS preference access outside profile boundary: {source.relative_to(ROOT)}"
        )

# Void JNI freeze/thaw calls cannot establish success on their own. The Kotlin
# wrapper must verify the refreshed native snapshot before reporting the mutation.
coins = (JAVA / "model/Coins.kt").read_text(encoding="utf-8")
for operation, verdict in (("freeze", "it.frozen"), ("thaw", "!it.frozen")):
    start = coins.index(f"fun {operation}(publicKey: String)")
    end = coins.index("\n    }", start)
    body = coins[start:end]
    if "refresh()" not in body or f"it.pub_key == publicKey && {verdict}" not in body:
        failures.append(f"Coins.{operation} requires a refreshed frozen-state postcondition")

backup = (JAVA / "util/backup/BackupHelper.kt").read_text(encoding="utf-8")
start = backup.index("private suspend fun createBackupLocked(")
end = backup.index("val manager =", start)
if (
    "check(!AnonConfig.viewOnly)" not in backup[start:end]
    or "if (WalletSession.isDecoy)" not in backup[start:end]
):
    failures.append(
        "Backup export must reject view-only/decoy contexts before auxiliary wallet access"
    )

# A non-OK restore handle must close before the next credential attempt.
start = backup.index("private fun bindRestoredWallet(")
retry = backup.index("opened = openRestoredWallet(manager, walletFile, pin)", start)
if (
    "if (opened != null && !closeRestoredWallet(manager, opened)) return null"
    not in backup[start:retry]
):
    failures.append("Legacy backup retry must confirm that the previous wallet closed")

if "wallet.setPassword(original) && wallet.store()" not in backup:
    failures.append("Backup rollback must confirm the credential change before storing")

seed_display = (JAVA / "ui/home/settings/SeedSettings.kt").read_text(encoding="utf-8")
if (
    "WalletProfileStore.passphraseHashKey(owner.profile)" not in seed_display
    or "WalletManager.instance?.managedWalletAt(walletFile) === wallet" not in seed_display
    or "owner.wallet.getSeed(enteredPassphrase)" not in seed_display
    or "activePassphraseHashKey()" in seed_display
):
    failures.append("Seed display must authenticate and read the same wallet/profile")

wallet_handler = (JAVA / "services/AnonWalletHandler.kt").read_text(encoding="utf-8")
network_settings = (JAVA / "ui/home/settings/NetworkSettings.kt").read_text(encoding="utf-8")
reapply = wallet_handler.split("private suspend fun reapplyCurrentLightWallet(", 1)[-1].split(
    "internal suspend fun connectRpcNode(", 1
)[0]
required_lws_order = (
    "manager.managedWalletAt(WalletProfileStore.walletFile(context, profile)) !== wallet",
    "check(wallet.isLightWallet)",
    "LwsConfig.load(prefs, profile)",
    "applyProxy(wallet, profile)",
    "updateDaemon(wallet, node, lightWallet = true)",
    "wallet.loginToUntrustedLws()",
)
lws_positions = [reapply.find(marker) for marker in required_lws_order]
if (
    -1 in lws_positions
    or lws_positions != sorted(lws_positions)
    or "walletHandler.reapplyLightWallet(wallet, profile)" not in network_settings
    or "val wallet = manager.wallet" in reapply
):
    failures.append("LWS reapply must keep the initiating wallet and profile through login")

wipe = wallet_handler[wallet_handler.index("internal fun wipe(") :]
if (
    "WalletProfileStore.passphraseHashKey(profile)" not in wipe
    or "manager.managedWalletAt(walletFile) === wallet" not in wipe
    or "manager.close(wallet) {" not in wipe
    or "WalletProfileStore.removeProfile(context, prefs, profile, manager)" not in wipe
    or "WalletProfileStore.removeProfile(context, prefs, WalletSession.profile)" in wipe
):
    failures.append("Wipe must authenticate and delete the captured wallet/profile")

decoy = (JAVA / "ui/home/settings/DecoySettings.kt").read_text(encoding="utf-8")
create_decoy = decoy[decoy.index("private fun createDecoy(") :]
required_order = (
    "tryProfileMutation(",
    "isDecoyTargetEmpty(context, prefs)",
    "WalletKey.store(",
    "manager.createWalletUnmanaged(",
    "manager.closeUnmanaged(wallet)",
    "if (!committed",
    "creationComplete = true",
)
positions = [create_decoy.find(step) for step in required_order]
if -1 in positions or positions != sorted(positions):
    failures.append(
        "Decoy provisioning must guard its empty target through close and durable credential publication"
    )

lock_screen = (JAVA / "ui/home/LockScreen.kt").read_text(encoding="utf-8")
settings = (JAVA / "ui/home/settings/Settings.kt").read_text(encoding="utf-8")
if (
    "WalletPinSession.verify(it, profile, prefs, pin)" not in lock_screen
    or "onVerified(verified, pin, shortCut)" not in lock_screen
    or "owner.current()" not in settings
    or "pinHashKey(owner.profile)" not in settings
    or "setPassword(" in settings
    or "activePinHashKey(" in lock_screen + settings
):
    failures.append(
        "PIN actions must retain the authenticated wallet/profile through credential changes"
    )

scanner = (JAVA / "ui/components/scanner/QrCamera.kt").read_text(encoding="utf-8")
if (
    "unbindAll()" in scanner
    or "provider?.unbind(preview, analysis)" not in scanner
    or "main.execute { if (active) deliverUr(result) }" not in scanner
    or "main.execute { if (active) deliverText(text) }" not in scanner
):
    failures.append("Camera callbacks and teardown must be scoped to the active preview")

ur_scanner = (JAVA / "ui/home/spend/qr/QRScanner.kt").read_text(encoding="utf-8")
if "it.ur.type" in ur_scanner:
    failures.append("The scanner must handle a terminal UR decode failure without a payload")

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
app = manifest.find("application")
if app is None or app.get(android + "allowBackup") != "false":
    failures.append("Android automatic backup must remain disabled")
for component in manifest.findall("application/service") + manifest.findall("application/provider"):
    if component.get(android + "exported") != "false":
        failures.append("Wallet services and file providers must not be exported")
if failures:
    raise SystemExit("\n".join(failures))
print("Selected anonymity source boundaries passed (not dynamic or security proof)")
