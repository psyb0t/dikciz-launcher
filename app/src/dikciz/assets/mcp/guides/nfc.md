# NFC tags

Open and unlock Dikciz and enable NFC. Use nfcStatus first. nfcRead waits for a presented tag and returns tagIdBase64, technologies and NDEF bytes when supported. timeoutMilliseconds defaults to 30000, range 1..120000.

nfcWrite requires dataBase64 containing a complete NDEF message, at most 32768 decoded bytes. format=true explicitly permits formatting an unformatted compatible tag. Inspect state and verified; formatting is not read-back verified.

nfcTransceive requires technology and dataBase64. Technologies: IsoDep, NfcA/B/F/V, MifareClassic, MifareUltralight. Android hardware limits apply. ioTimeoutMilliseconds defaults to 5000, range 1..60000, for technologies with configurable I/O timeout.

All three accept expectedTagIdBase64 from a prior read, up to 64 decoded bytes. Other tags are ignored. IDs select tags; they do not authenticate them. Raw commands and NDEF writes may alter a tag. Timeout/failure may leave partial effects, so inspect before retrying.

Each request releases reader mode and its connection. nfcTransceive sends one command per connection; there is no persistent session or command batch. A payment card with IsoDep/NfcA can have ndefSupported:false and still speak EMV APDUs. nfcRead does not decode EMV, and tag IDs are not account numbers. Lua/JS can encode raw exchanges, but application/authentication state may be lost between calls. A multi-command EMV reader, wallet provisioning and payment processing are not implemented. No adapter, disabled radio, foreground restriction and no tag before the deadline are explicit failures. Do not claim a tag transaction from emulator-only validation.

Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/nfc-control.md
