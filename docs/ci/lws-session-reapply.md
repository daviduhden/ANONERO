# LWS-SESSION-001 — changing transport during a wallet switch

A static review of `bf1cf25` found that Network Settings captured a wallet,
waited for transport persistence and then loaded LWS settings for the current
profile. Its LWS handler selected the current wallet again after waiting for
the network mutex. If another wallet had opened, an operation initiated for
the old wallet could reinitialize a different wallet. A pending enable on a
full-node wallet could also become active before its intended restart.

An untrusted LWS receives the wallet's public address and view key on login
and can correlate requests. Profile consent, endpoint selection and the
originating wallet must agree before the handler may send a login. RPC node
selection and wallet startup also share the proxy and daemon helpers.
The correction binds these helpers to the captured wallet, and the LWS path
checks the profile, managed file and active light-wallet mode before reapply.
Transport preferences remain global. LWS preferences, TLS policy, wallet files
and native interfaces retain their existing formats.

The manual Tests source guard checks the bindings. On a device, hold proxy
acquisition or the wallet network mutex while switching profiles or reopening
the same profile. The delayed operation must never log the replacement wallet
in. Repeat with a pending LWS enable on a full-node wallet and verify that a
normal active LWS reconnect still succeeds. No live LWS or Tor result is
available for this change. Snapshot checks cannot establish native I/O
quiescence. If the reapply path regresses, keep the wallet offline and require
a wallet restart before applying the selected transport.

## LWS-TRUST-002 — Unchecked wallet2 trust updates

Source review at `b09f9dc` identified unchecked Boolean trust writes in
startup, network reconfiguration and refresh callback reconnect. A missing
handle returned false. A wallet2 exception could cross the JNI boundary.
There is no observed trust-policy failure in Tests 45706621403 and its JVM
and emulator cases do not perform a live LWS login.

The adapter now reads back the trust flag after setting it and returns false
for an unavailable handle or native exception. The pinned wallet2 setters and
getters delegate to the same wallet. All active login paths verify the
untrusted state before sending the address and view key to the selected LWS,
and reapply it when login returns or throws. Failure prevents starting
refresh. Offline cleanup still attempts to clear trust. This change preserves
the RPC trust policy, transport choice, consent and JNI symbol. An untrusted
LWS can still correlate the address, view key and requests it legitimately
receives. These checks cannot establish native handle lifetime during close.

The JVM regressions cover rejection before login, loss of the postcondition
and an exception during login. Verify both ARM links, trust readback and offline
behavior against a rejecting LWS on device. If rollback is required, keep LWS
offline when the untrusted policy cannot be confirmed.
