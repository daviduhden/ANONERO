# QR frame ownership and received transaction details

The QR analyzer released a frame only at the end of successful processing. A skipped
format, a missing camera image or an exception before that line could retain the frame
and prevent CameraX from delivering subsequent frames. The same repository search
found one `ImageAnalysis.Analyzer` implementation.

Incoming transaction details passed account zero to the address lookup regardless of
the account reported by wallet2. Subaddress indices are scoped to an account, so the
same index can name an unrelated address. No other fixed account-zero
`getSubaddress` lookup was found in app code.

## Boundary review

- Camera frames cross from CameraX into the scanner; decoded results go to its
  existing callback. The scanner owns closing each proxy once. A malformed frame or
  a failing consumer can otherwise stop progress. Decoded data can contain wallet
  identifiers or private payloads, so analysis must keep it out of diagnostics.
- The selected transaction carries the wallet2 account and subaddress indices. The
  detail screen passes both indices to the existing wallet lookup on the IO
  dispatcher and checks the history entry, wallet and profile before lookup and
  before publishing. Incorrect coordinates can attribute a receipt to another account
  and link that account with unrelated activity if the address is shared.
- These fixes add no data collection, network requests or new storage. QR content
  still comes from untrusted sources, and this change does not validate payloads or
  synchronize native wallet lifetimes. Camera formats, native interfaces, transaction
  data, and persisted formats retain their existing contracts.

## CI and rollback

- Run the instrumented tests in both flavors. Verify unsupported and missing frames
  close once, and ensure a frame is released if access to it throws.
- On a device, use an ephemeral wallet with distinct subaddresses at the same index
  in accounts zero and one. Confirm the incoming transaction detail matches account
  one, including its address when copied. Check the account-zero case and outbound
  transfer displays for regressions.
- Roll back each change independently if required. Until corrected, avoid making
  account-attribution decisions from the affected detail screen and recreate the
  scanner session if frame delivery stalls.

Static checks confirm call sites and ownership paths only. Camera delivery and wallet2
address resolution still require instrumented or device evidence.
