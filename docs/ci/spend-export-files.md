# Spend-export file regression

Identifier CI-PRE-SPEND-EXPORT-01. The multi-recipient and QR export paths
could reuse stale data under fixed filenames, and the QR flow read the entire
file into memory even if the caller only needed a file export. The same stream
could report input failure as continued loading. No historical test execution
is associated with this static finding.

The assets are the user's intended spend data, profile-specific outputs, and
availability of the wallet interface. Stale or partial files can arise from
failed native writes and concurrent session changes. Oversized files can
exhaust memory during UR construction. A shared writer deletes the old
artifact before writing, requires regular nonempty output, and removes a
partial result on failure. QR reading rejects symbolic links and bounds
automatic UR construction at 16 MiB. Larger regular files remain available
through the explicitly requested file export. UI output is tied to the captured
wallet for preparation and save. Fixed filenames and interchange formats stay
compatible and no new log data, identifiers, storage locations, or network
requests are introduced.

Byte-level limits and failed-write cleanup have JVM fixtures in the existing
manual Tests workflow. Device CI should exercise actual native exports for both
flavors, session changes, and the Save dialog. These guards do not serialize
all other actors that use a fixed cache path and do not establish signed-file
binding. Rollback is confined to the spend writer, QR export UI and fixtures.
