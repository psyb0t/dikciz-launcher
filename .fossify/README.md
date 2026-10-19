# Fossify source integration

This directory records the imported Fossify release and the narrow patches needed to build Dikciz on that source. The [project overview](../README.md#fossify-source-updates) describes refresh commands; [`scripts/dikciz-upstream.sh`](../scripts/dikciz-upstream.sh) implements comparison, staging and replacement.

## Files

| File | Purpose |
| --- | --- |
| `release-marker.txt` | Upstream-generated identity of the imported Fossify release. Carry it with the selected upstream source; do not edit it by hand. |
| `overlays/app-build.gradle.kts.patch` | Registers the Dikciz flavour, Kotlin source sets and flavour-specific dependencies, and derives the Dikciz version from `VERSION`. |
| `overlays/gradle-wrapper.properties.patch` | Adds the checksum for the upstream Gradle distribution. |

The flavour patch must match [`app/build.gradle.kts`](../app/build.gradle.kts). Dikciz uses `MAJOR.MINOR.PATCH`, with minor and patch below 100, and computes Android's version code as `major * 10000 + minor * 100 + patch`. Fossify's version remains separate.

## Refresh invariants

The refresh script accepts a source directory inside the repository workspace. It checks for an executable wrapper, the main source set, the app build file and a valid upstream marker. Replacement requires a newer marker and `DIKCIZ_UPSTREAM_REFRESH=1`.

Staging starts from the selected upstream tree, restores the script's explicit list of Dikciz files and directories, and applies both patches with zero fuzz. An incoming path that collides with a preserved directory or a patch conflict stops staging. Source comparison excludes generated Gradle and build state.

Before replacement, the script copies and verifies a source backup. Git metadata and Android Lab state stay in place. The preserved product directories include [`app/src/dikciz/`](../app/src/dikciz/README.md), [`app/src/dikcizDebug/`](../app/src/dikcizDebug/README.md) and [`app/src/testDikciz/`](../app/src/testDikciz/README.md).

Preservation covers `overlays/` and this README, not every local file in `.fossify/`. Add any other Dikciz-owned file here to the script's preserve list, or a refresh drops it. Review the staged comparison before replacement.

[`scripts/test-upstream-refresh.sh`](../scripts/test-upstream-refresh.sh) checks preservation, backup verification and the build overlay. After a source refresh, verify the upstream-compatible core build, the Dikciz build and affected live behaviors, as described in [CONTRIBUTING.md](../CONTRIBUTING.md).
