# UI session and asynchronous work regressions

**Evidence:** preventive static review at `2ca0edb`. These defects were not reproduced
by [Tests 45679643013](https://github.com/daviduhden/ANONERO/actions/runs/45679643013).
That revision passed both debug flavors on the API 35 emulator (nine cases each)
and the 130 JVM cases for each flavor. Lint reported zero errors and 13 warnings
per flavor. Five `UseKtx` advisories point to checked preference commits in
`AnonWalletHandler`, `PinThrottle`, `PrivacySettings`, and `TransportModels`.
Their Boolean return is required for fail-closed behavior. The unit-returning
extension cannot replace those calls. Eight other reports are dependency-update
advisories. No project Kotlin compiler warning was found in the retained JVM,
assemble, or instrumented logs. The sdkmanager and emulator warnings are from
runner tools.

| ID | Root cause and cross-repo pattern | Fix | Regression evidence required |
| --- | --- | --- | --- |
| UI-SESSION-001 | Seed settings looked up the profile hash and resolved the active wallet again for seed/private-key reads and NERO export. Secure Wipe also made a detached check before selecting a wallet again. | Bind successful verification to the wallet identity and profile, check wallet-file ownership on the IO path and discard results if the session locks or changes before display. Keep seed-screen access transient and exclude it from diagnostics. | Source guard in `check-anonymity-boundaries.py`; in CI/device testing delay hash and key reads across MAIN/DECOY switch, same-profile reopen, navigation and lock. Confirm that seed, keys and NERO sheet stay hidden until reverified. |
| UI-SESSION-002 | Secure Wipe read a seed for validation, then reselected the wallet/profile on a later asynchronous path. It also reread the mutable profile at deletion. | The handler authenticates the captured profile's nonempty stored hash for the expected wallet and backing path, under the open-wallet monitor. It deletes only that profile after confirmed close. The UI handles the specific request's result and shared cleanup checks for a replacement at each stage. | Source guard plus managed-wallet slot regressions; exercise wrong passphrase, inactive/mismatched profile, lock, failed close, a profile switch while credential hashing runs, and failed preference commit. Verify no deletion on authorization or close failure and no residual credential deletion from the other profile. |
| UI-SESSION-003 | Transaction Details observed a non-null transaction before dispatch, then read mutable Compose state again on IO. WalletState selected the wallet again, and ignored the Boolean result of the native note write. | Capture the note and transaction owner before dispatch, require current history membership and unlocked state, and propagate native write/storage failure. Avoid delayed reads of mutable nullable state from lazy composition. | Device test should queue a note before lock, history/account change and profile replacement, then resume it. Confirm rejection with no note in the replacement wallet and positive save/store on the original current session. |
| UI-SESSION-004 | The animated QR producer was detached from a fixed-key effect and kept displaying the old payload when composition supplied a different UR. Zero or greater-than-1000 FPS could divide by zero or spin without suspension. | Tie frame state and coroutine to payload, fragment parameters and rate, requiring a positive inter-frame delay. | `AnimatedQrCodeSessionTest` checks immediate old-frame removal and eventual new-frame publication using synthetic URs. Receiver interoperability and on-device lifecycle still need independent verification. |

## Boundary review

Assets are wallet seeds and keys, profile wallet files and credentials, transaction
history and notes, and offline QR export integrity. A local user or adversary able
to schedule a lock, profile switch, wallet reopen, or navigation during asynchronous
work could associate data or a mutation with the wrong identity. Checks at the UI
and orchestration layers ensure authorization and side effects use the same
captured owner, and progressively stop stale shared wipe cleanup. No new key
material, telemetry, network channel, stored identifier, format or preference key
is introduced.

Point-in-time identity checks do not pin the JNI wallet for all in-flight calls or
make separate DataStore stores atomic. Wipe checks for an idle slot before shared
cleanup phases. A wallet published between those checks and a suspending shared
store can still observe a cleanup already in flight. A failed wipe after successful
native close can mean partial teardown. Physical erasure, native quiescence, and
resistance to forensic access remain unverified. Restore and existing serialized
wallet formats are unchanged. The only rate constraint affects the QR composable's
internal callers, all of which pass 15 FPS.

If rollback is required, retain explicit profile capture for seed access and guarded
profile deletion before restoring the previous flow. The note and QR changes may
be reverted independently of wallet storage and JNI ownership. All test and device
claims for the new local changes remain unverified.
