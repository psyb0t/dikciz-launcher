package eu.psyb0t.dikciz.fixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.widget.TextView;
import java.util.Locale;

public final class FixtureMediaSessionActivity extends Activity {
    private static final boolean DEFAULT_PLAYING = false;
    private static final int FIRST_TRACK_NUMBER = 1;
    private static final int LAST_TRACK_NUMBER = 3;
    private static final int NOTIFICATION_ID = 41;
    private static final long MEDIA_ACTIONS = PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_SEEK_TO
            | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
    private static final long MEDIA_DURATION_MILLISECONDS = 180_000L;
    private static final long DEFAULT_MEDIA_POSITION_MILLISECONDS = 12_000L;
    private static final float MEDIA_PLAYBACK_SPEED = 1.0f;
    private static final String MEDIA_ARTIST = "Fixture Artist";
    private static final String MEDIA_CHANNEL_ID = "fixture-media";
    private static final String MEDIA_CHANNEL_NAME = "Fixture media";
    private static final String MEDIA_SESSION_TAG = "DikcizFixtureMediaSession";
    private static final String MEDIA_TITLE_FORMAT = "Fixture track %d";
    private static final String INITIAL_PLAYING_EXTRA = "fixtureInitialPlaying";
    private static final String PLAYING_STATUS = "playing";
    private static final String PAUSED_STATUS = "paused";
    private static final String VIEW_STATUS_FORMAT = "Fixture media session: track %d, %s, position %d";

    private MediaSession mediaSession;
    private TextView statusView;
    private boolean playing = DEFAULT_PLAYING;
    private long positionMilliseconds = DEFAULT_MEDIA_POSITION_MILLISECONDS;
    private int trackNumber = FIRST_TRACK_NUMBER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusView = new TextView(this);
        setContentView(statusView);
        playing = getIntent().getBooleanExtra(INITIAL_PLAYING_EXTRA, DEFAULT_PLAYING);
        mediaSession = new MediaSession(this, MEDIA_SESSION_TAG);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPause() {
                playing = false;
                updateSession();
            }

            @Override
            public void onPlay() {
                playing = true;
                updateSession();
            }

            @Override
            public void onSeekTo(long position) {
                positionMilliseconds = position;
                updateSession();
            }

            @Override
            public void onSkipToNext() {
                trackNumber = nextTrackNumber(trackNumber);
                updateSession();
            }

            @Override
            public void onSkipToPrevious() {
                trackNumber = previousTrackNumber(trackNumber);
                updateSession();
            }
        });
        mediaSession.setActive(true);
        updateSession();
    }

    @Override
    protected void onDestroy() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        mediaSession.release();
        super.onDestroy();
    }

    private void updateSession() {
        String title = String.format(MEDIA_TITLE_FORMAT, trackNumber);
        String playbackStatus = playing ? PLAYING_STATUS : PAUSED_STATUS;
        statusView.setText(
                String.format(
                        Locale.ROOT,
                        VIEW_STATUS_FORMAT,
                        trackNumber,
                        playbackStatus,
                        positionMilliseconds
                )
        );
        mediaSession.setMetadata(
                new MediaMetadata.Builder()
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, MEDIA_DURATION_MILLISECONDS)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, MEDIA_ARTIST)
                        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                        .build()
        );
        mediaSession.setPlaybackState(
                new PlaybackState.Builder()
                        .setActions(MEDIA_ACTIONS)
                        .setState(
                                playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                                positionMilliseconds,
                                MEDIA_PLAYBACK_SPEED
                        )
                        .build()
        );
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(
                new NotificationChannel(
                        MEDIA_CHANNEL_ID,
                        MEDIA_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                )
        );
        notificationManager.notify(
                NOTIFICATION_ID,
                new Notification.Builder(this, MEDIA_CHANNEL_ID)
                        .setContentText(title)
                        .setContentTitle(MEDIA_ARTIST)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()))
                        .build()
        );
    }

    private int nextTrackNumber(int currentTrackNumber) {
        if (currentTrackNumber == LAST_TRACK_NUMBER) {
            return FIRST_TRACK_NUMBER;
        }
        return currentTrackNumber + 1;
    }

    private int previousTrackNumber(int currentTrackNumber) {
        if (currentTrackNumber == FIRST_TRACK_NUMBER) {
            return LAST_TRACK_NUMBER;
        }
        return currentTrackNumber - 1;
    }
}
