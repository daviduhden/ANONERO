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

### MW-002 — Replacement between authenticated close and profile removal

At `bf1cf25`, secure wipe authenticated the visible wrapper and closed it, then
separately acquired the deletion guard. A second managed session could open and
close in that interval. An idle path alone could then authorize deletion of its
replacement files. This is a source finding with no attributed CI crash or data
loss. The affected assets are the captured profile's wallet files and credentials.

Keep the slot monitor across successful close publication and the synchronous
profile deletion, preserving the auxiliary path check. Native close itself must
still run outside the monitor so wallet2 can join callbacks. A failed close must
skip deletion and retain ownership. A deletion failure after native close must
leave the wallet closed. Exercise both outcomes in the slot JVM suite. Device
coverage is still required for Keystore and filesystem failures. Shared cache and
DataStore cleanup occur later and still have the limitations in
[UI session boundaries](ui-session-boundaries.md). The change introduces no new
network activity, persisted identifier, wallet format, or recovery credential.
If this ordering needs to be withdrawn, keep destructive wipe unavailable until
the close-to-deletion ownership guarantee is restored.

### MW-003 — Concurrent decoy creation and retained-owner recovery

At `bf1cf25` the decoy setup checked for an empty target outside any shared
provisioning guard. Concurrent requests could replace the Keystore secret before
auxiliary ownership was registered. Manual removal could retry native close
while creation still used that wrapper. Publishing a duress credential also
discarded the synchronous preference write result. This preventive finding is
based on call order, with no observed CI incident.

Serialize empty-target checks, secret creation, wallet initialization, credential
publication and rollback with removal, PIN changes and authenticated deletion.
One process-wide mutex covers both profiles because their PINs must differ. Reject an overlapping destructive request instead of queuing it
against a different wallet. The protection is in-process, with no persisted lock
or change to existing keys, wallet files or network destinations. A device test
should pause provisioning before secret storage and after native acquisition,
attempt a second creation/removal, and inject a failed preference commit. Check
that only one request owns the secret and failure cannot report success. Roll
back the guard only together with disabling overlapping destructive operations.

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

### AUTH-001 — A deferred PIN operation could target a replacement session

At `bf1cf25` the lock UI recomputed the active profile key after hashing and
again after wallet open. Settings retained only a Boolean after verification,
then selected the current wallet for rekeying. Background unlock and signing
also resolved the wallet again on a worker. Overlapping local actions could
apply a verified PIN to another session, replace its gate or unlock its state.
A legacy wallet rekey followed by an unsuccessful preference write could leave
the gate and the wallet password inconsistent.

Carry the authenticated wallet identity, profile and verified credential version
through to the consumer. Serialize persistent credential mutations and keep
wallet password migration in the existing authenticated open path. A PIN change
requires a successfully bound device secret. If migration remains incomplete,
leave the previous PIN usable and retry migration at the next authenticated
open. Neither PIN nor credential version may be logged or saved in UI state.
Exercise wrong PIN, concurrent replacement, lock, old credential upgrade,
failed commit, and interrupted migration before treating this as validated.
Existing files, PIN encodings, transport policy and credentials keep their
formats. If reverting, disable affected settings writes until profile-bound
verification is available.
