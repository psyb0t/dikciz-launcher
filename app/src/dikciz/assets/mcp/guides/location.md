# Location, geocoding and GNSS

locationGet reads a recent permitted fix or waits for one. Check available, age, accuracy and source. timeoutMilliseconds=0 reads cache only. Location permission and Android foreground/background restrictions apply.

dikciz_geocode requires action=forward with nonblank query, or action=reverse with finite latitude (-90..90) and longitude (-180..180). Other action fields are refused. language is optional BCP-47, limit is 1..10 default 5, timeoutMilliseconds is 1..120000 default 30000. Android 13+ is required for bounded asynchronous geocoding.

Results contain bounded nullable address fields and coordinates from the installed Android provider. Empty matches are valid. The provider may send supplied text/coordinates to its network service. geocoder_unavailable, geocoder_timeout and geocoder_failed stay distinct. No invented address replaces failure.

dikciz_gnss_status needs fine location. It observes one satellite callback during timeoutMilliseconds 1..5000, default 1000, then unregisters. It does not activate GPS. Results page satellite IDs, constellation, signal, elevation/azimuth, fix-use flags and optional carrier frequency, plus observation time and total. No callback is available=false with no_status_during_window; that is not a location fix.

Use background Lua location subscriptions for existing event processing. These commands do not themselves create a geofence or durable route tracker.
