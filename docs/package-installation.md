# APK installation sets

`apkInstallSet` stages one base APK followed by its split APKs into one Android PackageInstaller session. Supply `apks: [{path: "...", sha256: "..."}]`; SHA-256 is optional, exact and verified while staging. The first file must be the base. There are at most 32 unique APKs, 512 MiB each and 1 GiB total. Paths obey the ordinary protected-file rules.

The installer checks readable APK containers and public package/signing metadata where Android can parse it. Android authoritatively validates split identities, versions, signatures and compatibility at commit. Unknown-source access and owner installation confirmation remain necessary. A queued or staged result is not installation success. Poll `apkInstallStatus` and inspect terminal callbacks.

`apkInstallCancel` requires a Dikciz-owned sessionId. It abandons a still-cancellable staging/pending session. cancellationAccepted false means the operation was terminal or too late. It never uninstalls an installed app. A raced actual success is recorded as installed; do not claim it was undone.

The existing `apkInstall` remains the standalone convenience entrypoint and shares the staging/callback machinery. See [files and APKs](files-and-apks.md) for upload, status and handoff.

Android may leave a cancelled session's confirmation visible. The cancellation result reports confirmationMayRemainOpen and instructions; close that confirmation before beginning another installation.
