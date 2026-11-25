---
name: github-actions
description: "Use when creating, auditing, or modifying ANONERO GitHub Actions workflows, test and build gates, native artifact handling, APK signing, checksum verification, or release publishing. Covers least privilege, pinned actions, and fail-closed release checks."
---

# GitHub Actions and release pipeline

## When to use

- Editing `.github/workflows/test.yml`, `release.yml` or `agent-knowledge-validation.yml`.
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

- All workflows use only `workflow_dispatch`; callable and automatic triggers are
  forbidden. Release embeds the required Tests jobs at the immutable dispatched main SHA and
  disabled Gradle caches, then aggregates all results and its same-run native artifact.
- Every job depends on the `authorize` job, which requires
  `github.actor == github.repository_owner` and
  `github.triggering_actor == github.repository_owner`. A non-owner dispatch must have no
  runnable jobs. A repository transfer to an organization changes the owner value; revisit
  the gate before transferring, because it fails closed.
- Default `permissions: contents: read`. Only `publish` gets `contents: write`.
- The owner dispatches from `main`. The tag must resolve to the dispatched SHA.
- Require the instrumented job before using either production signing key. An unsigned
  diagnostic run may omit instrumentation.
- Sign ANON in `android-anon-release` and NERO in `android-nero-release`. Each job
  downloads just its unsigned APKs, without checking out or executing project code.
  The separate `release` environment approves publication and carries no signing keys.
- Pin every `uses:` action to a full commit SHA with a version comment.
- No Gradle cache in the publishing workflow. Cache poisoning before signing is a
  release risk.
- `native-language-policy` must continue to enforce the Kotlin/C++ source layout.
- Required gates stay required: `authorize`, `unit`, `lint`, `jni-contract`, `i18n`,
  `native-language-policy`, `native`, `assemble`, `instrumented` (via `test.yml`), then
  build, app-specific signing, prepare, and optional publish.
- Release APKs must be unsigned when uploaded by `build`. No debug signing fallback.
- Verify before publish: six versioned APKs and two canonical copies, package IDs, versionName/versionCode, minSdk 29,
  non-debuggable, native ABI inventory, 16 KiB arm64 ELF and ZIP alignment, v2/v3
  signatures pinned to one distinct certificate and public key per app, and `SHA256SUMS`.
- The manual `tag` input must match `versionName` and point at the built commit. Re-check
  in `publish`.
- Publishing requires `publish == true`; a build-only run must leave the signed artifacts
  available without creating a release.
- Do not overwrite existing release assets.
- Keep `persist-credentials: false`, timeouts, and concurrency groups unless a change
  is explicitly justified.
- Do not run workflows, `act`, `gh workflow run`, or project builds locally. A
  maintainer may explicitly authorize remote configuration or a workflow dispatch.

## Procedure

1. **Read the whole job.** Identify inputs, outputs, artifacts, secrets, environment,
   and downstream needs.
2. **Classify the trust boundary.** PR code, third-party action, downloaded artifact,
   cache, secret, or published asset.
3. **Check permissions, triggers, and the owner gate.** Only `workflow_dispatch` may remain; no callable entry point is allowed. Every job must need `authorize`; keep
   `contents: read` as the default. Never expose signing or publishing environments to an untrusted ref.
4. **Pin actions.** Use a full SHA and update the comment to the release version.
5. **Preserve fail-closed checks.** New steps should `exit 1` on a failed invariant,
   not warn.
6. **Check artifact paths.** Native layout is `external-libs/<abi>/` plus the
   generated `include/` and `VERSION` entries under `external-libs/`; the workflow
   accepts either a `native-out/` wrapper or the flat layout.
7. **Check the release matrix.** Three versioned APKs per flavor, plus one canonical universal copy per flavor. Do not add an ABI
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
4. Dispatch from `main` when its SHA matches the release tag. Leave `publish=false`
   for verification and set `publish=true` only when authorizing a release.
5. Confirm each signing environment has its own secrets and public pin. Verify the
   source before approving each deployment. A publish run also requires approval
   of the separate `release` environment and successful instrumentation.
6. Confirm all eight APK files and `SHA256SUMS` are present.
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
- build the app and AndroidTest APKs before the emulator starts, and keep the connected
  Gradle invocation at `--max-workers=2` so host-side dexing cannot starve the guest;
- capture crash/system/main/events logcat, guest memory, and disk on failure;
- retry only an explicitly reported attachment failure while the guest is healthy,
  preserving each flavor's Gradle exit status;
- leave the XML and `test-result-exit-code.txt` validation as the final authority.

The emulator action needs `ANDROID_I_WANT_MY_TCG=yes` when it selects
`-accel off`. Keep that opt-in on the runner step in Tests and synchronize Release.
Inspect device availability and current-user permissions separately on hosted Linux.
Prepare KVM access only for the job user when the device exists. If absent, the action
uses bounded software emulation. Its launch success does not prove boot or test success.
Use the per-flavor XML and exit-code records to check both outcomes.

Audit successful runs as well as failed runs. Record project diagnostics separately
from dependency and hosted-runner advisories, and use reported artifact counts with
their exact run and commit. A cancelled boot before the deadline cannot establish a
timeout.

When changing a compiler or API selector to resolve a warning, validate the
effective compiler and platform in the build before accepting artifacts. For
OpenSSL use its generated config and keep both API 29 Android targets.

## Completion criteria

- [ ] Least privilege, pinned actions, no PR secret exposure.
- [ ] `workflow_dispatch` only; no callable or automatic triggers.
- [ ] Every job depends on the owner `authorize` gate.
- [ ] No cache in publishing.
- [ ] All required gates intact.
- [ ] All eight APK files and invariants verified before publish.
- [ ] No debug signing, no asset overwrite.
- [ ] Documentation updated with manual run commands.
- [ ] Success is not claimed without a manual owner run.

The `jni-contract` job also runs `scripts/check-native-diagnostics.py` to reject
project-owned native diagnostic payloads and unsafe exception disclosure. Keep this
static gate fail-closed; it does not establish native runtime logging behavior.

The same job runs `scripts/check-anonymity-boundaries.py` for selected Android logging,
LWS consumer, manifest and notification policies. This textual guard supplements tests.

Shared test jobs are owned by test.yml. After changing them, run the allowed static
`python3 scripts/sync-release-test-jobs.py --write`, then `--check` and actionlint.
Never hand-edit the generated release block independently. The generator and static
checker regression tests run in the JNI job; execute tests only in authorized CI.

## Maintenance & Continuous Learning

When this subsystem changes, reconcile this procedure with the implementation and
record reusable constraints, root causes and remaining verification limits. Check the
closest AGENTS.md and the skills index for stale guidance. Run the read-only
`python3 scripts/validate-agent-knowledge.py --scope worktree` before review; inspect
AK004 warnings for relevance and run regression tests only in authorized CI.

The manual agent-knowledge workflow runs Python regressions and worktree validation,
then validates committed changes when its optional `base` input is supplied. Keep
user inputs in quoted environment variables, full-history checkout at the dispatched
SHA, and the owner dependency. A clean checkout alone does not enforce AK001 against
previously committed changes; use `base` for that review.

Both signing jobs must depend directly on the instrumented job and require
its success, in addition to the owner and build jobs. The release suite source
validator checks those dependencies, the condition and the distinct environments.
Keep the corresponding regression cases in the manual workflow's Python suite.

CI Gradle steps before emulator startup may use `.github/scripts/run-gradle-ci.sh`
to retry once after Maven Central returns HTTP 429 during root classpath resolution.
Keep the original output and return the final invocation's failure status. Never
retry through this wrapper once Gradle has started tasks. Connected instrumentation
has its own device-health and attach retry rules. The wrapper regression uses a stub
and runs in the shared CI Python suite.
