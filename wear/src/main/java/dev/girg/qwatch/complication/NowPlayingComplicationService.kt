package dev.girg.qwatch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.girg.qwatch.data.readStageStateFlow
import kotlinx.coroutines.flow.first

class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) return null
        return makeData(value = 60f, hexColor = "#0BDBEF", artistName = "Sub Zero Project")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = applicationContext.readStageStateFlow().first()
        return when {
            !state.isGpsAvailable -> makeData(0f, "#606060", "GPS UNAVAILABLE")
            !state.isFestivalActive || state.stageId == null ->
                makeData(0f, "#303030", "")
            else -> makeData(
                value = state.setProgressPercent.toFloat(),
                hexColor = stageHexColor(state.stageId),
                artistName = state.artistName ?: ""
            )
        }
    }

    private fun stageHexColor(stageId: String): String = when (stageId) {
        "blue"                  -> "#0BDBEF"
        "red"                   -> "#FF0000"
        "black"                 -> "#9A9A9A"
        "indigo"                -> "#3842DA"
        "brown"                 -> "#936037"
        "magenta"               -> "#FF008B"
        "uv"                    -> "#D492FF"
        "green"                 -> "#00FF00"
        "yellow"                -> "#F1E300"
        "gold"                  -> "#BB9551"
        "orange_light_district" -> "#FF6500"
        "purple"                -> "#A100FF"
        "silver"                -> "#DADADA"
        "pink"                  -> "#EF81A0"
        else                    -> "#808080"
    }

    private fun makeData(value: Float, hexColor: String, artistName: String) =
        RangedValueComplicationData.Builder(
            value = value,
            min = 0f,
            max = 100f,
            contentDescription = PlainComplicationText.Builder(artistName).build()
        )
            .setText(PlainComplicationText.Builder(hexColor).build())
            .setTitle(PlainComplicationText.Builder(artistName).build())
            .build()
}
