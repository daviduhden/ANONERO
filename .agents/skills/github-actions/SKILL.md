---
name: github-actions
description: "Use when creating, auditing, or modifying ANONERO GitHub Actions workflows, test and build gates, native artifact handling, APK signing, checksum verification, or release publishing. Covers least privilege, pinned actions, and fail-closed release checks."
---

# GitHub Actions and release pipeline

## When to use

- Editing `.github/workflows/test.yml` or `release.yml`.
- Adding/removing a job, cache, artifact, permission, trigger, secret, or action.
- Auditing build/test/signing/release behavior.
- Diagnosing a CI gate without running it locally.

## Read first

- `AGENTS.md`
- `.github/workflows/AGENTS.md`
- `docs/architecture/dependencies.md`
- `.github/workflows/test.yml`
- `.github/workflows/release.yml`
- `scripts/check-jni-contract.py`
- `scripts/check-i18n.py`
- `external-libs/Makefile`
- `app/build.gradle.kts`

## Invariants

- Workflows are manual-only. `test.yml` keeps `workflow_call` only for the manual release
  workflow; there is no `push`, `pull_request`, `pull_request_target`, `schedule`, tag,
  `release`, `workflow_run`, or `repository_dispatch` trigger.
- Every job depends on the `authorize` job, which requires
  `github.actor == github.repository_owner` and
  `github.triggering_actor == github.repository_owner`. A non-owner dispatch must have no
  runnable jobs. A repository transfer to an organization changes the owner value; revisit
  the gate before transferring, because it fails closed.
- Default `permissions: contents: read`. Only `publish` gets `contents: write`.
- Signing and publishing use the protected `release` environment. No untrusted workflow
  may reach signing secrets.
- Pin every `uses:` action to a full commit SHA with a version comment.
- No Gradle cache in the publishing workflow. Cache poisoning before signing is a
  release risk.
- `native-language-policy` must continue to enforce the Kotlin/C++ source layout.
- Required gates stay required: `authorize`, `unit`, `lint`, `jni-contract`, `i18n`,
  `native-language-policy`, `native`, `assemble`, `instrumented` (via `test.yml`), then
  build, sign, publish.
- Release APKs must be unsigned before the `sign` job. No debug signing fallback.
- Verify before publish: six APKs, package IDs, versionName/versionCode, minSdk 29,
  non-debuggable, only arm64-v8a/armeabi-v7a, `libanonero.so`, signatures,
  `SHA256SUMS`.
- The manual `tag` input must match `versionName` and point at the built commit. Re-check
  in `publish`.
- Publishing requires `publish == true`; a build-only run must leave the signed artifacts
  available without creating a release.
- Do not overwrite existing release assets.
- Keep `persist-credentials: false`, timeouts, and concurrency groups unless a change
  is explicitly justified.
- Do not run workflows, `act`, `gh workflow run`, or project builds locally. `gh` is
  read-only for auditing.

## Procedure

1. **Read the whole job.** Identify inputs, outputs, artifacts, secrets, environment,
   and downstream needs.
2. **Classify the trust boundary.** PR code, third-party action, downloaded artifact,
   cache, secret, or published asset.
3. **Check permissions, triggers, and the owner gate.** Only `workflow_dispatch` and the
   guarded `workflow_call` may remain. Every job must need `authorize`; keep
   `contents: read` as the default. Never expose `release` to an untrusted run.
4. **Pin actions.** Use a full SHA and update the comment to the release version.
5. **Preserve fail-closed checks.** New steps should `exit 1` on a failed invariant,
   not warn.
6. **Check artifact paths.** Native layout is `external-libs/<abi>/` plus the
   generated `include/` and `VERSION` entries under `external-libs/`; the workflow
   accepts either a `native-out/` wrapper or the flat layout.
7. **Check the release matrix.** Three APKs per flavor, six total. Do not add an ABI
   without a native target.
8. **Update documentation.** If the contract changes, update
   `.github/workflows/AGENTS.md` and this skill.
9. **State evidence.** Only a real CI run can validate YAML, action behavior, and
   artifact contents. A local static parse is not a workflow run.

## Release procedure

1. Bump `versionName`/`versionCode` in `app/build.gradle.kts` as a coherent change.
2. Create a `v<versionName>` tag that points at the release commit.
3. Authorize the tag push separately; the tag is an input to the manual release workflow,
   not an automatic trigger.
4. Run the workflow manually as the repository owner (from the Actions UI or
   `gh workflow run release.yml -f tag=v1.2.3 -f publish=false|true`).
5. For a publish run, confirm the protected `release` environment approval and the four
   signing secrets. A build-only run must not create a release.
6. Confirm all six APKs and `SHA256SUMS` are present.
7. Confirm signatures with the workflow logs; do not manually re-sign or upload.
8. If a release fails, fix forward with a new commit/tag. Do not mutate an existing
   release’s assets.

## Static checks

```sh
python3 - <<'PY'
import pathlib
try:
    import yaml
except ImportError:
    raise SystemExit("PyYAML unavailable; inspect manually")
for p in pathlib.Path('.github/workflows').glob('*.yml'):
    yaml.safe_load(p.read_text())
    print("ok", p)
PY
grep -n "uses:\|permissions:\|environment:\|secrets\.\|pull_request_target" .github/workflows/*.yml
grep -nE '^  (push|pull_request|pull_request_target|schedule|release|workflow_run|repository_dispatch):' .github/workflows/*.yml
grep -n "authorize\|github.repository_owner\|triggering_actor" .github/workflows/*.yml
actionlint .github/workflows/*.yml
yamllint -c .yamllint .github/workflows/*.yml
zizmor --format plain .github/workflows
bash -n .github/scripts/*.sh
git diff --check
```

If PyYAML is unavailable, do not install it; inspect the YAML manually and say so.

### Instrumented orchestration

`ReactiveCircus/android-emulator-runner` executes the `script` input line by line. Do not
put loops or backslash continuations there. Keep the logic in
`.github/scripts/run-instrumented.sh` and call it with `bash` on one line. The script must:

- wait for `sys.boot_completed` and for the `package` and `activity` services;
- capture crash logcat, guest memory, and disk on failure;
- retry only instrumentation attach/zero-test failures while the guest is healthy;
- leave the XML and `test-result-exit-code.txt` validation as the final authority.

Hosted Linux runners have no `/dev/kvm`; the x86_64 emulator runs under software
emulation. Treat instrumented stability as an infrastructure decision (self-hosted KVM or
device farm), not as a reason to relax the test gate.

## Completion criteria

- [ ] Least privilege, pinned actions, no PR secret exposure.
- [ ] No automatic triggers; `workflow_dispatch` only, with guarded `workflow_call`.
- [ ] Every job depends on the owner `authorize` gate.
- [ ] No cache in publishing.
- [ ] All required gates intact.
- [ ] Six APKs and all invariants verified before publish.
- [ ] No debug signing, no asset overwrite.
- [ ] Documentation updated with manual run commands.
- [ ] Success is not claimed without a manual owner run.
