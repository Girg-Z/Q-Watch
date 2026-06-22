package dev.girg.qwatch.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Builds the PendingIntent that opens the wear app on the Favourites screen. Used as the tap action
 * for complications so that tapping the watch face opens the app. The now-playing complication
 * (slot 0) covers the whole face, so this effectively makes a tap anywhere open the app.
 */
object FavouritesLauncher {
    const val EXTRA_OPEN_FAVOURITES = "open_favourites"
    private const val REQUEST_OPEN_FAVOURITES = 0x1A2B

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(context, "dev.girg.qwatch.presentation.MainActivity")
            putExtra(EXTRA_OPEN_FAVOURITES, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_FAVOURITES,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
