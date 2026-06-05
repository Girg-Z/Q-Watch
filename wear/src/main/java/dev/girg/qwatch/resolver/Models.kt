package dev.girg.qwatch.resolver

data class TimetableData(
    val timezone: String,
    val locations: List<TimetableLocation>
)

data class TimetableLocation(
    val name: String,
    val events: List<TimetableEvent>
)

data class TimetableEvent(
    val name: String,
    val start: String,
    val end: String
)

data class FestivalDay(
    val date: String,
    val stages: List<Stage>
)

data class Stage(
    val id: String,
    val displayName: String,
    val polygon: List<Pair<Double, Double>>
)
