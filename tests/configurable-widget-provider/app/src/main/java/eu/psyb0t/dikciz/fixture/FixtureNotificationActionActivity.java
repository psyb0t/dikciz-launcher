package eu.psyb0t.dikciz.fixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public final class FixtureNotificationActionActivity extends Activity {
    private static final String ACTION_INVOKE = "eu.psyb0t.dikciz.fixture.NOTIFICATION_ACTION";
    private static final String CHANNEL_ID = "fixture-notification-action";
    private static final String CHANNEL_NAME = "Fixture notification action";
    private static final int FIRST_NOTIFICATION_ID = 42;
    private static final int NEXT_NOTIFICATION_ID_INCREMENT = 1;
    private static final int PENDING_INTENT_REQUEST_CODE = 42;
    private static final String PREFERENCES_NAME = "dikciz_notification_action";
    private static final String ACTION_COUNT_KEY = "actionCount";
    private static final String NEXT_NOTIFICATION_ID_KEY = "nextNotificationId";
    private static final String PREFERENCES_WRITE_FAILURE_MESSAGE = "Fixture notification state was not saved";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        int notificationId = preferences.getInt(NEXT_NOTIFICATION_ID_KEY, FIRST_NOTIFICATION_ID);
        boolean saved = preferences
                .edit()
                .putInt(ACTION_COUNT_KEY, 0)
                .putInt(NEXT_NOTIFICATION_ID_KEY, nextNotificationId(notificationId))
                .commit();
        if (!saved) {
            throw new IllegalStateException(PREFERENCES_WRITE_FAILURE_MESSAGE);
        }
        postNotification(notificationId);
    }

    private int nextNotificationId(int notificationId) {
        if (notificationId == Integer.MAX_VALUE) {
            return FIRST_NOTIFICATION_ID;
        }
        return notificationId + NEXT_NOTIFICATION_ID_INCREMENT;
    }

    private void postNotification(int notificationId) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(
                new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                )
        );
        Intent actionIntent = new Intent(this, FixtureNotificationActionReceiver.class)
                .setAction(ACTION_INVOKE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                PENDING_INTENT_REQUEST_CODE,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        notificationManager.notify(
                notificationId,
                new Notification.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_input_add)
                        .setContentTitle(CHANNEL_NAME)
                        .setContentText(CHANNEL_NAME)
                        .addAction(android.R.drawable.ic_input_add, ACTION_INVOKE, pendingIntent)
                        .build()
        );
    }
}
