package dev.girg.qwatch.presentation

import androidx.compose.ui.graphics.Color

data class StageInfo(
    val id: String,
    val displayName: String,
    val timetableLocation: String,
    val color: Color
)

val FESTIVAL_STAGES = listOf(
    StageInfo("red",     "RED",     "RED",     Color(0xFFFF0000)),
    StageInfo("blue",    "BLUE",    "BLUE",    Color(0xFF0BDBEF)),
    StageInfo("black",   "BLACK",   "BLACK",   Color(0xFF9A9A9A)),
    StageInfo("indigo",  "INDIGO",  "INDIGO",  Color(0xFF3842DA)),
    StageInfo("brown",   "BROWN",   "BROWN",   Color(0xFF936037)),
    StageInfo("magenta", "MAGENTA", "MAGENTA", Color(0xFFFF008B)),
    StageInfo("uv",      "U.V.",    "UV",      Color(0xFFD492FF)),
    StageInfo("green",   "GREEN",   "GREEN",   Color(0xFF00FF00)),
    StageInfo("yellow",  "YELLOW",  "YELLOW",  Color(0xFFF1E300)),
    StageInfo("gold",    "GOLD",    "GOLD",    Color(0xFFBB9551)),
    StageInfo("orange",  "ORANGE",  "ORANGE",  Color(0xFFFF6500)),
    StageInfo("purple",  "PURPLE",  "PURPLE",  Color(0xFFA100FF)),
    StageInfo("silver",  "SILVER",  "SILVER",  Color(0xFFDADADA)),
    StageInfo("pink",    "PINK",    "PINK",    Color(0xFFEF81A0)),
)

fun stageColorFor(stageId: String?): Color =
    FESTIVAL_STAGES.find { it.id == stageId }?.color ?: Color(0xFF808080)

/** Maps a timetable location (e.g. "RED", "UV") back to its stage, or null if unknown. */
fun stageForLocation(location: String?): StageInfo? =
    FESTIVAL_STAGES.find { it.timetableLocation == location }
