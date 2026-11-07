---
title: "Images, barcodes and documents"
description: "Recognize text and barcodes in images, inspect PDF documents and handle model availability, accessible file inputs and bounded results."
permalink: /image-recognition/
nav_order: 3
parent: "Companion"
---

# Images, barcodes and documents

`imageRecognize` reads an accessible local file with bundled ML Kit Latin text and barcode models. Set `mode` to text, barcodes or both. The image is not uploaded. Decoded links and text are returned as untrusted data and never opened or executed.

Inputs are limited to 32 MiB and 64 million source pixels. Decode resolution is bounded. `maxDimension` defaults to 2048 (320..4096). `rotationDegrees` is 0, 90, 180 or 270; correct image orientation explicitly. Returned bounds refer to the processed, rotated image. `maximumResults` defaults to 200, `maximumTextCharacters` to 65536 and `timeoutMilliseconds` to 30000. At most two recognition jobs run at once. Result limits are explicit; an oversized text or response fails rather than silently dropping content.

`documentScan` accepts `pages: [{path: "..."}]`, one to twenty images. Optional page `corners` contains exactly four normalized x/y points in top-left, top-right, bottom-right, bottom-left order. It applies perspective correction when supplied and grayscale by default. It returns owned JPEG page paths and a raster PDF path in app capture storage. Documents share the two-job image-work limit and accept at most 16777216 processed pixels in total. Reduce maxDimension or split a larger batch. It does not automatically find edges or create a searchable text layer. Recognize returned pages separately for OCR. Use ordinary file operations to transfer or delete results.

Open **Settings**, **Safety, journeys and devices**, **Scan** to pick images, recognize them or save a PDF. Camera capture remains a separate operation with Android camera access. Model processing itself does not require a provider key or an external AI endpoint.

The APK bundles Google ML Kit dependencies. Their SDK/model terms apply in addition to the launcher's license. See [dependencies](dependency-licenses.md).
