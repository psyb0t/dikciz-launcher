# Wi-Fi scans

wifiScanResults reads the existing cache. wifiScan requests a platform scan and waits for updated-results broadcast. It requires fine location, enabled location services and Wi-Fi or scan-always availability. It never silently enables them or connects a network.

wifiScan fields: timeoutMilliseconds=1..120000 (default 30000), limit=1..200 (default 50), offset=0..1000000 (default 0). Only one Dikciz scan waiter is active. All ordinary bindings share the same validation and permission review.

Success is state=observed, source=scan_completion_cache. Entries include SSID, BSSID, frequencyMHz, signalDbm, capabilities, timestampMicroseconds and observedAfterRequest. Compare timestamps to requestedAtElapsedMicroseconds. Old entries can remain in the cache. Broadcasts are global; do not claim exclusive attribution to this caller.

Use wifiScanResults for subsequent pages instead of launching another scan. Check hasMore and advance offset by returned count. Membership can change between reads.

wifi_scan_rejected means Android refused startScan, including throttling. wifi_scan_failed means no results update; wifi_scan_timeout means no completion before the deadline. Disabled services and permission denial are separate. A timeout releases the receiver but cannot cancel the platform's radio scan.

Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/wifi-control.md
