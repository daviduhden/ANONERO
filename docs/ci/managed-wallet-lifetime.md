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

### BK-001 — Partial backup installation and password rollback

Static review at `bf1cf25` found that a copy failure after preferences or the
first wallet file were written could block a retry at the empty-target check.
A failed password rollback also called wallet2 store after setPassword returned
false. The protected assets are the existing wallet files and the original
encrypted backup. Disk exhaustion, a destination collision or a native re-key
failure suffices. The restore now exclusively creates destination files under the
closed-path guard, removes its own partial copies on I/O failure, and commits
the PIN verifier, passphrase verifier and restore height together after binding.
A failed rollback skips the explicit store. The external backup, archive formats,
KDFs and DECOY files are unchanged. Fixed diagnostics avoid including wallet2
text in exceptions. The JVM fixture forces a collision after the first copy.
Device fault injection must still exercise credential and disk failures. Process
death during installation can leave an occupied target requiring recovery.
Reverting the code requires no format conversion. Preserve a partial target when
native ownership or the outcome of cleanup is uncertain.
