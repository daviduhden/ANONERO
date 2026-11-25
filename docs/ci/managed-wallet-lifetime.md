# MW-001 — Managed wallet replacement and profile deletion

This finding is verified by source review at `2ca0edb`. No crash or loss of funds
has been attributed to it in CI. Managed creation and open assigned the slot
unconditionally, and close temporarily cleared it before the native result.
Profile deletion checked only the auxiliary registry. A concurrent open or
cleanup could displace a native owner or remove files needed to retry close.

- **Assets and adversary.** Protect wallet keys, file consistency, and MAIN/DECOY
  separation against overlapping lifecycle work triggered by UI navigation,
  background shutdown or repeated local actions. No new network identity,
  destination, credential disclosure, or telemetry is introduced.
- **Boundary.** The Kotlin manager reserves one canonical wallet path while JNI
  acquires or closes its handle. Close hides the active wallet from listeners,
  retains the reservation, and restores visibility if native close fails.
  Profile deletion must pass both the managed and auxiliary path guards, and
  preserves credentials if files remain or their absence cannot be verified.
  A coroutine mutex serializes MAIN provisioning from empty-target validation
  through credential/file setup across onboarding and backup restore.
- **Failure mode.** Two native owners can reach the same backing files, a
  closing owner can be lost, or its credentials can be deleted during cleanup.
  The guards refuse replacement and deletion while ownership is uncertain.
- **Limits and compatibility.** This does not serialize arbitrary use of already
  captured raw handles with close, authenticate a filesystem outside the Android
  sandbox, or establish native callback quiescence. Paths, wallet formats,
  cryptography, JNI exports and flavor support are unchanged.
- **Verification.** JVM ownership regressions are prepared for both flavors.
  Authorized CI must also validate native close, wallet reopen and rollback on
  device. Also check overlapping MAIN provisioning requests and inject folder
  listing/deletion failure before credential removal. Static review cannot prove
  timing, native memory safety, or fund safety.
- **Rollback.** Revert the ownership change and its tests as a unit. No on-disk
  migration is needed. Preserve wallet files and credentials after any failed close.
