package dev.girg.qwatch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.girg.qwatch.data.readStageStateFlow
import kotlinx.coroutines.flow.first

class NextArtistComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return makeData("Headhunterz")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = applicationContext.readStageStateFlow().first()
        return makeData(state.nextArtistName ?: "")
    }

    /** Name goes in TEXT, the static "NEXT" label in TITLE (so the watch face can stack them). */
    private fun makeData(name: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(name).build(),
            contentDescription = PlainComplicationText.Builder(
                if (name.isEmpty()) "" else "Next: $name"
            ).build()
        )
            .apply {
                if (name.isNotEmpty()) setTitle(PlainComplicationText.Builder("NEXT").build())
            }
            // Full-screen slot: carry the tap action so a tap landing here still opens the app.
            .setTapAction(AppLauncher.stages(applicationContext))
            .build()
}
