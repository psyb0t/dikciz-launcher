# NFC tags

NFC commands share schemas, permissions and the native executor across HTTP, WebSocket, MCP, HTML and Lua. Open and unlock Dikciz, enable NFC in Android settings, then present a tag. An absent adapter, disabled radio, foreground restriction or deadline is an explicit failure. Each request owns reader mode and closes its tag connection afterward.

## Read and select a tag

`nfcRead` / `dikciz_nfc_read` waits up to `timeoutMilliseconds`, default 30000 and range 1 through 120000. It returns `tagIdBase64`, Android technology names, NDEF support and available NDEF bytes. A tag without NDEF support still returns its identity and technologies.

Every tag operation accepts `expectedTagIdBase64` from a prior read. Other presented tags are ignored until the deadline. The ID is at most 64 decoded bytes. A tag ID is a selector, not proof of identity or authorization.

## Write NDEF

`nfcWrite` / `dikciz_nfc_write` requires `dataBase64` containing a complete Android-compatible NDEF message, at most 32768 decoded bytes. Malformed messages fail before reader activation. Read-only tags and insufficient capacity fail explicitly. `format: true` permits formatting an unformatted compatible tag; its default is false.

Successful writes report `state: written` and `verified`. An ordinary NDEF write attempts read-back comparison. Formatting reports `verified: false`. A lost tag, failure or timeout can leave partial effects. Inspect the tag before retrying a write.

## Raw transaction

`nfcTransceive` / `dikciz_nfc_transceive` requires `technology` and `dataBase64`. Supported technologies are IsoDep, NfcA, NfcB, NfcF, NfcV, MifareClassic and MifareUltralight, subject to the presented tag and phone. Android's per-technology maximum also applies.

`ioTimeoutMilliseconds` defaults to 5000, range 1 through 60000, for technologies with configurable I/O timeouts. The outer deadline still applies. Responses include Base64 bytes and their count, bounded to 32768 bytes. Raw commands may modify a tag. Use the tag manufacturer's protocol and do not treat successful transport as application-level success.

## Example

```json
{"type":"nfcRead","timeoutMilliseconds":30000}
```

No tag is emulated by this API. RF transactions need a compatible physical tag. See [hardware inventories](hardware-controls.md) and [command schemas](command-reference.md).
