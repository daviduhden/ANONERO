# GitHub Actions — Agent Instructions

These instructions apply to `.github/workflows/`. The root `AGENTS.md` remains
authoritative for security, language, build, and Git policy.

## 1. Workflows

- `test.yml` — reusable test suite called by `release.yml` and manual dispatch only.
  There is no `push` or `pull_request` trigger. Jobs:
  - `authorize`: owner gate; every other job needs it.
  - `unit`: JVM unit tests for `anon` and `nero`.
  - `jni-contract`: `scripts/check-jni-contract.py`.
  - `i18n`: `scripts/check-i18n.py`.
  - `native-language-policy`: rejects project-owned `Cargo.toml`.
  - `native`: builds the pinned Monero/JNI archives after verifying the submodule and
    patch series are present.
  - `instrumented`: API 35 emulator tests after `native`.
- `release.yml` — manual `workflow_dispatch` only. Inputs:
  - `tag`: existing `v*` tag to build; required.
  - `publish`: publish signed APKs to GitHub Releases; default `false`.
  - `run_instrumented`: pass through to `test.yml`; default `true`.
  Jobs:
  - `authorize`: owner gate; every other job needs it.
  - `test`: calls `test.yml` with the requested ref and instrumented-test flag.
  - `build`: verifies version/tag, enforces the Kotlin/C++ policy, installs native
    archives, builds unsigned release APKs, verifies package ID, version, minSdk,
    ABI splits, debuggability, and artifact count.
  - `sign`: protected `release` environment; signs six APKs with the release keystore
    and creates `SHA256SUMS`.
  - `publish`: only when `publish == true`; re-checks the tag, verifies checksums, and
    publishes without overwriting existing assets.

## 2. Manual-only and owner authorization

- No workflow may have `push`, `pull_request`, `pull_request_target`, `schedule`,
  `release`, `workflow_run`, `repository_dispatch`, or any other automatic trigger.
- `test.yml` keeps `workflow_call` only so `release.yml` can reuse it. The callee has its
  own owner gate, so an indirect call by a non-owner does not execute jobs.
- `workflow_dispatch` is not owner-only by itself: collaborators with write access can
  dispatch workflows. Every workflow therefore has an `authorize` job:
  `github.actor == github.repository_owner && github.triggering_actor == github.repository_owner`.
  Every other job has `needs: authorize` (or includes it), so a non-owner run has no
  runnable jobs.
- Repository settings remain part of the boundary. Recommend:
  - only the owner has write/admin access;
  - branch protection on `main`, including required review for `.github/workflows/`;
  - a `release` environment with required reviewers and the four signing secrets;
  - Actions allowed actions restricted to the pinned set, with SHA pinning required.
- A collaborator with write access can still edit a workflow in a branch and dispatch that
  ref. The `authorize` job limits the default branch, but branch protection and repository
  permissions are required to make that guarantee complete.
- The gate compares the actor with `github.repository_owner`. A transfer to an
  organization changes that value, so no user actor would match; revisit the gate before
  transferring the repository. Failing closed is intentional.

## 3. Non-negotiable rules

- Default permissions are `contents: read`. Only the `publish` job gets
  `contents: write`; only the `sign`/`publish` jobs use the `release` environment.
- Pin every `uses:` action to a full commit SHA with the version in a comment.
  Do not use mutable tags.
- Never use `pull_request_target` with repository secrets or untrusted code.
- Never expose signing secrets to a job that runs PR code. Signing material is read
  only in `sign`, from the protected environment.
- Do not add caches to the publishing workflow. Its comment explains the cache
  poisoning risk; keep `cache-disabled: true`.
- Do not weaken or skip the `native-language-policy` job. Do not add a Rust build
  path.
- Do not add a debug signing fallback to release builds.
- Do not remove artifact verification, checksum verification, or signature
  verification.
- Publishing requires `publish == true`, a valid `tag` input, and a successful build/sign.
  A disabled `publish` input must still leave the signed artifacts available for review.
- Do not overwrite an existing release's assets. The current workflow refuses this.
- Keep timeouts and concurrency groups. A release must not be cancelled mid-upload.
- Keep `fetch-depth: 0` where tag/commit verification depends on it.
- Keep `persist-credentials: false` on checkouts unless a job genuinely needs Git
  push credentials, which none currently do.

## 4. Release invariants

- `versionName` and `versionCode` are read from `app/build.gradle.kts`.
- A `v*` tag must match `versionName` and point at the checked-out commit.
- Expected artifacts: three APKs per flavor (`arm64-v8a`, `armeabi-v7a`, universal)
  for `anon` and `nero` — six total.
- Expected application IDs: `io.anonero.anon` and `io.anonero.nero`.
- `minSdk` must be 29. Release APKs must not be debuggable.
- Only `arm64-v8a` and `armeabi-v7a` native libraries are accepted.
- Every release APK contains `lib/<abi>/libanonero.so`.
- `SHA256SUMS` covers all six signed APKs and is verified before publishing.
- The signing keystore is decoded to `$RUNNER_TEMP`, has restrictive permissions,
  and is removed by a trap. Do not print or upload it.
- The release environment must contain all four signing secrets. Missing secrets
  fail the job.

## 5. Changing a workflow

1. Read the entire affected job and its inputs/outputs.
2. Identify the trust boundary: PR code, third-party action, artifact, cache, secret,
   or release asset.
3. Check permissions, triggers, environment, action pins, and artifact paths.
4. Preserve fail-closed behavior. New steps must fail the job, not warn.
5. Do not introduce a new secret unless it is documented and scoped to `release`.
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

`zizmor` currently reports one low self-repository style finding for the `./` reusable
workflow reference. The `$/` alternative is recommended by GitHub but is rejected by the
installed `actionlint`; `./` resolves to the same commit and remains valid. Revisit this
when the validator supports the new syntax.

Forbidden: `act`, `gh workflow run`, `./gradlew`, emulator, Docker builds.

## 7. Checklist

- [ ] Least-privilege permissions preserved.
- [ ] All actions pinned by SHA.
- [ ] No automatic triggers (`push`, `pull_request`, `schedule`, tags, etc.).
- [ ] `workflow_dispatch` is the only entry point; `workflow_call` is guarded and used only
      by the manual release workflow.
- [ ] Every job needs the `authorize` owner gate.
- [ ] No secret reachable from untrusted PR code.
- [ ] No cache in publishing.
- [ ] Test gates still required before build/sign/publish.
- [ ] Six APKs, expected IDs/versions/minSdk/ABIs, non-debuggable.
- [ ] Checksums and signatures verified before publication.
- [ ] Kotlin/C++ policy intact; no Rust.
- [ ] Artifacts do not contain secrets or unintended wallet data.
- [ ] Documentation updated; success is not claimed without a manual run by the owner.
