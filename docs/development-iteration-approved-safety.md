# Approved safety proposals

## NERO signed transaction containment

The reviewed payment and the unknown signed payload cross different trust boundaries.
A compromised or incorrect external signer can return a transaction with different
outputs. Until wallet2 can validate a signed object against the reviewed construction
and submit that same object, no Android screen may broadcast a signed file. This
contains the known gap without changing wallet files, review amounts, import formats
or networking. ANON retains its existing pending-transaction broadcast path and
offline signing. NERO can still export the unsigned transaction. No new wallet
identifiers or network requests are introduced.

CI should verify both flavors, confirm signed-file submission is unreachable in the
app, and test that the warning is localized. The complete implementation requires
validated fixtures for multi-transaction sets, fees, change, substitutions and session
changes. Rollback must not re-enable unverified submission. Existing files remain
recoverable through an independently verified workflow.

## Release signing and unlock navigation

The signing keys establish update identity. A valid manual dispatch that skips
instrumentation may still build diagnostic unsigned APKs, while the protected
signing jobs must require successful instrumentation for that execution.
This keeps owner and environment approval, artifact checks and existing
version/package identity. The manual CI regression must check the signing jobs
remain skipped when instrumentation is disabled. Branch rules and release-tag
policy remain separate remote settings.

The lock overlay keeps wallet data hidden until native unlock succeeds. An
unsuccessful or stale unlock must not route the user into a different screen
behind it. Gate shortcut delivery on successful exit in the current profile.
A failed native background-sync start pauses refresh and keeps the overlay
locked. Restart is the current recovery path. CI needs failure-injection
coverage to confirm lock persistence, authentication on reopen and spend-key
handling.

## Native callbacks and reviewed actions

The JNI listener adapter stays attached until a successful native close. Replacing
the target changes its Java reference under a mutex. Kotlin serializes listener
registration with close, and a failed close keeps ownership. First attachment
still follows the pre-refresh startup contract. A callback can finish using its
previous local reference. This protects listener ownership; wallet and transaction
operations still need a lease that prevents concurrent close or replacement.

Review captures the wallet, profile and account and checks the native object
reference before signing or broadcasting. This prevents a stale screen from
starting an operation with a known mismatched session. Route destination rows
still require binding to the constructed native transaction. CI needs concurrent
close/replacement fixtures, CheckJNI and device evidence. The isolated adapter
can be revised while preserving failed-close ownership and the public JNI contract.

## Transport and exports

Live transaction construction and broadcast require the transport that was applied
to the current wallet and profile. Transport loss pauses refresh. Routine
state refresh and local decoy provisioning avoid the manager's separate height
RPC. Backup cleanup and post-broadcast recovery also recheck authorization
before restarting refresh. An in-flight native operation may continue past a transition. Validate the
stop behavior, reconnects, VPN routing and DNS on a device with controlled endpoints.
Any rollback must preserve offline behavior on loss and keep decoy provisioning local.

Unsigned export checks the output file and deletes partial writes. QR encoding
bounds reads and keeps oversized valid payloads available for file export.
Fixed cache filenames still need an operation-scoped lifecycle across screens.
Test stale output, cancelled export, concurrent views and file cleanup before
claiming isolation. No diagnostic should contain transaction paths or payloads.

## Remaining milestones

Signed-file binding needs a wallet2 parser/submit contract. An independent backup
credential needs explicit recovery semantics and historical restore fixtures.
Currently the restore path uses the same input to decrypt the archive, open
the copied wallet and derive the persisted seed-passphrase verifier. A writer
change must distinguish those inputs without changing the behavior of v1–v3 readers.
Dependency verification needs reviewed resolution output and trusted checksums.
Runtime coverage needs stable Android runners and separate evidence for minimum
API, both ABIs, transport loss, backup/restore and native sanitizer diagnostics.
Transaction review still needs a complete state model and accessibility/device
validation. Existing CI runs describe the remote revision only.

The JVM fault regression exercises rejection of unlock, profile changes and
online state updates until session reset. An already-open wallet shows the
overlay even before initialization. Proxy setup checks the lock fault before
and after acquiring the transport, and authorization refuses a faulted session.
A callback or native network operation already in flight remains device evidence.
