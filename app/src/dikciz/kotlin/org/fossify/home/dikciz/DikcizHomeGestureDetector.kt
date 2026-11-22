package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Recognises the upward home gesture that opens the app drawer.
 *
 * It watches raw activity touches rather than a child listener because the starter pages
 * can be covered edge to edge by one HTML widget, which would otherwise swallow the drag.
 * The gesture is only claimed once the drag clears both its distance and direction
 * thresholds, so a tap, a downward drag, or a mostly horizontal drag still reaches the page.
 */
internal class DikcizHomeGestureDetector(
    context: Context,
    private val activationBounds: () -> Rect?,
    private val isGestureEnabled: () -> Boolean,
    private val onDrawerRequested: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val activationHeightPixels = densityPixels(context, ACTIVATION_HEIGHT_DP)
    private val triggerDistancePixels = densityPixels(context, TRIGGER_DISTANCE_DP)

    private var initialX = NO_COORDINATE
    private var initialY = NO_COORDINATE
    private var isTrackingCandidate = false
    private var hasClaimedGesture = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginCandidate(event)
            MotionEvent.ACTION_MOVE -> return continueCandidate(event)
            MotionEvent.ACTION_POINTER_DOWN -> isTrackingCandidate = false
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                val wasClaimed = hasClaimedGesture
                reset()
                return wasClaimed
            }
        }
        return hasClaimedGesture
    }

    private fun beginCandidate(event: MotionEvent) {
        reset()
        if (!isGestureEnabled()) {
            return
        }
        val bounds = activationBounds() ?: return
        bounds.top = maxOf(bounds.top, bounds.bottom - activationHeightPixels)
        if (!bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
            return
        }
        initialX = event.rawX
        initialY = event.rawY
        isTrackingCandidate = true
    }

    private fun continueCandidate(event: MotionEvent): Boolean {
        if (hasClaimedGesture) {
            return true
        }
        if (!isTrackingCandidate || !isGestureEnabled()) {
            isTrackingCandidate = false
            return false
        }
        val horizontalDistance = abs(event.rawX - initialX)
        val verticalDistance = initialY - event.rawY
        if (maxOf(horizontalDistance, abs(verticalDistance)) < touchSlop) {
            return false
        }
        if (horizontalDistance > verticalDistance) {
            isTrackingCandidate = false
            return false
        }
        if (verticalDistance < triggerDistancePixels) {
            return false
        }
        isTrackingCandidate = false
        hasClaimedGesture = true
        onDrawerRequested()
        return true
    }

    private fun reset() {
        initialX = NO_COORDINATE
        initialY = NO_COORDINATE
        isTrackingCandidate = false
        hasClaimedGesture = false
    }

    private fun densityPixels(context: Context, valueDP: Int): Int {
        return (valueDP * context.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val ACTIVATION_HEIGHT_DP = 140
        const val NO_COORDINATE = 0F
        const val TRIGGER_DISTANCE_DP = 64
    }
}
