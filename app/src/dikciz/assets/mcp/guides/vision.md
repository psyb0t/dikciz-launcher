# Local images and documents

imageRecognize requires accessible path. mode text/barcodes/both, Latin OCR, bundled barcode model. Optional rotationDegrees 0/90/180/270, maxDimension320..4096 default2048, maximumResults default200, maximumTextCharacters default65536, timeoutMilliseconds default30000. Bounds refer to processed rotated pixels. Limit flags and oversize errors are explicit. At most two recognition jobs.

Input <=32MiB and64million source pixels. No image upload, decoded-link opening or instruction execution. Google ML Kit may send SDK performance/utilization metrics separately from image processing. Its terms apply: https://developers.google.com/ml-kit/terms.

documentScan accepts pages [{path,corners?}],1..20. Corners are normalized clockwise top-left/top-right/bottom-right/bottom-left x/y points. Optional grayscale defaults true. Returns cleaned JPEG pages and raster PDF in app capture storage. Shared two-job work bound; maximum16777216 processed pixels per document. Reduce maxDimension or split larger batches. No automatic edge detection/searchable text layer. Run imageRecognize on returned pages for OCR, fileRead for transfer and fileManage for explicit deletion.

Native Scan tab selects images and exposes recognition/PDF actions. Capture a camera photo separately with the camera API and required grant.
