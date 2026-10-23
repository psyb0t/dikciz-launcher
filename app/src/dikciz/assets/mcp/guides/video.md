# Camera video recording

cameraVideoRecord / dikciz_camera_video_record records a bounded foreground H.264 MP4. Read cameraList and cameraCapabilities first. Capabilities include videoSizes and frameRateRanges. No preview or background recording service is started.

Options: cameraId defaults to first camera; maxDimension320..1920(default1280), durationMilliseconds1000..120000(default3000), timeoutMilliseconds1..300000(default30000), frameRate5..60(default30), bitRate500000..20000000(default4000000), orientation0/90/180/270(default0), withAudio boolean(defaultfalse).

The total deadline must cover the requested duration and allow camera startup. Longer recordings need an explicit timeout. Camera sizes and AE frame-rate ranges must support the request; Android's encoder can reject unsupported size/rate combinations. Orientation is an MP4 playback hint, not rotation of source pixels. The camera may use a variable frame-rate range containing the requested rate.

Camera permission and foreground eligibility are required. withAudio:true additionally requires microphone access and records AAC, not cellular call audio. Photo and video commands share camera ownership; microphone recording and speech recognition share the video's microphone ownership. Missing access uses the common permission-review flow. Set autoRequestPermissions only when owner review is intended, then explicitly retry.

Success requires MediaRecorder finalization, actual completed camera frames and Android media metadata. The result includes state:saved, generated path, mimeType:video/mp4, bytes, actual durationMilliseconds/width/height/rotation/hasAudio, observedCaptureFrames, requestedDurationMilliseconds/requestedFrameRate and chosen camera frame-rate minimum/maximum. Camera callback count is not an encoded-frame count.

Read/download the returned file through normal file APIs. No caller path is accepted. Failed or interrupted recordings discard incomplete files. Deadline and foreground-loss checks run during startup/recording; platform stop/cleanup calls may take additional time. No success is returned for an unfinalized MP4. Physical scene or sound quality is not verified by container metadata.

Use jobStart with a caller idempotency key for reconnectable results. Poll jobStatus and request jobCancel when needed. Cancellation is an interruption request; poll the job and check cleanup before starting replacement work. It does not roll back side effects.

[Hardware](dikciz://docs/guides/hardware), [Jobs](dikciz://docs/guides/jobs), [Files](dikciz://docs/guides/files).
