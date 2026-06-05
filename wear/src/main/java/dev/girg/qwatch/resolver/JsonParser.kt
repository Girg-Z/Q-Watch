package dev.girg.qwatch.resolver

import org.json.JSONObject

fun parseTimetable(json: String): TimetableData {
    val root = JSONObject(json)
    val timezone = root.getString("timezone")
    val locsArray = root.getJSONArray("locations")
    val locations = (0 until locsArray.length()).map { i ->
        val loc = locsArray.getJSONObject(i)
        val name = loc.getString("name")
        val eventsArray = loc.getJSONArray("events")
        val events = (0 until eventsArray.length()).map { j ->
            val e = eventsArray.getJSONObject(j)
            TimetableEvent(
                name = e.getString("name"),
                start = e.getString("start"),
                end = e.getString("end")
            )
        }
        TimetableLocation(name, events)
    }
    return TimetableData(timezone, locations)
}

fun parseStages(json: String): List<FestivalDay> {
    val root = JSONObject(json)
    val daysArray = root.getJSONArray("days")
    return (0 until daysArray.length()).map { i ->
        val day = daysArray.getJSONObject(i)
        val date = day.getString("date")
        val stagesArray = day.getJSONArray("stages")
        val stages = (0 until stagesArray.length()).map { j ->
            val feature = stagesArray.getJSONObject(j)
            val props = feature.getJSONObject("properties")
            val geom = feature.getJSONObject("geometry")
            val ring = geom.getJSONArray("coordinates").getJSONArray(0)
            val polygon = (0 until ring.length()).map { k ->
                val point = ring.getJSONArray(k)
                Pair(point.getDouble(0), point.getDouble(1)) // (lng, lat)
            }
            Stage(
                id = props.getString("id"),
                displayName = props.getString("displayName"),
                polygon = polygon
            )
        }
        FestivalDay(date, stages)
    }
}
