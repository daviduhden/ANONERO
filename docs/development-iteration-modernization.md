# Kotlin, JNI and transport hardening

The baseline is `64d9f5d`. The working tree was clean at the start. The latest available
Tests run, `43640612517` on `dff2ecb`, passed JVM, lint, static checks, native archives,
APK linking and the API 35 smoke job. That older result does not cover this iteration.

## Threat and regression review

Saved node credentials and operator identities must stay out of persisted diagnostics.
Malformed imported or locally saved records can cause parsers to include raw fields in
exception messages, and implicit stringification can disclose credentials or hosts.
Routing the active wallet to a replacement node also reveals connection timing to the
operator. Selection uses the existing local catalog and the configured transport, and
must stop if the replacement cannot be persisted. LWS retains its separately authorized
operator and view-key policy.

Preserve the existing node fields and encodings. A repository-wide search found no
consumer of the legacy `Node.fromString` path or of credential-bearing `Node.toString`.
Keep diagnostic messages fixed and node parsing independent of native library startup.
A regression fixture checks diagnostic redaction. The replacement and session-failure
paths require CI and device exercise with an unavailable or unwritable node store.
Rollback should revert only the affected behavior without rewriting the local store or
substituting an operator. Static review does not prove VPN routing, DNS behavior, or the
lifetime, security and anonymity properties of a live wallet.

LWS protects the relationship between the wallet address, view key and the operator
the current profile approved. A settings or profile change during proxy acquisition can
invalidate that approval or the network validation for the pending login. The startup
and reapply paths compare the captured profile, LWS settings and transport after the
suspension, before configuring the native client. A rejected operation leaves refresh
paused. This is a stale-request guard, with no new disclosed fields, network clients,
settings keys, or approval semantics. It does not serialize changes made concurrently
with a native I/O call. Validate on a device by changing transport or LWS settings while
proxy acquisition is pending and observing the old and new endpoints. Revert this code
as a unit if necessary, without changing saved LWS consent.

## Findings and changes

| Priority | Source finding | Change and regression evidence |
| --- | --- | --- |
| High | A failed coin lookup could turn an explicit selection into an empty selection, which the sweep path interprets as the entire account. Asynchronous form checks could outlive the recipient or omit changes to balance and spend mode. | Keep unresolved selections separate, require the complete requested set and recheck spendability before construction. Bind the request to the wallet and inputs captured before NERO key-image preflight. Exercise changed recipient, changed account, a now-frozen output and repeated taps on a device. |
| High | Byte-array read failures were translated to empty message text. Native transaction inputs allocated outside exception handling, and erased key-image list elements were cast without a type check. | Keep JNI read failures pending, preserve a valid empty message, validate list element types and catch C++ allocation/conversion failures. The existing JNI CI job runs the expanded source guard. Native linkage and malformed-input fixtures are still needed. |
| High | Pending LWS login could outlive the profile or settings used to authorize it. A replaced listener may still be in use upstream when its adapter is destroyed. | The startup and reapply guards reject stale snapshots before init/login. Listener ownership still requires a separate coordinated lifetime change because wallet2 pause does not join an active callback. |
| Medium | An external proxy's open TCP port did not prove SOCKS5 readiness. Trusted-first ranking applied even with the preference disabled, and endpoint-only matches could select a different configuration. | Require a bounded SOCKS5 no-auth greeting with no destination request. Apply the trust preference conditionally and retain an exact eligible configuration when available. JVM fixtures cover complete, rejected and truncated replies, trust order, RPC credentials and wrong chain. |
| Medium | Legacy node conversion methods mixed parsing with native startup and included node addresses or credentials in implicit diagnostics. Automatic node replacement ignored the persistence result. | Use the build chain to parse legacy JSON and choose default RPC ports. Keep the existing storage keys, remove the unused string parser, redact exception diagnostics and require a successful preferences write before using the replacement. JVM code covers redacted stringification. |

The SOCKS greeting exposes generic SOCKS protocol traffic to an already selected
endpoint. It sends no destination, wallet data or additional identifier. It verifies
protocol negotiation at probe time. It cannot establish provider identity, continuous
availability or DNS routing. All external providers still require the configured proxy.

## Feature coverage and pending evidence

- **UI and localization:** The single-send screen reuses its localized construction and
  recipient errors. The four locale catalogs require no new string keys. Validate timing,
  accessibility and dialog behavior on device.
- **Accounts, subaddresses, pay-to-many and outputs:** The existing wallet2 account APIs,
  exact-amount multi-destination path and freeze/thaw buttons remain wired. The new
  single-send preflight refuses to build an explicit spend from outputs that are no
  longer usable. Account switching during in-flight native work still needs a lease.
- **Message signing:** Valid message bytes still go directly to wallet2. An unreadable
  array no longer invokes the engine with empty input.
- **Stealth, decoy and duress:** The existing local controls and profile isolation remain
  in place. Device scenarios for locks, background sync, decoy switching, notifications
  and wipe remain pending. These controls do not establish plausible deniability.
- **Network and LWS:** The existing destinations and consent rules remain in place.
  External provider recovery, simultaneous native I/O and transport changes, Android
  per-app VPN inclusion and DNS require device or packet-level evidence. Legacy saved
  RPC trust ranking is distinct from the native RPC trust flag. Existing full-node
  init, reconnect and manual-selection code set the latter independently.
- **Build and dependencies:** Reviewed Gradle, CMake, the version catalog, workflows,
  Monero and the build inputs. No toolchain pin or dependency changes were made.
  Instrumentation, JVM and native linkage for this iteration remain unverified.
- **Airgapped broadcast:** NERO's signed-file identity binding still needs a wallet2
  capability and an independent transaction fixture. The single-send changes do not
  establish that binding.

Rollback each narrowly scoped change with its tests and contract documentation. Neither
the node storage format nor the JNI signatures change. Do not roll back application
signing identities or import old settings to work around the new guards.

A queued refresh callback can arrive after a wallet has been marked offline. Wallet2
must not re-initialize or repeat an LWS login from that callback while offline or when
an external transport has no current ready authorization. The callback checks the
existing session and transport state. It sends no additional data and changes no
settings. Device CI must cover offline/online transitions and an in-flight refresh as
part of the listener-lifetime work.
