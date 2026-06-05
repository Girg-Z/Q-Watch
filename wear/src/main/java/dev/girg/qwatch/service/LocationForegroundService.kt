package dev.girg.qwatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.girg.qwatch.R
import dev.girg.qwatch.complication.MainComplicationService
import dev.girg.qwatch.data.StageState
import dev.girg.qwatch.data.writeStageState
import dev.girg.qwatch.resolver.FestivalDay
import dev.girg.qwatch.resolver.ResolveResult
import dev.girg.qwatch.resolver.StageResolver
import dev.girg.qwatch.resolver.TimetableData
import dev.girg.qwatch.resolver.parseStages
import dev.girg.qwatch.resolver.parseTimetable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var stageResolver: StageResolver

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            serviceScope.launch {
                val now = ZonedDateTime.now()
                val resolved = stageResolver.resolve(location.latitude, location.longitude, now)
                val state = when (resolved) {
                    is ResolveResult.AtStage -> StageState(
                        stageId = resolved.stageId,
                        stageName = resolved.stageName,
                        artistName = resolved.artistName,
                        isGpsAvailable = true,
                        isFestivalActive = true,
                        lastUpdateMillis = System.currentTimeMillis()
                    )
                    ResolveResult.BetweenStages -> StageState(
                        isGpsAvailable = true,
                        isFestivalActive = true,
                        lastUpdateMillis = System.currentTimeMillis()
                    )
                    ResolveResult.FestivalInactive -> StageState(
                        isGpsAvailable = true,
                        isFestivalActive = false,
                        lastUpdateMillis = System.currentTimeMillis()
                    )
                }
                applicationContext.writeStageState(state)
                requestComplicationUpdate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val timetable = loadTimetable() ?: run { Log.e(TAG, "timetable.json missing"); stopSelf(); return }
        val days = loadStages() ?: run { Log.e(TAG, "stages.json missing"); stopSelf(); return }
        stageResolver = StageResolver(timetable, days)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        requestLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            serviceScope.launch {
                applicationContext.writeStageState(StageState(isGpsAvailable = false))
                requestComplicationUpdate()
            }
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun requestComplicationUpdate() {
        ComplicationDataSourceUpdateRequester
            .create(applicationContext, ComponentName(applicationContext, MainComplicationService::class.java))
            .requestUpdateAll()
    }

    private fun loadTimetable(): TimetableData? = try {
        parseTimetable(assets.open("timetable.json").bufferedReader().readText())
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse timetable.json", e)
        null
    }

    private fun loadStages(): List<FestivalDay>? = try {
        parseStages(assets.open("stages.json").bufferedReader().readText())
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse stages.json", e)
        null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Location tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("QWatch")
            .setContentText("Tracking festival stage")
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "LocationForegroundService"
        private const val CHANNEL_ID = "location_service"
        private const val NOTIFICATION_ID = 1
    }
}
