# Wi-Fi selection and local hotspot

These Android 10+ flows supplement [scan results](wifi-control.md). They preserve Android approval and foreground requirements. Shared tools are available through every ordinary binding; requesting or changing a network is mutating.

`wifiNetworksConfigure` takes action suggest/removeSuggestion/save and ssid. For suggest/save, security is open/wpa2/wpa3. Protected networks require an 8..63 character printable ASCII passphrase. Open networks reject a passphrase. SSIDs are 1..32 UTF-8 bytes. Credentials are passed to Android and excluded from persisted status.

A suggestion lets Android decide whether to connect. Saving opens a dedicated review screen, then Android's saved-network approval. Read `wifiConnectionStatus` using the returned ID for the observed approval result. Saving does not prove connection. Requests expire after fifteen minutes.

`wifiConnectionStart` requests an owner-approved local network with a NetworkSpecifier while Dikciz is foreground. It returns asynchronous lifecycle status, then networkHandle when Android reports a network. It does not silently replace the process or phone's default route. Use wifiConnectionStatus, wifiConnectionsList and wifiConnectionStop.

`wifiHotspotStart` requests an Android local-only hotspot. Android selects its credentials. Read them only for the intended local peer. Use wifiHotspotStatus and wifiHotspotStop. This is not internet tethering. Nearby Wi-Fi/location access and Android hardware support remain necessary.

Durations are bounded, inactive requests are retained within a fixed capacity, and a connected-device foreground notification offers Stop all. Open **Settings**, **Safety, journeys and devices**, **Wi-Fi** for the native forms. A denied approval, unavailable network or unsupported hotspot is reported explicitly.
