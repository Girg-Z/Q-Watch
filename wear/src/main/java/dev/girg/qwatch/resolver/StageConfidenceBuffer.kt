package dev.girg.qwatch.resolver

/** Sentinel keys for the non-stage outcomes, kept distinct from any real stage id. */
const val KEY_BETWEEN = "__between__"
const val KEY_INACTIVE = "__inactive__"

fun ResolveResult.confidenceKey(): String = when (this) {
    is ResolveResult.AtStage -> stageId
    ResolveResult.BetweenStages -> KEY_BETWEEN
    ResolveResult.FestivalInactive -> KEY_INACTIVE
}

/**
 * Rolling buffer over the last [capacity] resolved outcomes. The committed value only changes once a
 * single outcome holds a [majority] of the buffer, so a stray fix cannot flip the displayed stage.
 */
class StageConfidenceBuffer(
    private val capacity: Int = 5,
    private val majority: Int = 3
) {
    private val buffer = ArrayDeque<String>()
    private var committedKey: String? = null

    /** Adds [key], re-evaluates the majority, and returns the currently committed key (may be null). */
    fun add(key: String): String? {
        buffer.addLast(key)
        while (buffer.size > capacity) buffer.removeFirst()

        val top = buffer.groupingBy { it }.eachCount().maxByOrNull { it.value }
        if (top != null && top.value >= majority) committedKey = top.key
        return committedKey
    }

    fun committed(): String? = committedKey

    fun reset() {
        buffer.clear()
        committedKey = null
    }
}
