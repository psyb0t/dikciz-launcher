# Wi-Fi approval and local hotspot

wifiNetworksConfigure: action suggest/removeSuggestion/save plus ssid. suggest/save security open/wpa2/wpa3; protected passphrase8..63 printable ASCII. SSID1..32 UTF-8 bytes. Open networks reject passphrase. Android chooses whether to use suggestions. Save opens owner review then Android approval, expiring after15minutes. Read wifiConnectionStatus by returned id; saving is not connection.

wifiConnectionStart requests an Android NetworkSpecifier while Dikciz is foreground. Poll wifiConnectionStatus, list wifiConnectionsList, stop wifiConnectionStop. Callback networkHandle is an actual requested network, not a promise the default route changed.

wifiHotspotStart requests a local-only hotspot with Android-selected credentials. wifiHotspotStatus reads status; wifiHotspotStop closes by id. This is not internet tethering.

Nearby Wi-Fi/location grants, enabled hardware and owner approval apply. Durations/capacity are bounded. Connected-device notification Stop all releases requests. Status omits network passphrases except hotspot credentials explicitly returned to its owner.
