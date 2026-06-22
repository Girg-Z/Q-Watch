package dev.girg.qwatch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.girg.qwatch.data.readFavouriteIdsFlow
import dev.girg.qwatch.presentation.TimetableRepository
import kotlinx.coroutines.flow.first

/**
 * SHORT_TEXT complication that surfaces the user's immediately-next favourite set as a small,
 * secondary line on the watch face (e.g. "★ Headhunterz"). Only one favourite is shown here even
 * when several collide in time; the full list lives in the watch app's Favourites screen.
 *
 * Tapping the complication opens the watch app directly on the Favourites screen.
 */
class NextFavouriteComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return makeData("★ Headhunterz")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val ids = applicationContext.readFavouriteIdsFlow().first()
        val next = if (ids.isEmpty()) null
        else TimetableRepository(applicationContext).getNextFavourite(ids)
        val text = next?.let { "★ ${it.artist}" } ?: ""
        return makeData(text)
    }

    private fun makeData(text: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("Next favourite set").build()
        )
            .setTapAction(AppLauncher.stages(applicationContext))
            .build()
}
