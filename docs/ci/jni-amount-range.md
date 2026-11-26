# AMOUNT-001 — Atomic units at the signed Android boundary

Source review at `b09f9dc` found wallet2 amounts, balances, fees, dust and
history transfers narrowed from unsigned 64-bit atomic units to Kotlin
`Long`. Above `Long.MAX_VALUE` that conversion cannot represent the amount.
Positive account or output values can also overflow a signed aggregate.
Conversely, the restore preview could pass a negative balance from backup
metadata to the unsigned wallet2 formatter and display a large positive value.

The affected assets are reviewed payment amounts, fee and balance displays,
and backup previews. A malformed backup or wallet input can reach the affected
reads. Large legitimate cumulative receipts can exceed an individual transfer.
The Android contract covers nonnegative signed 64-bit atomic units. Values
outside that domain must not authorize a spend or be displayed as a wrapped
amount. No wallet2 consensus, key derivation, serialized wallet, JNI signature,
network destination or logging payload needs to change. The adapter still
cannot represent the entire unsigned wallet2 domain. A future wider UI type
would require end-to-end review of every consumer.

The checks require both ARM builds, boundary-value coverage and tests on the
exact revision. The green Tests run [45706621403](https://github.com/daviduhden/ANONERO/actions/runs/45706621403)
predates the change. No crash, transferred funds or sanitizer verdict
has been attributed to this finding. Roll back the adapter and its callers
together. Refuse transactions whose reviewed amounts cannot be represented.

The native helper checks all coin, transfer, transaction, balance and fee
results consumed as signed atomic amounts. Snapshot/read failures use their
existing Java exception paths, fee estimation keeps its failure result and an
out-of-range callback cannot publish a wrapped payment. The multi-destination
converter bounds the running total before wallet2 sees the request. Kotlin
checks cumulative receipts, watch-only balances and selected outputs. Missing
balance state blocks the Send preflight including sweep. Backup extraction
validates the balance before publishing the preview.

The manual suite includes a formatter device case at `Long.MAX_VALUE` and one
for a negative amount. JVM cases check the decimal parser's upper bound and
oversized inputs. The native boundary assertion is compiled for each ABI, and
the JNI source check covers the wallet2 fields consumed by the model and review
getters. Running those new device and compiler cases remains pending.
