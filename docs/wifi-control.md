# Wi-Fi scans

`wifiScanResults` reads Android's existing scan cache without triggering radio work. `wifiScan` / `dikciz_wifi_scan` requests a scan and waits for Android's results-updated broadcast. Both return paginated network records: SSID, BSSID, frequency MHz, RSSI dBm, capabilities and Android's elapsed timestamp in microseconds.

Fine location permission and enabled Android location services are required for a scan. Wi-Fi or Android's scan-always setting must permit scanning. The API does not silently enable either setting, choose a network or change connectivity.

## Request

```json
{"type":"wifiScan","timeoutMilliseconds":30000,"limit":50,"offset":0}
```

Timeout defaults to 30000 ms, range 1 through 120000. Limit is 1 through 200, default 50; offset is 0 through 1000000, default 0. A second Dikciz scan request cannot wait concurrently. The receiver is unregistered after success, failure, cancellation or timeout. Android offers no matching cancellation API for the already requested scan.

A completed result reports state: observed and source: scan_completion_cache. Each returned entry adds observedAfterRequest, computed from its Android elapsed timestamp against requestedAtElapsedMicroseconds. Old cache entries can coexist with new ones. Completion broadcasts are global, so the API does not attribute them exclusively to this caller.

Read subsequent pages with wifiScanResults to avoid triggering another scan for every page. Network membership can change between requests. Inspect timestamps rather than treating cache reads as fresh measurements.

## Failures

Android can throttle or reject scanning even with permission. wifi_scan_rejected means startScan returned false; wifi_scan_failed means the completion broadcast reported no update; wifi_scan_timeout means no completion arrived within the selected deadline. Disabled location or scanning has its own error. No failure is converted into a successful cached scan.

Use autoRequestPermissions only to ask the owner to review missing fine-location access. Granting it does not enable location services or bypass scan throttling.

See [hardware inventories](hardware-controls.md), [permissions](automation-control.md#request-missing-android-access) and [command schemas](command-reference.md).
