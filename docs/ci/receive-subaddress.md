# ADDR-001 — Subaddress allocation during refresh

At `b09f9dc`, `WalletState.update` called `getLatestSubAddress` on every
unlocked update. Its index calculation always advanced beyond the last created
index. As a result every refresh attempted to append and store a wallet2
subaddress. The explicit add action also called the same allocator after its
own append. A history index ahead of the native count could select an index
beyond the single address actually added. The void append had no postcondition
and the implicit store discarded its result. This is a source finding. The
[successful API 35 run](https://github.com/daviduhden/ANONERO/actions/runs/45706621403/job/139546185283)
contains no recorded device reproduction.

Receive entry and the address-list add action now request one new address for
the captured wallet and major index. Recomposition, history polling and label
changes do not issue addresses. The index comes from the native count even
when earlier addresses have received no funds, since a recipient may already
have shared them. Known history or output indices beyond that count refuse
issuance. A successful store, matching count and wallet2 address validation
precede publication in Receive. Returning to Receive or unlocking can allocate
another fresh index. Editing a label checks the account, minor index and full
address before writing. Failed or overflowing list reads clear the snapshot.
The native account and subaddress APIs also check count ranges, account creation
and label write results. The adapter inspects setter status before reading the
label back because the wallet2 getter clears the preceding status.

The assets are receipt attribution, wallet metadata, the fresh-address
decision and wallet storage. Repeated sync callbacks and overlapping requests
can otherwise grow the wallet or bind a label to another account. No recipient,
network identifier, persistent application preference or native export is
added. External observers may still correlate payments, and other parties may
reuse an address once shared. A failed store can leave an extra in-memory index
until the wallet is closed. Wallet2 remains responsible for persistence.

JVM regressions cover two consecutive unpaid requests, an unchanged count after
append, a failed store, ahead-of-count history and invalid counts. Manual
device validation should open Receive, repeatedly refresh and edit its label,
then confirm exactly one new index until the next user request. Switch accounts
during the request, reject stale label changes, and inject native read/store
failures on both flavors. Verify the displayed address persists after reopening.
No on-disk conversion is required if withdrawn. Preserve explicit issue and
store checks when changing the receive flow again.
