# Bluetooth

Discover schemas before use. Android scan/connect access and enabled hardware are required. These commands never silently enable the radio.

bluetoothDevices lists bonded peers with pagination. bluetoothScan mode=ble|classic collects actual peers for durationMilliseconds (default 5000, maximum 120000), maximumDevices (default 50, maximum 200). Classic refuses to interrupt existing discovery.

bluetoothPair(address) returns paired only if already bonded. A new bond request returns submitted; check bluetoothDevices after Android confirmation.

bluetoothExchange requires address and serviceUuid, secure defaults true. Optional dataBase64 writes at most 65536 bytes; readBytes defaults 4096, maximum 65536, zero disables input. One read is not a complete application message. Deadline closes the socket. Partial output may precede failure.

bluetoothGatt requires address and action: discover, read, write, readDescriptor, writeDescriptor or observe. Discovery is paginated with offset/limit. Other actions require serviceUuid and characteristicUuid; descriptor actions also descriptorUuid. Use discovered serviceInstanceId/characteristicInstanceId to resolve duplicate UUIDs. Writes require dataBase64, maximum 512 bytes. writeType=default|noResponse|signed. Observe accepts maximumEvents (default 1, maximum 100); a subscribed window can expire with no events. Optional mtu=23..517.

Both transactions default timeoutMilliseconds=30000, maximum 120000. They close owned connections afterward. Verify the peripheral's application result before retrying writes. No-response writes do not prove application success.

Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/bluetooth-control.md
