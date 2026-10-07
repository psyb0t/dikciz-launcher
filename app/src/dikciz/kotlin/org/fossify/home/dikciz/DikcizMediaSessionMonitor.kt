package org.fossify.home.dikciz

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import org.json.JSONObject

internal class DikcizMediaSessionMonitor(
    private val context: Context,
) {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, DikcizNotificationListenerService::class.java)
    private val controllers = mutableMapOf<String, RegisteredMediaController>()
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener(::updateControllers)
    private var started = false

    fun start() {
        if (started || sessionManager == null) {
            return
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent)
            started = true
            updateControllers(sessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            clearControllers()
        }
    }

    fun stop() {
        if (started) {
            sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
            started = false
        }
        clearControllers()
    }

    private fun updateControllers(activeControllers: List<MediaController>?) {
        clearControllers()
        activeControllers.orEmpty().forEach { controller ->
            val callback = mediaControllerCallback(controller)
            controllers[controller.sessionToken.toString()] = RegisteredMediaController(controller, callback)
            controller.registerCallback(callback)
            dispatch(controller)
        }
    }

    private fun clearControllers() {
        controllers.values.forEach { registration ->
            registration.controller.unregisterCallback(registration.callback)
        }
        controllers.clear()
    }

    private fun mediaControllerCallback(controller: MediaController): MediaController.Callback {
        return object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                dispatch(controller)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                dispatch(controller)
            }
        }
    }

    private fun dispatch(controller: MediaController) {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        DikcizAutomationEventBus.enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.MediaSession,
                source = "$SOURCE_PREFIX${controller.packageName}",
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = "$COALESCING_PREFIX${controller.packageName}",
                payload = JSONObject()
                    .put(PAYLOAD_PACKAGE_NAME, controller.packageName)
                    .put(PAYLOAD_PLAYBACK_STATE, playbackState?.state ?: JSONObject.NULL)
                    .put(PAYLOAD_POSITION_MILLISECONDS, mediaPosition(playbackState))
                    .put(PAYLOAD_TITLE, boundedMetadata(metadata, MediaMetadata.METADATA_KEY_TITLE))
                    .put(PAYLOAD_ARTIST, boundedMetadata(metadata, MediaMetadata.METADATA_KEY_ARTIST))
                    .put(PAYLOAD_ALBUM, boundedMetadata(metadata, MediaMetadata.METADATA_KEY_ALBUM))
                    .put(PAYLOAD_DURATION_MILLISECONDS, mediaDuration(metadata)),
            ),
        )
    }

    private fun boundedMetadata(metadata: MediaMetadata?, key: String): String? {
        return metadata?.getString(key)?.take(MAXIMUM_METADATA_CHARACTERS)
    }

    private fun mediaDuration(metadata: MediaMetadata?): Any {
        if (metadata == null || !metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
            return JSONObject.NULL
        }
        return metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
    }

    private fun mediaPosition(playbackState: PlaybackState?): Any {
        val position = playbackState?.position ?: return JSONObject.NULL
        if (position < MINIMUM_MEDIA_POSITION_MILLISECONDS) {
            return JSONObject.NULL
        }
        return position
    }

    private data class RegisteredMediaController(
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    companion object {
        fun hasAccess(context: Context): Boolean {
            return DikcizAutomationStatus.hasNotificationListenerAccess(context)
        }

        fun activeSessionCount(context: Context): Int {
            if (!hasAccess(context)) {
                return 0
            }
            val sessionManager = context.getSystemService(MediaSessionManager::class.java) ?: return 0
            val listenerComponent = ComponentName(context, DikcizNotificationListenerService::class.java)
            return try {
                sessionManager.getActiveSessions(listenerComponent).size
            } catch (_: SecurityException) {
                0
            }
        }

        private const val COALESCING_PREFIX = "media-session:"
        private const val MAXIMUM_METADATA_CHARACTERS = 256
        private const val MINIMUM_MEDIA_POSITION_MILLISECONDS = 0L
        private const val PAYLOAD_ALBUM = "album"
        private const val PAYLOAD_ARTIST = "artist"
        private const val PAYLOAD_DURATION_MILLISECONDS = "durationMilliseconds"
        private const val PAYLOAD_PACKAGE_NAME = "packageName"
        private const val PAYLOAD_PLAYBACK_STATE = "playbackState"
        private const val PAYLOAD_POSITION_MILLISECONDS = "positionMilliseconds"
        private const val PAYLOAD_TITLE = "title"
        private const val SOURCE_PREFIX = "media-session:"
    }
}
