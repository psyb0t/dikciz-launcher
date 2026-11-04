# Retained Bluetooth, USB and NFC

deviceConnectionOpen returns a process-owned id. Exchange, inspect and close with deviceConnectionExchange/Read/Close. deviceConnectionsList pages metadata. Default lifetime300000ms, max3600000; eight active. Default operation timeout30000ms, max120000; transfer bound65536bytes. Timeout closes hardware; writes can have partial effects. All exchanges count as mutating.

kind rfcomm: address, serviceUuid, optional secure. Exchange optional dataBase64 and readBytes. Returned chunks are not message framing.

kind gatt: address. Exchange action discover/read/write/subscribe/unsubscribe/notifications. Use exact discovered UUIDs and instance IDs for duplicates. Notification cursors report bounded loss.

kind usb: deviceId, optional interfaceIndex. Missing access returns usbRequestAccess recovery. Exchange control requires requestType/request/value/index; bulk/interrupt requires endpointAddress. Use readBytes for input, dataBase64 for output.

kind nfc: foreground unlocked Dikciz plus presented tag; technology defaults IsoDep. Exchange transceive/readNdef/writeNdef. Other supported technologies include NfcA/B/F/V and supported Mifare. Tag loss is failure. No payment interpretation/card emulation.

Expiry, close and process death release hardware. Connected-device notification Stop all closes active connections. The optional Vehicle console template reads ELM327 RPM/coolant/voltage through paired RFCOMM with explicit framing. Real hardware verification remains separate from emulator results.
