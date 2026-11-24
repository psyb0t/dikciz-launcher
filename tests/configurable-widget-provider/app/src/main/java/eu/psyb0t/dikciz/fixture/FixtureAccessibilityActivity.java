package eu.psyb0t.dikciz.fixture;

import android.app.Activity;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public final class FixtureAccessibilityActivity extends Activity {
    private static final String ACTION_COUNT_STATE_KEY = "actionCount";
    private static final int INITIAL_ACTION_COUNT = 0;
    private static final String TOUCH_PROBE_TAG = "DikcizTouchProbe";
    // One line per completed touch with the first finger's samples, for the touch scorer.
    private static final String TOUCH_TRACE_TAG = "DikcizTouchTrace";
    private static final int MAX_TRACE_SAMPLES = 240;
    private static final String VERIFICATION_VERIFIED = "verified";
    private static final String VERIFICATION_UNVERIFIED = "unverified";
    private static final String VERIFICATION_UNAVAILABLE = "unavailable";

    private int actionCount = INITIAL_ACTION_COUNT;
    private TextView statusView;
    private TextView touchStatusView;
    private View actionButton;
    private int touchCount;
    private boolean trackingTouch;
    private float downX;
    private float downY;
    private int moveSamples;
    private int maxPointers;
    private double initialTwoPointerSpan;
    private double lastTwoPointerSpan;
    private final Rect buttonBounds = new Rect();
    private final StringBuilder trace = new StringBuilder();
    private int traceSamples;
    private int tracedPointerId;

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
        actionButton = findViewById(R.id.fixture_accessibility_action);
        touchStatusView = findViewById(R.id.fixture_accessibility_touch_status);
        actionButton.setOnClickListener(this::completeAction);
        updateTouchStatus();
        updateStatus();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        recordTouch(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                && actionButton.getGlobalVisibleRect(buttonBounds)
                && buttonBounds.contains((int) event.getRawX(), (int) event.getRawY())) {
            touchCount++;
            updateTouchStatus();
        }
        return super.dispatchTouchEvent(event);
    }

    private void recordTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            trackingTouch = true;
            downX = event.getX();
            downY = event.getY();
            moveSamples = 0;
            maxPointers = event.getPointerCount();
            initialTwoPointerSpan = Double.NaN;
            lastTwoPointerSpan = Double.NaN;
            trace.setLength(0);
            traceSamples = 0;
            tracedPointerId = event.getPointerId(0);
            appendTrace(event);
            return;
        }
        if (!trackingTouch) {
            return;
        }
        maxPointers = Math.max(maxPointers, event.getPointerCount());
        if (event.getPointerCount() == 2) {
            lastTwoPointerSpan = Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
            if (Double.isNaN(initialTwoPointerSpan)) {
                initialTwoPointerSpan = lastTwoPointerSpan;
            }
        }
        if (action == MotionEvent.ACTION_MOVE) {
            moveSamples += 1 + event.getHistorySize();
            appendTrace(event);
            return;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            trackingTouch = false;
            appendTrace(event);
            Log.i(TOUCH_TRACE_TAG,
                    "down_ms=" + event.getDownTime()
                            + " pointers=" + maxPointers
                            + " dpi=" + Math.round(getResources().getDisplayMetrics().xdpi)
                            + " target=" + targetBounds()
                            + " samples=" + trace);
            long durationMs = event.getEventTime() - event.getDownTime();
            String displacementPx = maxPointers == 1
                    ? Double.toString(Math.hypot(event.getX() - downX, event.getY() - downY))
                    : "unavailable";
            String spanChangePx = maxPointers == 2 && !Double.isNaN(initialTwoPointerSpan)
                    ? Double.toString(lastTwoPointerSpan - initialTwoPointerSpan)
                    : VERIFICATION_UNAVAILABLE;
            Log.i(TOUCH_PROBE_TAG,
                    "completed=" + (action == MotionEvent.ACTION_UP)
                            + " duration_ms=" + durationMs
                            + " move_samples=" + moveSamples
                            + " max_pointers=" + maxPointers
                            + " displacement_px=" + displacementPx
                            + " span_change_px=" + spanChangePx
                            + " android_verification=" + verifyInput(event));
        }
    }

    /** Appends the traced finger's samples as t:x:y in screen pixels, history first. */
    private void appendTrace(MotionEvent event) {
        int index = event.findPointerIndex(tracedPointerId);
        if (index < 0) {
            return;
        }
        float offsetX = event.getRawX() - event.getX();
        float offsetY = event.getRawY() - event.getY();
        for (int h = 0; h < event.getHistorySize(); h++) {
            addSample(event.getHistoricalEventTime(h) - event.getDownTime(),
                    event.getHistoricalX(index, h) + offsetX, event.getHistoricalY(index, h) + offsetY);
        }
        addSample(event.getEventTime() - event.getDownTime(), event.getX(index) + offsetX, event.getY(index) + offsetY);
    }

    private void addSample(long time, float x, float y) {
        if (traceSamples >= MAX_TRACE_SAMPLES) {
            return;
        }
        if (traceSamples > 0) {
            trace.append(',');
        }
        trace.append(time).append(':').append(Math.round(x * 10) / 10f).append(':').append(Math.round(y * 10) / 10f);
        traceSamples++;
    }

    private String targetBounds() {
        return actionButton.getGlobalVisibleRect(buttonBounds)
                ? buttonBounds.left + ":" + buttonBounds.top + ":" + buttonBounds.right + ":" + buttonBounds.bottom
                : "none";
    }

    private String verifyInput(MotionEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return VERIFICATION_UNAVAILABLE;
        }
        InputManager inputManager = getSystemService(InputManager.class);
        if (inputManager == null) {
            return VERIFICATION_UNAVAILABLE;
        }
        return inputManager.verifyInputEvent(event) == null
                ? VERIFICATION_UNVERIFIED : VERIFICATION_VERIFIED;
    }

    private void updateTouchStatus() {
        touchStatusView.setText(getString(R.string.fixture_accessibility_touch_count, touchCount));
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
