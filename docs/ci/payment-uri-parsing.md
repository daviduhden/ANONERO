# Payment URI parser regression

Identifier CI-PRE-PAYMENT-URI-01. During static review, the payment URI parser
compared the decoded scheme-specific part with the complete scheme. It then
globally replaced `monero:`, which rewrote a hierarchical URI's authority and
could also modify query values. It accepted the first of duplicate parameters.
The same parser serves single-send scans and payment scanner entry points in
transaction history.

The recipient and exact amount are payment-intent data. A sender-controlled QR
code may include misleading authority syntax or repeated amount parameters.
Normalize only the exact URI scheme boundary, reject non-address authority
syntax, parse optional fields unambiguously, and retain wallet2 address
validation for the selected network. Parse and validation failures produce no
new diagnostics containing scanned data. No network request, saved-state format,
or Monero transaction behavior changes.

The JVM fixtures exercise opaque and hierarchical URI spelling, bare addresses,
literal colons in query values, ambiguous amounts, malformed percent escapes and
authority delimiters. The existing manual Tests workflow runs the fixtures in
both flavors. Wallet2 address checks and QR navigation still need device
coverage. Rollback is confined to the payment parser and its fixtures. Source
review alone cannot establish payment or anonymity guarantees.
