# PIN persistence and transaction Parcel regression

## PIN gate

The assets are the PIN attempt budget and the credentials and wallet capabilities
it protects. A person with interactive access to a locked app can retry PINs and
restart the process. Android preferences can expose changed values in memory even
when disk persistence fails. Without a successful commit, proceeding to verify a
PIN would let an attacker discard individual guesses by restarting.

The lock screen records the attempt to app-private preferences before invoking
PIN verification or wallet open. A failed commit aborts that request and uses the
existing generic verification failure. After authentication, a failed counter
clear reports a fixed diagnostic and does not invalidate the already completed
credential check. The diagnostic contains no PIN, attempt count, or profile. This
adds no network activity. Root, compromised OS, or app-context execution remain
outside the rate limit's protection. The preference keys and wait schedule are
unchanged. Rollback affects this persistence check only and needs no data migration.

Run `PinThrottlePersistenceTest` via authorized instrumentation for both flavors.
It simulates failure at the preferences boundary after updating the in-memory
state. The lock screen rejection and recovery after actual storage failure remain
device checks.

## Transaction Parcel

The previous writer omitted the transfer field when its value was null. The reader
still consumed a list and could read the notes at the wrong offset. Wallet history
and destination addresses remain on the existing app-local path. The fix writes the
nullable list in the same position as populated lists and adds no destination,
network field, or external input path. JSON, JNI constructor signatures, and
existing non-null Parcel records keep their formats. An older null record was
already incomplete. No persistent Parcel migration is involved. Rollback can
reintroduce the incomplete null layout.

Run `TransactionParcelTest` on Android API 29–32 and API 33+ to cover the legacy and
typed readers. Its null, empty and populated list cases verify following fields and
full consumption. Local source review cannot exercise Android's Parcel reader.
