package dev.girg.qwatch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/** Receives activity-transition broadcasts and folds them into [MotionState]. */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            if (event.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) continue
            val motion = when (event.activityType) {
                DetectedActivity.STILL -> MotionState.Motion.STILL
                DetectedActivity.WALKING,
                DetectedActivity.ON_FOOT,
                DetectedActivity.RUNNING -> MotionState.Motion.MOVING
                else -> null
            }
            motion?.let { MotionState.update(it) }
        }
    }

    companion object {
        const val ACTION = "dev.girg.qwatch.ACTIVITY_TRANSITION"
    }
}
