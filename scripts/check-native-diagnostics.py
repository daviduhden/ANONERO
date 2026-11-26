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

"""Static guard against uncontrolled JNI diagnostics; does not execute wallet code."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/cpp/anonero.cpp"
text = SOURCE.read_text(encoding="utf-8")
failures = []

# Adapter diagnostics must be fixed literals. Upstream exception text can contain
# addresses, amounts, paths or key fragments; it is not a redaction boundary.
# Ignore comments and macro definitions when inspecting call sites. Scan across
# lines too, so formatting a dynamic call over multiple lines does not evade it.
code = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.DOTALL)
code = re.sub(r"^#define[^\n]*", "", code, flags=re.MULTILINE)
for call in re.finditer(r"\bLOG([VDIWE])\s*\((.*?)\)\s*;", code, flags=re.DOTALL):
    if call[1] in "VDI":
        failures.append("Routine native lifecycle diagnostics are forbidden")
    if not re.fullmatch(r'\s*"(?:[^"\\]|\\.)*"\s*', call[2]):
        failures.append("Dynamic native diagnostic is forbidden")
if re.search(r"^#define\s+LOG[VDI]\b", text, flags=re.MULTILINE):
    failures.append("Native diagnostics must expose warning/error macros only")
for boundary in ("JNI_OnLoad(", "Java_io_anonero_model_WalletManager_initLogger("):
    start = text.index(boundary)
    end = text.index("\n}\n", start)
    if "Monero::WalletManagerFactory::setLogLevel(-1);" not in text[start:end]:
        failures.append(f"{boundary}: native logging must initialize silently")

if re.search(r"\b\w+\s*\.\s*what\s*\(", text):
    failures.append("JNI adapter must not disclose uncontrolled exception text")

for operation in ("importKeyImages", "importOutputs", "submitTransaction", "signAndExportJ"):
    start = text.index(f"Java_io_anonero_model_Wallet_{operation}(")
    end = text.index("\n}\n", start)
    if "errorString()" in text[start:end]:
        failures.append(f"{operation}: raw wallet2 error crosses the operation result boundary")

# Snapshot conversion must fail atomically. Clearing a JVM error or skipping a
# failed record makes a partial output/history list look successful to Kotlin.
for function in (
    "newWalletStatusInstance",
    "newCoinsInfo",
    "coins_cpp2java",
    "newTransferInstance",
    "newTransferList",
    "newTransactionInfo",
    "cpp2java",
):
    start = text.index(f"jobject {function}(")
    end = text.index("\n}\n", start)
    body = text[start:end]
    if "ExceptionClear" in body or re.search(r"\bcontinue\s*;", body):
        failures.append(f"{function}: snapshot conversion must preserve errors and all records")
for model in ("Coins", "TransactionHistory"):
    start = text.index(f"Java_io_anonero_model_{model}_refreshJ(")
    end = text.index("\n}\n", start)
    body = text[start:end]
    if "catch (...)" not in body or "throwSnapshotFailure(env)" not in body:
        failures.append(
            f"{model}: refresh must contain native exceptions and reject null snapshots"
        )

# Collections can allocate before wallet2 itself is reached. Review the
# complete export boundary, not just the wallet2 call.
for operation in (
    "createTransactionJ",
    "createTransactionMultDestJ",
    "estimateTransactionFeeJ",
    "createSweepTransaction",
    "signMessageJ",
    "verifySignedMessageJ",
):
    start = text.index(f"Java_io_anonero_model_Wallet_{operation}(")
    end = text.index("\n}\n", start)
    body = text[start:end]
    preamble, _, implementation = body.partition("try {")
    if "java2cpp_" in preamble or "catch (...)" not in implementation:
        failures.append(f"{operation}: JNI input conversion must stay inside the native catch")

for converter in ("java2cpp_long", "java2cpp_bytes", "java2cpp_string_array", "java2cpp_set"):
    start = text.index(f"{converter}(")
    end = text.index("\n}\n", start)
    body = text[start:end]
    if "ExceptionClear" in body:
        failures.append(f"{converter}: preserve pending JNI input errors")
    if converter == "java2cpp_set" and "IsInstanceOf(element.get()," not in body:
        failures.append("java2cpp_set: check runtime element type before casting to jstring")

# A failed Java string acquisition can leave a pending OOM. Further string
# acquisitions and fallback allocations are invalid until the JVM handles it.
utf_helper = code.split("class ScopedUtfChars", 1)[1].split("};", 1)[0]
if not re.search(
    r"env->ExceptionCheck\(\)\s*\?\s*nullptr\s*:\s*env->GetStringUTFChars\(",
    utf_helper,
):
    failures.append("ScopedUtfChars: guard chained acquisitions against pending JVM errors")
if len(re.findall(r"->GetStringUTFChars\s*\(", code)) != 1:
    failures.append("GetStringUTFChars: use ScopedUtfChars for string acquisition")
if re.search(
    r"if\s*\([^;{}]*\.get\(\)\s*==\s*nullptr[^;{}]*?\)\s*\{?\s*"
    r"return\s+env->NewStringUTF\(",
    code,
):
    failures.append("UTF acquisition failure must preserve the JVM error before returning text")

# These Boolean exports gate transport setup and the lock screen. Both argument
# conversion and wallet2 failures must stay inside the export's catch boundary.
for owner, operation, call in (
    ("WalletManager", "verifyWalletPassword", "->verifyWalletPassword("),
    ("Wallet", "setPassword", "->setPassword("),
    ("Wallet", "store", "->store("),
    ("WalletManager", "setProxyJ", "->setProxy("),
    ("Wallet", "setProxyJ", "->setProxy("),
    ("Wallet", "initJ", "->init("),
    ("Wallet", "lightWalletLogin", "->lightWalletLogin("),
    ("Wallet", "startBackgroundSync", "->startBackgroundSync("),
    ("Wallet", "stopBackgroundSync", "->stopBackgroundSync("),
):
    start = code.index(f"Java_io_anonero_model_{owner}_{operation}(")
    end = code.index("\n}\n", start)
    before_try, _, implementation = code[start:end].partition("try {")
    native_call, _, fallback = implementation.partition("catch (...)")
    if (
        call in before_try
        or "std::string(" in before_try
        or call not in native_call
        or "return JNI_FALSE;" not in fallback
    ):
        failures.append(f"{owner}.{operation}: native call must fail closed inside a catch")
    if operation == "initJ" and "upper_transaction_size_limit < 0" not in before_try:
        failures.append("Wallet.initJ: check size limit before converting to unsigned")

# Scalar status reads and the status/error snapshot may be consulted during
# recovery. A failed read must never grant access or report a ready wallet.
for owner, operation, call, fallback in (
    ("Wallet", "getStatusJ", "->status(", "Monero::Wallet::Status_Critical"),
    ("Wallet", "getConnectionStatusJ", "->connected(", "return 0;"),
    (
        "Wallet",
        "statusWithErrorString",
        "->statusWithErrorString(",
        "Monero::Wallet::Status_Critical",
    ),
    (
        "PendingTransaction",
        "getStatusJ",
        "->status(",
        "Monero::PendingTransaction::Status_Critical",
    ),
    (
        "UnsignedTransaction",
        "getStatusJ",
        "->status(",
        "Monero::UnsignedTransaction::Status_Critical",
    ),
    ("WalletManager", "queryWalletDeviceJ", "->queryWalletDevice(", "return -1;"),
):
    start = code.index(f"Java_io_anonero_model_{owner}_{operation}(")
    end = code.index("\n}\n", start)
    before_try, _, implementation = code[start:end].partition("try {")
    native, _, failure = implementation.partition("catch (...)")
    if (
        call in before_try
        or "std::string" in before_try
        or call not in native
        or fallback not in failure
    ):
        failures.append(f"{owner}.{operation}: preserve a failing status on native exception")

# Seed and key exports also allocate before or during the wallet2 call. A
# native failure must become a fixed JVM error, not a mnemonic fallback.
for operation, calls in (
    ("getSeed", ("->getPolyseed(", "->seed(")),
    ("getLegacySeed", ("->seed(",)),
    ("isPolyseedSupported", ("->getPolyseed(",)),
    ("getSeedLanguage", ("->getSeedLanguage(",)),
    ("setSeedLanguage", ("->setSeedLanguage(",)),
    ("getSecretViewKey", ("->secretViewKey(",)),
    ("getSecretSpendKey", ("->secretSpendKey(",)),
):
    start = code.index(f"Java_io_anonero_model_Wallet_{operation}(")
    end = code.index("\n}\n", start)
    preamble, _, implementation = code[start:end].partition("try {")
    native_calls, _, failure = implementation.partition("catch (...)")
    if (
        "std::string" in preamble
        or "ScopedUtfChars" in preamble
        or any(call not in native_calls or call in preamble or call in failure for call in calls)
        or "throwWalletSeedFailure(env);" not in failure
    ):
        failures.append(f"Wallet.{operation}: seed and key work must remain in the native catch")
    if operation == "getSeed" and not re.search(
        r"if\s*\(\s*!wallet->getPolyseed\(seed, offset\)\s*\)\s*\{\s*seed = wallet->seed\(offset\);",
        native_calls,
    ):
        failures.append("Wallet.getSeed: legacy fallback requires a normal false Polyseed result")

# Transaction review reads wallet2 summaries and totals before publishing a
# signable or broadcastable model. Allocation failures must propagate as Java errors.
for owner, operations in (
    (
        "PendingTransaction",
        {
            "getErrorString": "errorString",
            "getAmount": "amount",
            "getDust": "dust",
            "getFee": "fee",
            "getFirstTxIdJ": "txid",
            "getTxCount": "txCount",
        },
    ),
    (
        "UnsignedTransaction",
        {
            "getErrorString": "errorString",
            "getAddress": "recipientAddress",
            "getAmount": "amount",
            "getFee": "fee",
            "getFirstTxIdJ": "paymentId",
            "getTxCount": "txCount",
            "confirmationMessageJ": "confirmationMessage",
        },
    ),
):
    for operation, method in operations.items():
        start = code.index(f"Java_io_anonero_model_{owner}_{operation}(")
        end = code.index("\n}\n", start)
        preamble, _, implementation = code[start:end].partition("try {")
        native, _, failure = implementation.partition("catch (...)")
        call = f"->{method}("
        if (
            call in preamble
            or call not in native
            or "throwTransactionReviewFailure(env);" not in failure
        ):
            failures.append(
                f"{owner}.{operation}: review read must fail through the Java error path"
            )

# Signed jlong amounts must not wrap on the way out of wallet2. Count model
# constructor fields separately because a transaction snapshot includes its fee.
for signature, expected in (
    ("jobject newCoinsInfo(", 1),
    ("jobject newTransferInstance(", 1),
    ("jobject newTransactionInfo(", 2),
    *(
        (f"Java_io_anonero_model_Wallet_{name}(", 1)
        for name in (
            "getBalance",
            "getBalanceAll",
            "getUnlockedBalance",
            "getUnlockedBalanceAll",
            "viewOnlyBalance",
            "viewOnlyBalanceFor",
            "estimateTransactionFeeJ",
        )
    ),
    *(
        (f"Java_io_anonero_model_PendingTransaction_{name}(", 1)
        for name in ("getAmount", "getFee", "getDust")
    ),
    *(
        (f"Java_io_anonero_model_UnsignedTransaction_{name}(", 1)
        for name in ("getAmount", "getFee")
    ),
):
    start = code.index(signature)
    if code[start : code.index("\n}\n", start)].count("checkedAtomicAmount(") != expected:
        failures.append(f"{signature}: check all monetary conversions to Kotlin Long")

# wallet2 does not serialize listener pointer swaps against callback delivery.
listener_start = code.index("Java_io_anonero_model_Wallet_setListenerJ(")
listener_end = code.index("\n}\n", listener_start)
listener_setup = code[listener_start:listener_end]
if (
    listener_setup.count("wallet->setListener(") != 1
    or "wallet->setListener(listener.get());" not in listener_setup
    or "delete installed" in listener_setup
    or "installed->replaceJavaListener(env, javaListener)" not in listener_setup
):
    failures.append("Wallet.setListenerJ: replace only the installed adapter's Java target")

close_start = code.index("Java_io_anonero_model_WalletManager_closeJ(")
close_end = code.index("\n}\n", close_start)
lookups, _, close_result = code[close_start:close_end].partition("->closeWallet(")
if (
    'getHandleField(env, walletInstance, "listenerHandle")' not in lookups
    or "delete listener;" in lookups
    or close_result.count("return JNI_FALSE;") != 2
    or close_result.count("env->SetLongField(") != 2
    or "catch (...)" not in close_result
    or close_result.find("env->SetLongField(") < close_result.find("catch (...)")
    or close_result.find("delete listener;") < close_result.find("env->SetLongField(")
):
    failures.append(
        "WalletManager.closeJ: clear fields and free the listener after successful close"
    )

if failures:
    raise SystemExit("\n".join(failures))
print("Native diagnostic static policy passed (not runtime or security proof)")
