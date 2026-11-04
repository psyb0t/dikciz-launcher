# APK sets and cancellation

Upload files through ordinary protected file APIs. apkInstallSet takes apks [{path,sha256?}], base first then splits. At most32 unique APKs,512MiB each,1GiB total. SHA-256 verifies while staging. Android authoritatively validates split names/package/version/signatures/compatibility. Unknown-source access and owner confirmation remain required.

Poll apkInstallStatus with sessionId. Staged/queued does not mean installed. Inspect actual terminal callback and package state.

apkInstallCancel with a Dikciz-owned sessionId abandons a still-cancellable staging/pending session. cancellationAccepted false means terminal or too late. A raced actual install success remains installed. Cancellation never uninstalls an app.

Standalone apkInstall shares the same staging/callback path. No silent approval bypass.
