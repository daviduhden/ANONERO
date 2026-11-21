#!/usr/bin/env python3
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
        failures.append(f"{model}: refresh must contain native exceptions and reject null snapshots")

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

if failures:
    raise SystemExit("\n".join(failures))
print("Native diagnostic static policy passed (not runtime or security proof)")
