package dev.girg.qwatch.festival

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --- Models (mirrors the wear module; no shared module exists yet) -----------------------------

data class TimetableEvent(val name: String, val start: String, val end: String)
data class TimetableLocation(val name: String, val events: List<TimetableEvent>)
data class TimetableData(val timezone: String, val locations: List<TimetableLocation>)

data class StageInfo(
    val id: String,
    val displayName: String,
    val timetableLocation: String,
    val color: Color
)

val FESTIVAL_STAGES = listOf(
    StageInfo("red", "RED", "RED", Color(0xFFFF0000)),
    StageInfo("blue", "BLUE", "BLUE", Color(0xFF0BDBEF)),
    StageInfo("black", "BLACK", "BLACK", Color(0xFF9A9A9A)),
    StageInfo("indigo", "INDIGO", "INDIGO", Color(0xFF3842DA)),
    StageInfo("brown", "BROWN", "BROWN", Color(0xFF936037)),
    StageInfo("magenta", "MAGENTA", "MAGENTA", Color(0xFFFF008B)),
    StageInfo("uv", "U.V.", "UV", Color(0xFFD492FF)),
    StageInfo("green", "GREEN", "GREEN", Color(0xFF00FF00)),
    StageInfo("yellow", "YELLOW", "YELLOW", Color(0xFFF1E300)),
    StageInfo("gold", "GOLD", "GOLD", Color(0xFFBB9551)),
    StageInfo("orange", "ORANGE", "ORANGE", Color(0xFFFF6500)),
    StageInfo("purple", "PURPLE", "PURPLE", Color(0xFFA100FF)),
    StageInfo("silver", "SILVER", "SILVER", Color(0xFFDADADA)),
    StageInfo("pink", "PINK", "PINK", Color(0xFFEF81A0)),
)

fun stageForLocation(location: String): StageInfo? =
    FESTIVAL_STAGES.find { it.timetableLocation == location }

// --- Parsing -----------------------------------------------------------------------------------

fun parseTimetable(json: String): TimetableData {
    val root = JSONObject(json)
    val timezone = root.getString("timezone")
    val locsArray = root.getJSONArray("locations")
    val locations = (0 until locsArray.length()).map { i ->
        val loc = locsArray.getJSONObject(i)
        val eventsArray = loc.getJSONArray("events")
        val events = (0 until eventsArray.length()).map { j ->
            val e = eventsArray.getJSONObject(j)
            TimetableEvent(e.getString("name"), e.getString("start"), e.getString("end"))
        }
        TimetableLocation(loc.getString("name"), events)
    }
    return TimetableData(timezone, locations)
}

// --- A single set, ready for the favourites UI -------------------------------------------------

data class FestivalSet(
    val location: String,
    val artist: String,
    val rawStart: String,   // "yyyy-MM-dd HH:mm" — used to build the favourite id
    val dayLabel: String,   // "SAT" / "SUN" etc.
    val startTime: String,  // "HH:mm"
    val endTime: String
)

class FestivalRepository(context: Context) {
    private val timetable = run {
        val json = context.assets.open("timetable.json").bufferedReader().readText()
        parseTimetable(json)
    }

    private val eventFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE")

    /** Stages that actually have at least one set, in festival order. */
    fun stagesWithSets(): List<StageInfo> =
        FESTIVAL_STAGES.filter { stage ->
            timetable.locations.find { it.name == stage.timetableLocation }?.events?.isNotEmpty() == true
        }

    /** All sets for a stage, chronological. */
    fun setsForLocation(location: String): List<FestivalSet> {
        val loc = timetable.locations.find { it.name == location } ?: return emptyList()
        return loc.events.map { e ->
            val start = LocalDateTime.parse(e.start, eventFmt)
            val end = LocalDateTime.parse(e.end, eventFmt)
            FestivalSet(
                location = location,
                artist = e.name,
                rawStart = e.start,
                dayLabel = start.format(dayFmt).uppercase(),
                startTime = start.format(timeFmt),
                endTime = end.format(timeFmt)
            )
        }.sortedBy { it.rawStart }
    }
}
