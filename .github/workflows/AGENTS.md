# GitHub Actions — Agent Instructions

These instructions apply to `.github/workflows/`. The root `AGENTS.md` remains
authoritative for anonymity, privacy, security, language, build, and Git policy.

## 1. Workflows

- `test.yml` — manually dispatched test suite; source of the generated release test jobs.
  There is no `push` or `pull_request` trigger. Jobs:
  - `authorize`: owner gate; every other job needs it.
  - `unit`: JVM unit tests for `anon` and `nero`.
  - `lint`: Android Lint for both flavors; uploads HTML/XML reports.
  - `jni-contract`: `scripts/check-jni-contract.py` with `scripts/check-native-diagnostics.py` and
    `scripts/check-anonymity-boundaries.py`.
  - `i18n`: `scripts/check-i18n.py`.
  - `native-language-policy`: rejects project-owned Java sources.
  - `native`: builds the pinned Monero/JNI archives after verifying the submodule and
    patch series are present.
  - `assemble`: links `libanonero.so` and verifies the API 29 floor even when instrumented
    tests are skipped.
  - `instrumented`: API 35 emulator tests after `native` and `assemble`.
- `release.yml` — manual `workflow_dispatch` only. Inputs:
  - `tag`: existing `v*` tag to build; required.
  - `publish`: publish signed APKs to GitHub Releases; default `false`.
  - `run_instrumented`: pass through to `test.yml`; default `true`.
  Jobs:
  - `authorize`: owner gate; every other job needs it.
  - required suite: embeds the eight Tests jobs using the immutable dispatched main commit;
    `test` aggregates all results and the same-run native artifact before build.
  - `build`: verifies version/tag, enforces the Kotlin/C++ policy, installs native
    archives, builds unsigned release APKs, verifies package ID, version, minSdk,
    ABI splits, debuggability, and artifact count.
  - `sign-anon` and `sign-nero`: independent environments approve and sign only
    their own three APKs. Each signs with v2/v3 after the ZIP and unsigned-input
    checks, verifies the pinned certificate and package, and uploads checksums.
  - `prepare`: verifies distinct signer certificates and public keys, all checksums,
    and the two canonical universal filenames before staging release assets.
  - `publish`: only when `publish == true`; re-checks the tag, verifies checksums, and
    publishes without overwriting existing assets.

- `agent-knowledge-validation.yml` — manually dispatched Python validator and CI-only
  regression module. `authorize` gates the validation job. Full history is fetched at
  the dispatched SHA; optional `base` compares its merge base against that SHA.
  Worktree validation always runs; without `base`, synchronization of committed project
  changes is not assessed. No Android build, cache, secrets or artifact upload is used.

## 2. Manual-only and owner authorization

- No workflow may have `push`, `pull_request`, `pull_request_target`, `schedule`,
  `release`, `workflow_run`, `repository_dispatch`, or any other automatic trigger.
- All workflows accept only `workflow_dispatch`; no callable entry point is allowed.
  Release embeds synchronized test jobs and runs the full suite in its own manual run.
  `scripts/sync-release-test-jobs.py --check` rejects drift; `--write` regenerates only
  the marked release job block. Every shared job retains its owner dependency; the
  aggregate rejects missing/failed/cancelled required checks and unapproved skips.
- `workflow_dispatch` is not owner-only by itself: collaborators with write access can
  dispatch workflows. Every workflow therefore has an `authorize` job:
  `github.actor == github.repository_owner && github.triggering_actor == github.repository_owner`.
  Every other job has `needs: authorize` (or includes it), so a non-owner run has no
  runnable jobs.
- Repository settings remain part of the boundary. Recommend:
  - only the owner has write/admin access;
  - branch protection on `main`, including required review for `.github/workflows/`;
  - two app-specific signing environments and the publication-only `release`
    environment, each restricted to `main` with required reviewers;
  - Actions allowed actions restricted to the pinned set, with SHA pinning required.
- Release dispatch is restricted to `main`. Restrict environment deployment refs as
  a separate server-side control and review workflow changes before allowing them on
  `main`. Environment approval is required before the release key is made available.
- The gate compares the actor with `github.repository_owner`. A transfer to an
  organization changes that value, so no user actor would match; revisit the gate before
  transferring the repository. Failing closed is intentional.

## 3. Non-negotiable rules

- Default permissions are `contents: read`. Only the `publish` job gets
  `contents: write`; signing jobs have no token permissions and `publish` alone uses the
  publication-only `release` environment.
- Pin every `uses:` action to a full commit SHA with the version in a comment.
  Do not use mutable tags.
- Never use `pull_request_target` with repository secrets or untrusted code.
- Never expose signing secrets to a job that runs PR code. Signing material is read
  only in the matching signing step. Signing jobs never check out or run project
  code, and download only the app-specific unsigned APK artifact.
- Do not add caches to the publishing workflow. Its comment explains the cache
  poisoning risk; keep `cache-disabled: true`.
- Do not weaken or skip the `native-language-policy` job. Kotlin/Compose and C++/JNI
  are the permanent architecture; do not add another native runtime or FFI layer.
- Do not add a debug signing fallback to release builds.
- Do not remove artifact verification, checksum verification, or signature
  verification.
- Publishing requires `publish == true`, instrumentation, a valid `tag` input, and successful preparation.
  A disabled `publish` input must still leave the signed artifacts available for review.
- Do not overwrite an existing release's assets. The current workflow refuses this.
- Do not add a step that uploads source, artifacts, logs, environment data, or build
  metadata to an external service. CI must not introduce telemetry or a new identifier.
- Do not publish artifacts containing wallet data, node addresses, user identifiers, or
  secrets. Review new artifact paths and log output for linkability.
- Keep timeouts and concurrency groups. A release must not be cancelled mid-upload.
- Keep `fetch-depth: 0` where tag/commit verification depends on it.
- Keep `persist-credentials: false` on checkouts unless a job genuinely needs Git
  push credentials, which none currently do.

## 4. Release invariants

- `versionName` and `versionCode` are read from `app/build.gradle.kts`.
- A `v*` tag must match `versionName` and point at the dispatched and tested SHA.
- Expected artifacts: three APKs per flavor (`arm64-v8a`, `armeabi-v7a`, universal)
  for `anon` and `nero`. The two canonical universal copies bring the signed file count to eight.
- Expected application IDs: `dev.uhden.anon` and `dev.uhden.nero`.
- `minSdk` must be 29. Release APKs must not be debuggable.
- Only `arm64-v8a` and `armeabi-v7a` native libraries are accepted.
- Every release APK contains `lib/<abi>/libanonero.so`. Packaged arm64-v8a
  shared libraries must have 16 KiB compatible ELF LOAD segments.
- `SHA256SUMS` covers all eight signed APK files and is verified before publishing.
- The signing keystore is decoded to `$RUNNER_TEMP`, has restrictive permissions,
  and is removed by a trap. Do not print or upload it.
- Each `android-<flavor>-release` environment stores its own keystore and password
  secrets and normalized public certificate hash. Missing or mismatched values fail
  signing. See [custody and registration](../../docs/android-release-signing.md).

## 5. Changing a workflow

1. Read the entire affected job and its inputs/outputs.
2. Identify the trust boundary: PR code, third-party action, artifact, cache, secret,
   or release asset.
3. Check permissions, triggers, environment, action pins, and artifact paths.
4. Preserve fail-closed behavior. New steps must fail the job, not warn.
5. Do not introduce a new secret unless it is documented and scoped to the matching protected environment.
6. Do not add a new artifact that contains wallet data, logs, keys, or native debug
   symbols not intended for publication.
7. Update this file and the `github-actions` skill if the workflow contract changes.
8. Expect validation only through a manual dispatch by the repository owner. Do not run
   workflows or project builds locally.

## 6. Static checks

Allowed:

```sh
python3 - <<'PY'
import pathlib, yaml
for p in pathlib.Path('.github/workflows').glob('*.yml'):
    yaml.safe_load(p.read_text())
PY
grep -n "uses:" .github/workflows/*.yml
grep -n "permissions:" .github/workflows/*.yml
grep -n "pull_request_target\|secrets\." .github/workflows/*.yml
grep -nE '^  (push|pull_request|pull_request_target|schedule|release|workflow_run|repository_dispatch):' .github/workflows/*.yml
actionlint .github/workflows/*.yml
zizmor --format plain .github/workflows
git diff --check
```

If PyYAML is unavailable, parse structurally by reading and use a minimal YAML
sanity check; do not install packages.

The generated release suite disables Gradle caches, binds every test checkout to
`${{ github.sha }}` and preserves the same-run native artifact contract.
`zizmor --offline` reported no findings in this iteration; that is a static result only.

Forbidden: `act`, `gh workflow run`, `./gradlew`, emulator, Docker builds.

## 7. Checklist

- [ ] Least-privilege permissions preserved.
- [ ] All actions pinned by SHA.
- [ ] No automatic triggers (`push`, `pull_request`, `schedule`, tags, etc.).
- [ ] `workflow_dispatch` is the only entry point in all workflows; shared jobs and
      aggregate remain synchronized and fail closed.
- [ ] Every job needs the `authorize` owner gate.
- [ ] No secret reachable from untrusted PR code.
- [ ] No cache in publishing.
- [ ] Test gates still required before build/sign/publish.
- [ ] Six versioned APKs, two canonical copies, expected IDs/versions/minSdk/ABIs, non-debuggable.
- [ ] Checksums and signatures verified before publication.
- [ ] Kotlin/C++ policy intact.
- [ ] Artifacts do not contain secrets or unintended wallet data.
- [ ] Documentation updated; success is not claimed without a manual run by the owner.

The JNI job additionally checks Kotlin cancellation imports and shared-job synchronization,
then runs Python static-checker regression tests in CI only. Do not run those tests locally.
Optional emulator animation settings are disabled after run 43633496677 failed before the
repository test script; readiness, instrumentation, report and nonzero-test gates remain.
