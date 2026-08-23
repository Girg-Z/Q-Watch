package dev.girg.qwatch.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Builds the PendingIntent that opens the wear app on the stage list screen. Used as the tap action
 * for the watch-face complications, so tapping anywhere on the face opens the stage screen (the
 * now-playing complication spans the whole face).
 */
object AppLauncher {
    const val EXTRA_OPEN_STAGES = "open_stages"
    private const val REQUEST_OPEN_STAGES = 0x1A2C

    fun stages(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(context, "dev.girg.qwatch.presentation.MainActivity")
            putExtra(EXTRA_OPEN_STAGES, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_STAGES,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
