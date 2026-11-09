---
name: static-analysis
description: "Use when asked to audit, review, or validate ANONERO without compiling or running tests: reference and symbol checks, JNI parity, localization, manifest and dependency review, secret scans, compatibility analysis, or diff review."
---

# Static analysis and audits

## When to use

- A review or audit request where builds/tests are prohibited.
- Checking references, JNI contract, i18n, manifest, dependencies, or formats.
- Investigating a suspected regression, leak, or dead code path.
- Producing an evidence-backed findings report.

Do not use to claim runtime success, fund safety, or cryptographic proof.

## Hard constraints

- Never run `./gradlew`, `make`, `cmake`, `docker`/`podman`, `adb`, an emulator, or
  any project build/test.
- Never install dependencies to run a check.
- Never exfiltrate source, logs, keys, or wallet data.
- Allowed: reading, `git`, `grep`/`rg`, `find`, `file`, `python3` static scripts,
  `python3 -m py_compile scripts/*.py`, XML/TOML/JSON parsing with the standard
  library, `bash -n`, `git diff --check`.
- Optional YAML parsing only if PyYAML is already installed. If not, inspect
  manually and say so.

## Read first

- `AGENTS.md`
- `docs/architecture/repository-state.md`
- `docs/architecture/dependencies.md`
- `scripts/check-jni-contract.py`
- `scripts/check-i18n.py`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `.github/workflows/test.yml`

## Audit procedure

1. **Define scope.** List the directories, commits, and questions. Record the exact
   `HEAD` and dirty state.
2. **Build an inventory.** Files, languages, entry points, persistence, network,
   native, secrets, CI.
3. **Establish the baseline.** Run only allowed static checks:
   - `python3 scripts/check-jni-contract.py`
   - `python3 scripts/check-i18n.py`
   - `git diff --check`
   - manifest/resource/dependency parsing.
4. **Trace invariants.** Use the root security invariants and the relevant skill as
   the checklist. For each, find the enforcing code or state clearly that it is
   absent.
5. **Search for high-risk patterns.**
   - secrets in logs, prefs, intents, clipboard, notifications, tests;
   - cleartext/network clients outside wallet2;
   - `setTrustedDaemon(true)` in LWS paths;
   - `Double`/`getAmountFromDouble` for new amounts;
   - unchecked JNI strings/arrays/handles;
   - profile cross-reads;
   - `allowBackup`, exported components, new permissions;
   - disabled security checks in workflows.
6. **Check compatibility.** Backup versions, legacy PIN, `.premigrate`, JNI
   signatures, Monero pin/patches, minSdk, ABIs.
7. **Verify references.** Every path, symbol, resource, and command cited in a report
   must exist. Use `rg -n` and file existence checks.
8. **Classify evidence and severity.** Separate verified static, inferred, and
   unverified. Do not inflate confidence.
9. **Write actionable findings.** Each finding: file:line, what is wrong, impact,
   confidence, recommended fix, and the CI check that would confirm it.
10. **State limitations.** No runtime, device, network, or side-channel proof.

## Findings template

```
Finding: <short title>
Severity: critical | high | medium | low | informational
Confidence: high | medium | low
Evidence: <file:line> <quoted fact>
Impact: <what can go wrong, for whom>
Fix: <specific change>
Verification: <CI job / test / device check still required>
```

## Useful static checks

```sh
python3 scripts/check-jni-contract.py
python3 scripts/check-i18n.py
python3 - <<'PY'
for p in ("scripts/check-jni-contract.py", "scripts/check-i18n.py"):
    compile(open(p, encoding="utf-8").read(), p, "exec")
print("script syntax ok")
PY
bash -n external-libs/build.sh external-libs/patches/apply.sh
git diff --check
git status --short
find app/src -name '*.java'
grep -rn "usesCleartextTraffic\|allowBackup\|exported=" app/src/main/AndroidManifest.xml
grep -rn "getAmountFromDouble\|toDouble()" app/src/main/java/io/anonero
grep -rn "setTrustedDaemon" app/src/main/java/io/anonero
grep -rn "getSecretViewKey\|getSecretSpendKey\|getSeed" app/src/main/java/io/anonero
```

## Completion criteria

- [ ] Scope, HEAD, and dirty state recorded.
- [ ] Every finding has file:line evidence and a confidence level.
- [ ] Runtime/cryptographic claims are marked unverified.
- [ ] References and paths are real.
- [ ] No build/test was run.
- [ ] Limitations and recommended CI verification are explicit.
