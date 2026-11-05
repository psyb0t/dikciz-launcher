# Image recognition dependencies

Dikciz source retains its project license. The image-recognition feature additionally bundles Google's ML Kit text-recognition:16.0.1 and barcode-scanning:17.3.0 SDKs, models and their transitive Android dependencies. These are subject to [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms) and incorporated Google API terms. Do not describe the bundled models as GPL source.

Recognition processes image input and results on the device. Google states that the SDK can contact its servers for maintenance information and sends performance/utilization metrics. This is separate from image uploads. Dikciz does not upload images through imageRecognize or documentScan. [Google's installation documentation](https://developers.google.com/ml-kit/tips/installation-paths) describes bundled model behavior.

Pinned direct AAR checksums are verified by the Dikciz Gradle dependency check. Build integration is kept in the flavor dependencies and matching upstream overlay. The optional fixture regeneration command fetches a SHA-pinned MIT-licensed Nayuki QR generator; its source header is retained in the verified cache. Committed PNG fixtures need no generator download during ordinary live tests.

The flavor's ProGuard rules preserve ML Kit registrar constructors referenced through manifest metadata. Run recognition against the minified APK when checking a release; compilation does not exercise registrar loading.

## JavaScript interpreter

Background scripts bundle Mozilla Rhino 1.9.2 under the [Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/). The unmodified runtime and its source are published by [Mozilla Rhino](https://github.com/mozilla/rhino/releases/tag/Rhino1_9_2_Release). Dikciz pins its version and verifies its JAR checksum in every Dikciz variant. It runs locally in interpreted mode; direct Java class access is disabled.
