# Live microphone PCM

Use microphoneStreamStart, microphoneStreamRead, microphoneStreamStop, microphoneStreamsList and microphoneStreamForget. MCP names are dikciz_microphone_stream_start/read/stop/forget and dikciz_microphone_streams_list. Discover each schema.

Start needs microphone permission and foreground Dikciz. Recording can continue behind another app under a dedicated Android microphone foreground service. Its notification has Stop recording. Notification blocking can hide it from the drawer; inspect notificationPostingEnabled and Android active apps. Privacy controls or access loss can terminate capture. This is microphone audio, not cellular call audio.

Defaults: 16000 Hz, mono, 100 ms chunks, 30000 ms maximum duration/startup timeout, 2097152 replay bytes, 1024 events and 60 minutes retention. inputDeviceId can prefer an audioDevices input. Inspect routedInputDeviceId after samples arrive. Android can reject a requested format.

Start returns streamId. Read with cursor=0 and optional waitMilliseconds up to 30000. Decode audioBase64 as signed little-endian PCM16 at returned sampleRateHz/channels. Each chunk has sequence, firstFrame, frames and observedAtElapsedNanoseconds. Decoded length is frames * channels * 2. Optional audioTimestampNanoseconds/audioFramePosition use a monotonic timebase and may be null.

Save nextCursor, drain hasMore and inspect historyGap/lostEvents. Whole events include start, audio and exit. A page holds at most 393216 encoded JSON bytes. Bounded retention evicts old chunks explicitly. Metadata is sampled before the optional wait, so poll again for current lifecycle state. Use waitMilliseconds=0 inside Lua event handlers.

Stop requests cancellation. Poll until stopped/expired/failed and hardwareReleaseConfirmed=true, then drain terminal events and forget. Cleanup failure retains hardware ownership and prevents forgetting until the app process restarts. A failed start may leave a retained record, so inspect list.

Streaming shares exclusive microphone ownership with recording, recognition and video with audio. Limits: eight retained records, 32 MiB reserved replay buffers, each buffer 65536..8388608 bytes, 1..4096 events, retention 1..1440 minutes, startup 1..120000 ms and duration 100..2147483647 ms. One encoded chunk must fit both the buffer and a response page. Hardware also constrains formats.

Audio history lives in process memory. Disconnecting and activity recreation preserve it; process death, force-stop, reboot or APK replacement lose it. The restart command recreates the activity within the same process and does not clear this history. Terminal expiry is pruned on subsequent operations. List supports offset/limit pagination. Audio payloads are not application log records. Explicit saves or durable jobs containing audio reads can persist the returned data.

Read docs/microphone-streams.md in the repository for the full lifecycle and operating limits.
