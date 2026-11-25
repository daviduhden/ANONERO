# Backup restore staging and native close

Restore preview previously wrote the decrypted ZIP directly under the cache
root. It now creates a separate directory under `cache/tmp_backup` for the
encrypted input and decrypted archive. On return or exception the helper removes
that directory without following links. The preview still reads the validated
extracted files from `cache/tmp_extract`. Startup purges both staging locations
and old cache-root files. Export, extraction, restore and explicit cache cleanup
share the backup mutex so they cannot delete each other's in-use scratch files.
The preview binds its payload to the staged files through an in-memory owner.
Disposal cancels that owner and queues cleanup under the same mutex. A stale
preview can neither restore newer files nor remove another preview's files.

A failed passphrase open can return a non-OK managed wallet. The legacy PIN
retry requires native close to return success first. A false or throwing close
aborts restoration and retains MAIN's files and credentials while the wallet
remains managed. The identity-mismatch path requires the same close confirmation
before removing MAIN. DECOY is outside both cleanup paths.

## Threat model and compatibility

- Assets are the wallet seed, cache and node credentials in the backup, and the
  files and credentials required to reopen the restored wallet.
- A malformed import, incorrect credential, or native close failure can
  interrupt the operation. A local observer with access to app-private storage
  could correlate archive contents with the wallet. The archive remains in
  private scratch outside FileProvider paths and no data is sent over the network.
- The input URI is untrusted. Decryption writes into private staging, archive
  validation precedes extraction, and wallet2 owns the copied MAIN files during
  binding. Profile removal is deferred while the previous native owner remains live.
- If cleanup fails or the process dies, plaintext scratch can remain until the
  next startup purge. File deletion does not prove physical erasure or protection
  against privileged inspection. The backup mutex only coordinates backup operations.
- v1/v2/v3 inputs, wallet credentials, cryptography and published backup formats
  are unchanged. Scratch files have no migration requirement.
- Roll back by reverting only the code and checks for this change. No persisted
  format conversion is needed. Keep the startup purge for both scratch layouts.

## Evidence

The JVM archive tests cover successful cleanup, cleanup after partial output and
a symlink staging parent. The source guard checks the close gate before the
legacy PIN retry. CI still needs to run the JVM suite and verify backup previews
and restore on both flavors, including false and throwing native close, and
startup recovery after an interrupted decrypt. Static checks do not prove native
lifetime, data erasure or end-to-end restore.
