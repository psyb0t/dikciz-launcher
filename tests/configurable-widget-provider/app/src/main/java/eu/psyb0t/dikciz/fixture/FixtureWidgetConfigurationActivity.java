package eu.psyb0t.dikciz.fixture;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

public final class FixtureWidgetConfigurationActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        setResult(RESULT_CANCELED);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        setContentView(R.layout.fixture_widget_configuration);
        findViewById(R.id.fixture_cancel).setOnClickListener(this::cancelConfiguration);
        findViewById(R.id.fixture_save).setOnClickListener(this::saveConfiguration);
    }

    private void cancelConfiguration(View ignoredView) {
        finish();
    }

    private void saveConfiguration(View ignoredView) {
        RemoteViews views = ConfigurableFixtureWidgetProvider.createRemoteViews(
                this,
                appWidgetId,
                R.string.fixture_widget_status_ready
        );
        AppWidgetManager.getInstance(this).updateAppWidget(appWidgetId, views);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
