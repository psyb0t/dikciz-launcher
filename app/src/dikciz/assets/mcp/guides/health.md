# Health Connect records

healthRecordsCatalogue reports provider availability, sixteen kinds, exact writeShape, units, timestamp fields, aggregate metrics and read/write grants. Read this before constructing records.

healthPermissionsRequest: kinds array, access read/write/readWrite, optional includeBackground/includeHistory. Android owner approval is required. Missing grants return permissionRequest and explicit retryRequired. Never replay writes automatically.

healthRecordsRead: kind, startTimeMilliseconds,endTimeMilliseconds, optional limit/pageToken. Follow nextPageToken/hasMore; max range366days and Android history rules. healthRecordsAggregate uses provider metrics and origin handling.

healthRecordsWrite: kind and1..50 records. Each has stable clientRecordId, optional clientRecordVersion, correct timestamps and values. UTC Unix milliseconds; manual-entry metadata; strict SDK bounds. healthRecordsDelete: kind, exact recordIds and confirm true. Android enforces record-origin ownership.

Kinds: steps,distance,activeCalories,totalCalories,heartRate,sleep,exercise,weight,height,hydration,bodyFat,bodyTemperature,bloodPressure,bloodGlucose,oxygenSaturation,floors. Native Health tab offers targeted grants and today's data. Missing provider/permission/records remain explicit.
