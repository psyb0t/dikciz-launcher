package eu.psyb0t.dikciz.fixture;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public final class ConfigurableFixtureWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_PRIMARY = "eu.psyb0t.dikciz.fixture.action.PRIMARY";
    private static final String ACTION_SECONDARY = "eu.psyb0t.dikciz.fixture.action.SECONDARY";
    private static final String ACTION_MEDIA_PREVIOUS = "eu.psyb0t.dikciz.fixture.action.MEDIA_PREVIOUS";
    private static final String ACTION_MEDIA_PLAY_PAUSE = "eu.psyb0t.dikciz.fixture.action.MEDIA_PLAY_PAUSE";
    private static final String ACTION_MEDIA_NEXT = "eu.psyb0t.dikciz.fixture.action.MEDIA_NEXT";
    private static final String ACTION_WEATHER_REFRESH = "eu.psyb0t.dikciz.fixture.action.WEATHER_REFRESH";
    private static final boolean DEFAULT_WEATHER_RECOVERED = false;
    private static final int DEFAULT_MEDIA_TRACK_NUMBER = 1;
    private static final int FIRST_MEDIA_TRACK_NUMBER = 1;
    private static final int INVALID_APP_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final int LAST_MEDIA_TRACK_NUMBER = 3;
    private static final int PENDING_INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT
            | PendingIntent.FLAG_IMMUTABLE;
    private static final int PREFERENCES_MODE = Context.MODE_PRIVATE;
    private static final int TRACK_NUMBER_INCREMENT = 1;
    private static final String MEDIA_STATE_PREFERENCES_NAME = "fixture_media_state";
    private static final String MEDIA_STATE_PLAYING_KEY_PREFIX = "playing_";
    private static final String MEDIA_STATE_TRACK_NUMBER_KEY_PREFIX = "track_number_";
    private static final String WEATHER_STATE_RECOVERED_KEY_PREFIX = "recovered_";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(
                    appWidgetId,
                    createRemoteViews(context, appWidgetId, R.string.fixture_widget_status_ready)
            );
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!isFixtureAction(action)) {
            super.onReceive(context, intent);
            return;
        }
        int appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                INVALID_APP_WIDGET_ID
        );
        if (!isOwnedAppWidgetId(context, appWidgetId)) {
            return;
        }
        if (isGenericAction(action)) {
            updateGenericAction(context, appWidgetId, action);
            return;
        }
        if (isMediaAction(action)) {
            updateMediaAction(context, appWidgetId, action);
            return;
        }
        updateWeatherAction(context, appWidgetId);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            removeMediaState(context, appWidgetId);
            removeWeatherState(context, appWidgetId);
        }
        super.onDeleted(context, appWidgetIds);
    }

    static RemoteViews createRemoteViews(Context context, int appWidgetId, int statusResource) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.fixture_widget);
        views.setTextViewText(R.id.fixture_widget_status, context.getString(statusResource));
        applyMediaState(context, appWidgetId, views);
        applyWeatherState(context, appWidgetId, views);
        if (appWidgetId == INVALID_APP_WIDGET_ID) {
            return views;
        }
        views.setOnClickPendingIntent(
                R.id.fixture_primary_action,
                actionPendingIntent(context, appWidgetId, ACTION_PRIMARY)
        );
        views.setOnClickPendingIntent(
                R.id.fixture_secondary_action,
                actionPendingIntent(context, appWidgetId, ACTION_SECONDARY)
        );
        views.setOnClickPendingIntent(
                R.id.fixture_media_previous_action,
                actionPendingIntent(context, appWidgetId, ACTION_MEDIA_PREVIOUS)
        );
        views.setOnClickPendingIntent(
                R.id.fixture_media_play_pause_action,
                actionPendingIntent(context, appWidgetId, ACTION_MEDIA_PLAY_PAUSE)
        );
        views.setOnClickPendingIntent(
                R.id.fixture_media_next_action,
                actionPendingIntent(context, appWidgetId, ACTION_MEDIA_NEXT)
        );
        views.setOnClickPendingIntent(
                R.id.fixture_weather_refresh_action,
                actionPendingIntent(context, appWidgetId, ACTION_WEATHER_REFRESH)
        );
        return views;
    }

    private static void updateGenericAction(Context context, int appWidgetId, String action) {
        int statusResource = ACTION_PRIMARY.equals(action)
                ? R.string.fixture_widget_status_primary
                : R.string.fixture_widget_status_secondary;
        AppWidgetManager.getInstance(context).updateAppWidget(
                appWidgetId,
                createRemoteViews(context, appWidgetId, statusResource)
        );
    }

    private static void updateMediaAction(Context context, int appWidgetId, String action) {
        MediaState updatedState = updatedMediaState(mediaState(context, appWidgetId), action);
        saveMediaState(context, appWidgetId, updatedState);
        AppWidgetManager.getInstance(context).updateAppWidget(
                appWidgetId,
                createRemoteViews(context, appWidgetId, R.string.fixture_widget_status_ready)
        );
    }

    private static void updateWeatherAction(Context context, int appWidgetId) {
        saveWeatherRecovered(context, appWidgetId);
        AppWidgetManager.getInstance(context).updateAppWidget(
                appWidgetId,
                createRemoteViews(context, appWidgetId, R.string.fixture_widget_status_ready)
        );
    }

    private static boolean isFixtureAction(String action) {
        return isGenericAction(action) || isMediaAction(action) || isWeatherAction(action);
    }

    private static boolean isGenericAction(String action) {
        return ACTION_PRIMARY.equals(action) || ACTION_SECONDARY.equals(action);
    }

    private static boolean isMediaAction(String action) {
        return ACTION_MEDIA_PREVIOUS.equals(action)
                || ACTION_MEDIA_PLAY_PAUSE.equals(action)
                || ACTION_MEDIA_NEXT.equals(action);
    }

    private static boolean isWeatherAction(String action) {
        return ACTION_WEATHER_REFRESH.equals(action);
    }

    private static boolean isOwnedAppWidgetId(Context context, int appWidgetId) {
        if (appWidgetId == INVALID_APP_WIDGET_ID) {
            return false;
        }
        AppWidgetProviderInfo widgetInfo = AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(appWidgetId);
        return widgetInfo != null && new ComponentName(context, ConfigurableFixtureWidgetProvider.class)
                .equals(widgetInfo.provider);
    }

    private static void applyMediaState(Context context, int appWidgetId, RemoteViews views) {
        MediaState state = mediaState(context, appWidgetId);
        int statusResource = state.isPlaying
                ? R.string.fixture_widget_media_playing
                : R.string.fixture_widget_media_paused;
        views.setTextViewText(
                R.id.fixture_media_status,
                context.getString(statusResource, state.trackNumber)
        );
    }

    private static MediaState mediaState(Context context, int appWidgetId) {
        SharedPreferences preferences = context.getSharedPreferences(
                MEDIA_STATE_PREFERENCES_NAME,
                PREFERENCES_MODE
        );
        int trackNumber = preferences.getInt(
                mediaStateKey(MEDIA_STATE_TRACK_NUMBER_KEY_PREFIX, appWidgetId),
                DEFAULT_MEDIA_TRACK_NUMBER
        );
        if (trackNumber < FIRST_MEDIA_TRACK_NUMBER || trackNumber > LAST_MEDIA_TRACK_NUMBER) {
            trackNumber = DEFAULT_MEDIA_TRACK_NUMBER;
        }
        return new MediaState(
                trackNumber,
                preferences.getBoolean(
                        mediaStateKey(MEDIA_STATE_PLAYING_KEY_PREFIX, appWidgetId),
                        false
                )
        );
    }

    private static void applyWeatherState(Context context, int appWidgetId, RemoteViews views) {
        int statusResource = weatherRecovered(context, appWidgetId)
                ? R.string.fixture_weather_recovered
                : R.string.fixture_weather_unavailable;
        views.setTextViewText(R.id.fixture_weather_status, context.getString(statusResource));
    }

    private static boolean weatherRecovered(Context context, int appWidgetId) {
        return context.getSharedPreferences(MEDIA_STATE_PREFERENCES_NAME, PREFERENCES_MODE)
                .getBoolean(
                        mediaStateKey(WEATHER_STATE_RECOVERED_KEY_PREFIX, appWidgetId),
                        DEFAULT_WEATHER_RECOVERED
                );
    }

    private static MediaState updatedMediaState(MediaState state, String action) {
        if (ACTION_MEDIA_PREVIOUS.equals(action)) {
            return new MediaState(previousTrackNumber(state.trackNumber), state.isPlaying);
        }
        if (ACTION_MEDIA_PLAY_PAUSE.equals(action)) {
            return new MediaState(state.trackNumber, !state.isPlaying);
        }
        return new MediaState(nextTrackNumber(state.trackNumber), state.isPlaying);
    }

    private static int previousTrackNumber(int trackNumber) {
        if (trackNumber == FIRST_MEDIA_TRACK_NUMBER) {
            return LAST_MEDIA_TRACK_NUMBER;
        }
        return trackNumber - TRACK_NUMBER_INCREMENT;
    }

    private static int nextTrackNumber(int trackNumber) {
        if (trackNumber == LAST_MEDIA_TRACK_NUMBER) {
            return FIRST_MEDIA_TRACK_NUMBER;
        }
        return trackNumber + TRACK_NUMBER_INCREMENT;
    }

    private static void saveMediaState(Context context, int appWidgetId, MediaState state) {
        context.getSharedPreferences(MEDIA_STATE_PREFERENCES_NAME, PREFERENCES_MODE)
                .edit()
                .putInt(mediaStateKey(MEDIA_STATE_TRACK_NUMBER_KEY_PREFIX, appWidgetId), state.trackNumber)
                .putBoolean(mediaStateKey(MEDIA_STATE_PLAYING_KEY_PREFIX, appWidgetId), state.isPlaying)
                .apply();
    }

    private static void removeMediaState(Context context, int appWidgetId) {
        context.getSharedPreferences(MEDIA_STATE_PREFERENCES_NAME, PREFERENCES_MODE)
                .edit()
                .remove(mediaStateKey(MEDIA_STATE_TRACK_NUMBER_KEY_PREFIX, appWidgetId))
                .remove(mediaStateKey(MEDIA_STATE_PLAYING_KEY_PREFIX, appWidgetId))
                .apply();
    }

    private static void saveWeatherRecovered(Context context, int appWidgetId) {
        context.getSharedPreferences(MEDIA_STATE_PREFERENCES_NAME, PREFERENCES_MODE)
                .edit()
                .putBoolean(
                        mediaStateKey(WEATHER_STATE_RECOVERED_KEY_PREFIX, appWidgetId),
                        true
                )
                .apply();
    }

    private static void removeWeatherState(Context context, int appWidgetId) {
        context.getSharedPreferences(MEDIA_STATE_PREFERENCES_NAME, PREFERENCES_MODE)
                .edit()
                .remove(mediaStateKey(WEATHER_STATE_RECOVERED_KEY_PREFIX, appWidgetId))
                .apply();
    }

    private static String mediaStateKey(String prefix, int appWidgetId) {
        return prefix + appWidgetId;
    }

    private static PendingIntent actionPendingIntent(Context context, int appWidgetId, String action) {
        Intent intent = new Intent(context, ConfigurableFixtureWidgetProvider.class)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getBroadcast(context, appWidgetId, intent, PENDING_INTENT_FLAGS);
    }

    private static final class MediaState {
        private final int trackNumber;
        private final boolean isPlaying;

        private MediaState(int trackNumber, boolean isPlaying) {
            this.trackNumber = trackNumber;
            this.isPlaying = isPlaying;
        }
    }
}
