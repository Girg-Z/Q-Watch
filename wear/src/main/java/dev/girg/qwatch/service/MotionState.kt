package dev.girg.qwatch.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide latest motion, written by [ActivityTransitionReceiver], read by the smart loop. */
object MotionState {

    enum class Motion { STILL, MOVING, UNKNOWN }

    private val _motion = MutableStateFlow(Motion.UNKNOWN)
    val motion: StateFlow<Motion> = _motion

    fun update(motion: Motion) {
        _motion.value = motion
    }
}
