# W-008 — Android API selection for the OpenSSL build

The ARM build logs for [44932222742](https://github.com/daviduhden/ANONERO/actions/runs/44932222742/job/136931166373)
show that the pinned OpenSSL configuration uses API 29 Clang wrappers and also
passes `-D__ANDROID_API__=29` to compilation. The NDK wrapper already supplies
the macro. Repeating its definition produces `-Wmacro-redefined` on both ABIs.

The [pinned Android configuration](https://raw.githubusercontent.com/openssl/openssl/openssl-3.5.9/Configurations/15-android.conf)
reads the old define as a selector for the wrapper. Removing the flag alone
would allow Configure to select the NDK's maximum API. The
[upstream issue](https://github.com/openssl/openssl/issues/18561) documents the
same behavior. The related recipe scan covered other Android dependencies and
the APK build. They select the API through the compiler wrapper or CMake's
Android platform parameter.

## Build trust boundary

Affected assets are Android API 29 compatibility, deterministic ABI selection
and the integrity of the crypto archives linked into the application. A
misconfigured build or unexpected upstream source change can silently select
newer system APIs. The patch changes only the pinned Configure input for Android:
pass the explicit API level separately from compiler flags, check the selected
wrapper and preserve the NDK macro. No protocol, key, algorithm, runtime data or
network behavior changes. Keep the archive checksum, NDK pin, app API floor and
desktop target. Reject a patch mismatch or unexpected compiler before building.

For rollback, revert the configure patch and invocation as a unit. That restores
the documented warning and retains API 29. CI must confirm the wrapper for both
ABIs, no recurrence of this macro warning, successful APK linking and API 29
compatibility. Patch application and shell lint provide static evidence only.
