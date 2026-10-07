package eu.psyb0t.dikciz.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public final class FixtureAccessibilityActivity extends Activity {
    private static final String ACTION_COUNT_STATE_KEY = "actionCount";
    private static final int INITIAL_ACTION_COUNT = 0;

    private int actionCount = INITIAL_ACTION_COUNT;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            actionCount = savedInstanceState.getInt(
                    ACTION_COUNT_STATE_KEY,
                    INITIAL_ACTION_COUNT
            );
        }
        setContentView(R.layout.fixture_accessibility_activity);
        statusView = findViewById(R.id.fixture_accessibility_status);
        findViewById(R.id.fixture_accessibility_action).setOnClickListener(this::completeAction);
        updateStatus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(ACTION_COUNT_STATE_KEY, actionCount);
        super.onSaveInstanceState(outState);
    }

    private void completeAction(View ignoredView) {
        actionCount++;
        updateStatus();
    }

    private void updateStatus() {
        statusView.setText(getString(R.string.fixture_accessibility_action_count, actionCount));
    }
}
