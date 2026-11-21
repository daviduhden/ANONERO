# Broadcast recovery regression

Identifier CI-PRE-BROADCAST-01. This is a preventive source finding. The
transaction-history signed-file broadcast placed its success state after local
history refresh and wallet storage. A refresh exception after native submission
could report a failed broadcast and invite retry. The review flow recorded
acceptance earlier, but ignored a false store result and logged an exception
from recovery.

The asset is the user's payment intent and the integrity of its broadcast status.
An unavailable disk, asynchronous wallet shutdown, or JNI exception can interrupt
local recovery after wallet2 reports acceptance. The app must not reinterpret a
confirmed successful submission as a failed submit. The wallet2 result remains
the acceptance boundary, followed by UI state and best-effort local recovery. The
change removes arbitrary diagnostics from these paths and adds no data
disclosure, persistent state, transport changes or native signatures.

The JVM fixture covers successful recovery, a false store result, an exception
after acceptance and cancellation. The existing manual Tests jobs run it for both
flavors. Instrumented coverage should exercise both entry points with an injected
post-acceptance fault. This change does not bind signed files to reviewed
transactions and does not resolve uncertain outcomes before wallet2 reports
acceptance. Roll back both UI paths and their shared helper together if the new
behavior regresses. Static review is not proof of fund safety or anonymity.
