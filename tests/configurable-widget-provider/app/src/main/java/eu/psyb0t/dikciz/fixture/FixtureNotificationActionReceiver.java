package eu.psyb0t.dikciz.fixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FixtureNotificationActionReceiver extends BroadcastReceiver {
    private static final String ACTION_INVOKE = "eu.psyb0t.dikciz.fixture.NOTIFICATION_ACTION";
    private static final String PREFERENCES_NAME = "dikciz_notification_action";
    private static final String ACTION_COUNT_KEY = "actionCount";
    private static final String PREFERENCES_WRITE_FAILURE_MESSAGE = "Fixture action count was not saved";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_INVOKE.equals(intent.getAction())) {
            return;
        }
        int actionCount = context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getInt(ACTION_COUNT_KEY, 0);
        boolean saved = context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(ACTION_COUNT_KEY, actionCount + 1)
                .commit();
        if (!saved) {
            throw new IllegalStateException(PREFERENCES_WRITE_FAILURE_MESSAGE);
        }
    }
}
