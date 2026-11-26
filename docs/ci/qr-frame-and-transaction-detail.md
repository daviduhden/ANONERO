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

## QR-002 — Detection and binding after scanner disposal

Static review of the CameraX advisory at `b09f9dc` found that the worker read
BoofCV's reusable detections even when processing a frame threw. The analyzer
also treated encoded JPEG bytes as luminance and trusted unverified strides.
An invalid camera frame could trigger an out-of-bounds access or redisplay a
payload from a preceding frame. The corrected path reads only supported YUV
planes, checks the accessible byte extent with wide arithmetic, and consumes
detections only after that detector pass succeeds. Instrumented regressions
cover padded and interleaved rows, short buffers and the caller's buffer position.

A separate ordering issue involved the asynchronous camera provider. A result
queued during dismissal could bind the camera after disposal or deliver a
payment URI or private wallet payload to the outgoing screen. The provider,
preview, analyzer and worker now belong to the permission-granted view and its
lifecycle owner. Teardown invalidates pending callbacks and unbinds those two
use cases. The worker owns UR assembly; results and haptics reach the UI on
the main thread after the active check. The dialog forwards a completed UR
through its declared callback. The CI source guard checks the scoped unbind
and main-thread delivery. Device scenarios must cover dismissal while the
provider is pending, permission revocation during scanning, delayed scan
results after a new scanner opens, and regular/inverted and multipart codes.
A compromised camera or locally modified wallet remains outside this guarantee.
The correction adds no permission, persistence, third-party recipient or change
to QR/UR formats. Reverting requires keeping stale sessions from publishing
results until an equivalent ownership fix is available.

## W-013 — Instrumentation compiler and preference lint warnings

[Tests 45706621403](https://github.com/daviduhden/ANONERO/actions/runs/45706621403)
compiled `AnimatedQrCodeSessionTest` for both flavors on API 35. Both
compilations emitted the same deprecation warning for the Compose JUnit rule.
The QR session regression now uses the v2 rule. Its explicit test-clock
advances exercise rendering before and after UR replacement under the new
dispatcher. No other project call to the legacy rule was found.

At the same SHA, each lint XML listed eight dependency advisories and two
`UseKtx` warnings. Secure wipe now uses the same checked editor batch as the
PIN throttle and transport settings. The remaining quick fix inside the helper
would discard the disk-write result because AndroidX edit returns Unit. A
method-scoped annotation documents the exception at that one implementation.
The API 35 failure-injection case for PinThrottle checks the observable result.
The [dependency inventory](../architecture/dependencies.md) records the three
catalog pin changes. Android Lint, Android test compilation and device execution
of this checkout remain pending.
