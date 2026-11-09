# ANONERO skills index

Skills are procedures, not substitutes for `AGENTS.md`. Read the root `AGENTS.md`
first, then the closest directory `AGENTS.md`, then the skill. If a skill conflicts
with a security invariant in `AGENTS.md`, the invariant wins.

**Git authorization:** no skill authorizes a commit, push, tag, or publication.
`AGENTS.md` §8.1 requires prior, explicit human authorization for each operation.

## Available skills

| Skill | Purpose | Location | Use when |
| --- | --- | --- | --- |
| `android-kotlin` | Android/Kotlin architecture, state, coroutines, services, persistence | `.agents/skills/android-kotlin/SKILL.md` | Changing ViewModels, Flow, Koin, lifecycle, wallet-state plumbing, or app services |
| `jetpack-compose` | Compose screens, navigation, theming, accessibility, localization | `.agents/skills/jetpack-compose/SKILL.md` | Changing UI, routes, components, text resources, or accessibility |
| `native-jni` | C++ JNI adapter, handles, callbacks, symbol contract | `.agents/skills/native-jni/SKILL.md` | Changing `external` declarations, JNI exports, native memory, or `app/CMakeLists.txt` |
| `monero-wallet` | Wallet2 behavior: create/restore, accounts, UTXOs, transactions, signing | `.agents/skills/monero-wallet/SKILL.md` | Changing wallet operations, fees, coin control, sync, or transaction review |
| `cryptographic-security` | PIN, Keystore, wallet secret, backups, randomness, secret handling | `.agents/skills/cryptographic-security/SKILL.md` | Reviewing or changing any cryptographic boundary or secret lifecycle |
| `tor-networking` | Embedded Tor, SOCKS/manual proxy, fail-closed networking | `.agents/skills/tor-networking/SKILL.md` | Changing connectivity, proxy application, Tor lifecycle, or network privacy |
| `monero-lws` | Optional LWS configuration, login, trust, sync, UI | `.agents/skills/monero-lws/SKILL.md` | Changing LWS behavior or reviewing view-key/Tor/trust rules |
| `wallet-privacy` | Stealth mode, decoy wallet, duress PIN, lock, wipe, notifications | `.agents/skills/wallet-privacy/SKILL.md` | Changing privacy features or reviewing profile isolation |
| `github-actions` | CI/CD, build gates, signing, artifacts, releases | `.agents/skills/github-actions/SKILL.md` | Editing workflows or auditing build/sign/publish behavior |
| `static-analysis` | Audits without building or running tests | `.agents/skills/static-analysis/SKILL.md` | Reviewing references, JNI parity, i18n, manifest, dependencies, or diffs |
| `compatibility-migrations` | Persisted formats, legacy paths, Monero/toolchain upgrades | `.agents/skills/compatibility-migrations/SKILL.md` | Changing backups, PIN/Keystore migration, preferences, pins, SDK/AGP/Gradle |

## Selection shortcuts

- UI only -> `jetpack-compose`.
- UI plus state -> `jetpack-compose` + `android-kotlin`.
- Wallet operation -> `monero-wallet`; add `native-jni` if the boundary changes.
- Key, PIN, backup encryption, or secret handling -> `cryptographic-security`; add
  `compatibility-migrations` if an old format must keep working.
- Network or LWS -> `tor-networking`; add `monero-lws` for LWS-specific rules.
- Decoy, duress, stealth, lock, or wipe -> `wallet-privacy`.
- CI, signing, or release -> `github-actions`.
- “Review/audit only, do not build” -> `static-analysis`.
- Old data or pinned dependency upgrade -> `compatibility-migrations`.

## Deliberate omissions

- **`jni-ffi` is covered by `native-jni`.** The boundary is C++/JNI; there is one
  procedure for symbol parity, handle lifetime, callbacks, and native memory.
- **`code-migration` is replaced by `compatibility-migrations`.** The Java-to-Kotlin
  migration is complete (no `.java` files), and C++ is the intentional native
  adapter language. The remaining migration work is persisted formats, legacy
  compatibility, Monero patches, and toolchain upgrades.
- **No per-subsystem skills for `AppModule.kt`, `Prefs.kt`, or `WalletState.kt`.** They
  share the `android-kotlin` procedure and do not justify separate files.
- **No `SKILLS.md` substitute.** Each `SKILL.md` is the discoverable unit; this file is
  only an index.

## Directory instruction files

| File | Scope |
| --- | --- |
| `AGENTS.md` | Repository-wide contract |
| `app/AGENTS.md` | Android module |
| `app/src/main/cpp/AGENTS.md` | C++ JNI adapter |
| `external-libs/AGENTS.md` | Pinned native build and Monero patches |
| `.github/workflows/AGENTS.md` | CI, signing, release |

## Maintenance

When adding a skill:

1. Create `.agents/skills/<lowercase-hyphen-name>/SKILL.md`.
2. Add YAML frontmatter with `name` and a trigger-oriented `description`.
3. Include when to use, read-first paths, invariants, procedure, static checks, and
   completion criteria.
4. Add it to this table and remove any now-redundant skill.
5. Verify every referenced path exists.

When changing architecture, update the affected skill and this index in the same
change. Do not leave a skill describing a subsystem that no longer exists.
