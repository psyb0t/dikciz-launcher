# Dikciz unit tests

This flavour-specific JVM test source set covers Unicode-safe USSD response budgets, SMS receipt aggregation and the pure grid rules in [`DikcizNativeGrid.kt`](../dikciz/kotlin/org/fossify/home/dikciz/DikcizNativeGrid.kt). It uses JUnit through the `testDikcizImplementation` dependency in [the app build](../../build.gradle.kts).

The test files live in `kotlin/org/fossify/home/dikciz/`:

| File | Coverage |
| --- | --- |
| `DikcizNativeGridTest.kt` | Bounds, overlap, grid reflow and overflow eviction, row-major free-cell search, preferred-span fallback, full pages, moves, resizes and pixel/cell conversion. |
| `DikcizUssdResponseTest.kt` | Empty/exact/shortened response budgets, complete supplementary characters, combining code points and numeric boundaries. |
| `DikcizSmsReceiptTest.kt` | Multipart sent/delivery callbacks, uncertain and failed reports, duplicate parts and invalid segment counts. |
| `DikcizGridSettingsTest.kt` | Conflicts after grid changes, the first offending widget, and persisted grid-setting names and values. |

Run these tests from the repository root with `make gradle GRADLE_TASK=:app:testDikcizDebugUnitTest`. `make app-test` selects the Fossify-compatible core tests instead.

Keep geometry tests independent of Android UI and device state. Native rendering, persistence and protocol behavior belong in the [live control suite](../../../tests/live_control/README.md). The [configuration guide](../../../docs/dikciz-configuration.md) defines the saved grid contract, and the [source map](../dikciz/README.md) identifies the runtime classes.
