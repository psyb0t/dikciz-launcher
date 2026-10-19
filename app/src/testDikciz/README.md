# Dikciz grid unit tests

This flavour-specific JVM test source set exercises the pure grid rules in [`DikcizNativeGrid.kt`](../dikciz/kotlin/org/fossify/home/dikciz/DikcizNativeGrid.kt). It uses JUnit through the `testDikcizImplementation` dependency in [the app build](../../build.gradle.kts).

Both test files live in `kotlin/org/fossify/home/dikciz/`:

| File | Coverage |
| --- | --- |
| `DikcizNativeGridTest.kt` | Bounds, overlap, row-major free-cell search, preferred-span fallback, full pages, moves, resizes and pixel/cell conversion. |
| `DikcizGridSettingsTest.kt` | Conflicts after grid changes, the first offending widget, and persisted grid-setting names and values. |

Run these tests from the repository root with `make gradle GRADLE_TASK=:app:testDikcizDebugUnitTest`. `make app-test` selects the Fossify-compatible core tests instead.

Keep geometry tests independent of Android UI and device state. Native rendering, persistence and protocol behavior belong in the [live control suite](../../../tests/live_control/README.md). The [configuration guide](../../../docs/dikciz-configuration.md) defines the saved grid contract, and the [source map](../dikciz/README.md) identifies the runtime classes.
