# Geocoding and GNSS status

locationGet reads a permitted cached or fresh position. geocode resolves supplied text/coordinates through Android's geocoding provider. gnssStatus observes satellite status. These are separate operations; a resolved address or visible satellite is not proof of the phone's current position.

## Geocoding

geocode, MCP dikciz_geocode, requires action=forward or action=reverse. Forward requires a nonblank query of at most 1024 characters and refuses coordinates. Reverse requires finite latitude within -90..90 and longitude within -180..180 and refuses query.

Optional language is a BCP-47 tag, at most 64 characters. limit is 1..10, default 5, selecting the maximum provider matches, not an exhaustive paginated address database. timeoutMilliseconds is 1..120000, default 30000. The bounded callback API requires Android 13 or newer.

Results contain items, limit, hasMore=false, source=android_geocoder and language. Address fields include nullable coordinates, address line, locality, administrative area, country code/name and postal code, each text field at most 1024 characters. Empty matches are valid. Android's provider can use a network service and receives the supplied query or coordinates.

Missing provider, timeout and provider failure return geocoder_unavailable, geocoder_timeout and geocoder_failed. Older Android returns unsupported_android_version. No synthetic address replaces a missing response. Provider accuracy and coverage vary.

## GNSS observation

gnssStatus, MCP dikciz_gnss_status, needs precise location access. It listens for a satellite-status callback for timeoutMilliseconds (1..5000, default 1000), then unregisters. It does not turn on GPS or start a tracking job. Location may need to be active elsewhere before Android supplies GNSS status.

A received status returns available=true, observedAtMilliseconds, total and an offset/limit page. Fields include satellite ID, constellation, signal C/N0 in dB-Hz, elevation/azimuth degrees, usedInFix, ephemeris/almanac flags and optional carrier frequency in Hz. This is status observation, not raw navigation-message capture or a location fix.

No GPS provider, rejected registration or no callback returns available=false with no_gps_provider, registration_rejected or no_status_during_window. Missing permission uses the common locationForeground owner-review procedure. Do not report a fix from unavailable status.

All ordinary entrypoints and local script/widget bindings share these commands. For background location subscriptions and precision policies, read the [scripting tutorial](scripting-tutorial.md) and [native device guide](native-device-control.md).
