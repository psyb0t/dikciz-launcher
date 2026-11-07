---
title: "Health Connect records"
description: "Read and write supported Health Connect records through Android-approved access, with resource discovery, time ranges and platform requirements."
permalink: /health-records/
nav_order: 4
parent: "Companion"
---

# Health Connect records

`healthRecordsCatalogue` reports provider availability, exact record kinds, timestamp/value shapes, aggregate metric names and current read/write grants. The installed catalogue is authoritative. Availability is separate from permission.

Supported kinds are steps, distance, activeCalories, totalCalories, heartRate, sleep, exercise, weight, height, hydration, bodyFat, bodyTemperature, bloodPressure, bloodGlucose, oxygenSaturation and floors. Units appear in field names. Interval records use startTimeMilliseconds/endTimeMilliseconds; instantaneous records use timeMilliseconds. Times are UTC Unix milliseconds.

Request only needed kinds with `healthPermissionsRequest`, `kinds` array and `access` read/write/readWrite. Optional includeBackground/includeHistory request Android's separate grants where supported. The phone owner must approve. A missing grant returns targeted recovery and requires explicit retry, never an automatic write replay.

`healthRecordsRead` requires kind and a time range, supports limit and opaque pageToken. Follow nextPageToken/hasMore. Ranges are at most 366 days; platform history restrictions still apply. `healthRecordsAggregate` uses the provider's metrics and origin handling rather than summing possibly duplicated raw records.

`healthRecordsWrite` accepts one to fifty records with stable clientRecordId, optional nonnegative clientRecordVersion, timestamps and values. Record validation and SDK bounds apply. Writes use manual-entry metadata. `healthRecordsDelete` requires exact recordIds and confirm true; Health Connect enforces data-origin ownership. It does not delete arbitrary records from other apps.

Open the native Health tab to inspect supported grants, request chosen access and read today's records. A provider, owner grants and actual stored records are required for meaningful results. These operations are not medical interpretation.
