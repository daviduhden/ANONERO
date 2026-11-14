<!--
  Copyright (c) 2026, David Uhden Collado
  Copyright (c) 2022-2026, r4v3r23

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Manual-only workflow iteration — 2026-11-14

## Threat model before editing

Assets: release source/test binding, native artifacts, protected signing keys and
publication authorization. Adversaries include a collaborator able to dispatch a branch,
a compromised upstream action or poisoned cache, and accidental changes that skip a
required check. The boundary is manual dispatch -> owner gate -> required suite ->
unsigned build -> protected signing -> optional publication.

The explicit user requirement permits only workflow_dispatch; it overrides the older
workflow_call exception in workflow instructions and the github-actions skill. Remove
that entry point while preserving the full required suite inside the release workflow.
Do not dispatch an auxiliary workflow or accept results from an unrelated earlier run.
Import test jobs into release with the release tag checkout reference and disabled
Gradle caches. Keep all action pins, read-only test permissions, artifact checks,
timeouts, owner authorization and protected environments unchanged.

A generated aggregate job must require every shared job, reject failed/cancelled/skipped
required checks, permit skipped instrumentation only when explicitly disabled, and expose
only the native artifact from this run. The existing publication condition continues to
require instrumentation. A standard-library source synchronizer/checker prevents drift
between the shared jobs, normalized release jobs and aggregate dependencies. It does not
parse arbitrary YAML or replace actionlint. Run it as a static CI check in both suites.
Run Python script regression tests in CI only, not locally.

Compatibility: manual inputs remain intact; native archives stay in the same run and
artifact contract. No secret or permission changes, network callbacks, caches, wallet
formats or JNI changes. Rollback is reverting workflow/script source together; do not
restore workflow_call without a new explicit user decision. Required CI evidence is an
owner-authorized manual run of Tests and release against the intended source, including
failure/skip cases. Local generator verification and actionlint cannot establish runtime
action behavior, signer safety or artifact correctness. No workflow execution, build,
test, commit, push, tag or publication is authorized by this iteration.

## Implemented and static verification

`test.yml` and `release.yml` now expose workflow_dispatch only. Release contains the
complete Tests suite in its own execution. Generated checkout refs are explicitly
`refs/tags/${{ inputs.tag }}` to avoid branch/tag name ambiguity; this is the same tag
namespace as release build. Gradle caches are disabled in every imported release job.
The aggregate depends on all eight shared jobs plus owner authorization, rejects failed
or unexpectedly skipped results and requires the native artifact output before build.
It permits skipped instrumentation only for an explicitly disabled input; publication
still requires instrumented execution.

`scripts/sync-release-test-jobs.py --check` compares normalized source jobs, aggregate
needs/body and shared Android/Gradle environment values. `--write` regenerates only the
marked release block. The checker rejects callable/automatic entry points, missing
required source jobs, unsupported checkout refs/caches and missing generation markers.
Both suites run it and `python3 -m unittest discover -s scripts/tests` in jni-contract.
New synchronizer regressions cover trigger, environment, job, cache, checkout-ref and
marker changes plus restoring drifted release job text. Tests were prepared, not run.

Static checks passed: generator --check, Python source syntax compilation, actionlint,
yamllint, offline zizmor (no findings) and git diff --check. No workflow was dispatched.
The checker supports this repository's indentation layout, not arbitrary YAML; actionlint
remains necessary. Tags built by the new workflow must contain its checker scripts.
Runtime job skip propagation, permissions, artifacts and signing still need manual CI.

## Existing-run diagnosis: 43633496677 (read-only gh inspection)

HEAD a2c9cfb: JVM, lint, native, assemble, symbol and localization jobs passed. The
instrumented runner reported boot after about 605 seconds, then its optional animation
settings command failed with `Failure calling service settings: Broken pipe (32)` and
adb exit 224 before the repository test script started. No reports were generated.
The directly observed failure is the emulator/system-service command; the underlying
guest failure cause is not established by these logs. Hosted runner instability remains.

Disable the runner's nonessential animation-settings mutation. This is not a test or
security check: all repository readiness, test execution, XML report validation and
nonzero-test gates remain required. The repository script still fails closed if guest
services or instrumentation are unhealthy; no blind retry or passing fallback is added.
This may avoid this pre-script operation, but does not prove the emulator is stable or
that tests execute. CI/device evidence is still required. The release copy must be
regenerated from Tests after this configuration change.
