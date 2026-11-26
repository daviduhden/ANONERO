# CI-039 recurrence on bf1cf25

[Tests run 45691880945](https://github.com/daviduhden/ANONERO/actions/runs/45691880945)
tested `bf1cf25adb7969ffeebb26dab319593fbfe93819`. Android Lint for ANON and NERO
and preparation of the API 35 instrumented APKs failed in root classpath
resolution. In both jobs Maven Central returned HTTP 429 on the Kotlin Gradle
plugin 2.4.21 POM before the first task, on both the initial request sequence
and the single bounded retry. Neither job produced test or lint results.
The JVM, native archive and Debug APK jobs passed on independent runners.

The [earlier JVM job](https://github.com/daviduhden/ANONERO/actions/runs/45677086262/job/139427459051)
saved a Gradle cache two seconds after its failed configuration. The [lint
job](https://github.com/daviduhden/ANONERO/actions/runs/45691880945/job/139479241943)
and [instrumented job](https://github.com/daviduhden/ANONERO/actions/runs/45691880945/job/139499039439)
both restored that exact immutable entry. Even the successful runs that fetched
the plugin could not update it. The external rate limit caused the failures.
The incomplete cache kept otherwise reusable dependency requests on the network.

The four Gradle jobs in Tests now use the existing pinned setup-java action.
Its post-job save checks job success. The Gradle module cache has its own path
and the action separately caches the wrapper distribution. Only a dispatch on
the default branch testing its dispatched revision may write. setup-gradle
still validates wrapper integrity. The release generator strips the Tests
cache inputs and checks that Gradle caching stays disabled. No change to the
artifact repositories, pinned dependencies, retry count or device timeouts is
required. See the pinned [save implementation](https://github.com/actions/setup-java/blob/77dd5b2ce225ea11469928ac317850245a74ae78/src/cleanup-java.ts).

The release synchronization regression checks removal of cache inputs and
rejects an unexpected cache policy. A future authorized Tests run must resolve
the dependencies and save the cache after success. A subsequent run with the
same inputs should restore it and complete both jobs. Cold runners, cache
eviction and dependency changes can still require Maven Central.

## APK-001 — Debug package ABI coverage

Both Debug packaging jobs previously accepted the first path matching
`lib/*/libanonero.so`. A universal APK missing one supported architecture
could therefore pass. A short-circuiting grep could also close unzip's output
before it finished under pipefail. Both jobs now read the ZIP directory and
check for exactly one adapter at each required path, with matching little-endian
ELF class, machine and shared-object type. CI mutations cover an absent ABI and
a mislabeled library. This checks packaging and leaves device linkage and
execution to their respective gates. The release-specific checks still verify
the split and universal packages separately.

Static inspection of the downloaded ANON and NERO Debug APKs from run
45691880945 passed the entry and ELF checks for both ABIs. That result belongs
to `bf1cf25`. The updated workflow and its regression module await execution.

The owner gate resolves the requested ref once with the pinned checkout action.
Each job consumes its commit output, including jobs that wait for native
artifacts. This keeps a branch move during the build from mixing an archive
and Kotlin/JNI sources from different revisions. With an empty input, the
gate uses the dispatch SHA. The cache write condition compares resolved SHAs.

At `b09f9dc`, [Tests 45706621403](https://github.com/daviduhden/ANONERO/actions/runs/45706621403)
completed the four Gradle jobs and packaged both adapters. The successful JVM
job saved a cache that assembly and instrumentation subsequently restored.
Reuse after eviction, changed dependency pins or a new rate limit still depends
on a successful fetch.
