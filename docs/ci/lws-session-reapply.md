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
