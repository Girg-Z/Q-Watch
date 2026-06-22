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
        return makeData("NEXT · Headhunterz")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = applicationContext.readStageStateFlow().first()
        return makeData(
            if (state.nextArtistName != null) "NEXT · ${state.nextArtistName}" else ""
        )
    }

    private fun makeData(text: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(text).build()
        )
            // Full-screen slot: carry the tap action so a tap landing here still opens the app.
            .setTapAction(FavouritesLauncher.pendingIntent(applicationContext))
            .build()
}
