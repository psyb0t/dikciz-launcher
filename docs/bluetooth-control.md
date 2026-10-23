# Bluetooth discovery and transactions

These commands use the shared HTTP, WebSocket, MCP, HTML and Lua executor. Android Bluetooth access and an enabled adapter are required. Android 12 and later distinguish scan and connect access; older scans require location access. Requests do not silently enable the radio or bypass peripheral security.

## Discover and pair

`bluetoothScan` / `dikciz_bluetooth_scan` selects `mode: ble` or `classic`. `durationMilliseconds` defaults to 5000, range 1 through 120000. `maximumDevices` defaults to 50, range 1 through 200. Results report actual discovered addresses, nullable names, types, bond state and RSSI, plus count and whether the device limit was reached. A scan owns only its own discovery and releases it after completion. Classic discovery refuses to interrupt an existing scan.

`bluetoothPair` / `dikciz_bluetooth_pair` takes an explicit `address`. Already bonded devices report paired. A newly accepted bond request reports submitted, not completed pairing; Android may show owner confirmation. Check `bluetoothDevices` afterward.

## Classic RFCOMM

`bluetoothExchange` / `dikciz_bluetooth_exchange` requires `address` and canonical `serviceUuid`. `secure` defaults to true. Optional `dataBase64` writes at most 65536 decoded bytes. `readBytes` defaults to 4096, range 0 through 65536, and reads one available chunk. It does not promise a complete application message. Results report actual byte counts, Base64 input and end-of-stream state.

`timeoutMilliseconds` defaults to 30000, range 1 through 120000. A deadline closes the owned socket and frees the connection slot. A failure may follow partial output. Use the peer's protocol to confirm an operation before retrying.

## BLE GATT

`bluetoothGatt` / `dikciz_bluetooth_gatt` opens one owned connection for one action, then disconnects and closes it. Required fields are `address` and `action`. Actions:

| Action | Selection and result |
| --- | --- |
| `discover` | Optional serviceUuid filter; offset/limit pagination over service, characteristic and descriptor records. Results include properties, permissions and instance IDs. |
| `read`, `write` | serviceUuid and characteristicUuid; writes require dataBase64. |
| `readDescriptor`, `writeDescriptor` | Service and characteristic UUIDs plus descriptorUuid; writes require dataBase64. |
| `observe` | Service and characteristic UUIDs; enables its CCCD and collects at most maximumEvents, default 1 and maximum 100. |

All UUIDs use canonical hyphenated form. Duplicate UUIDs require discovered `serviceInstanceId` or `characteristicInstanceId`. Writes are bounded to 512 decoded bytes. Characteristic `writeType` is default, noResponse or signed. An accepted no-response write cannot prove the peer applied it.

Optional `mtu` is 23 through 517; the result reports the negotiated value when Android supplies it. The shared timeout defaults to 30000 ms, maximum 120000. Observation timeout after subscription returns the collected events with `windowExpired: true`; connection or subscription failure remains an error. Each event contains actual bytes and its timestamp. Connections are not retained between commands.

## Example

```json
{"type":"bluetoothScan","mode":"ble","durationMilliseconds":5000}
```

Discovery can return no devices. Successful GATT/RFCOMM exchange needs a compatible external peer. See [hardware inventories](hardware-controls.md), [USB](usb-control.md) and [command schemas](command-reference.md).
