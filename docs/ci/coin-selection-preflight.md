# Coin selection preflight regression

Preventive source finding, identifier CI-PRE-COIN-01. There is no failing CI run
for this finding. The existing Send UI checked explicit selections against a
wallet snapshot, but Wallet's regular and multi-destination paths only summed
matching key images, and explicit sweeps bypassed that check. The summation did
not detect overflow or a partial match.

The assets are the funds and the user's input-selection decision. A stale
selection, concurrent account change, or malformed native snapshot can omit an
output, misstate eligibility, or present a value that wraps arithmetic in Kotlin.
The data flow is local from the selected key images through the current Wallet
account and output snapshot to JNI and wallet2. The guard requires the entire
explicit selection to be eligible before invoking wallet2. It does not send,
persist, log, or add identifying data. Incorrect handling could reject a valid
payment or broaden the selection beyond what the user requested.

Empty selection retains its existing no-coin-control meaning, including wallet-wide
sweep. Wallet2 remains authoritative for construction, fees and output selection.
No native signatures, persisted formats or wallet files change. Kotlin checks
cannot serialize the native wallet's lifetime or guarantee the real transaction's
input set against state changes after the snapshot.

The JVM regression covers exact selection, missing and duplicate keys,
unspendable and wrong-account outputs, insufficient balance and overflow. It is
included by the existing manual JVM jobs for both flavors. Native validation
should exercise selected send and sweep against account/output changes. Roll back
the Wallet and UI preflight changes together if CI reveals a compatibility issue.
Static review is not proof of fund safety or anonymity.
