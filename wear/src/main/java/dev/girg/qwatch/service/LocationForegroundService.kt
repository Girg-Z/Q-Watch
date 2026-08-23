package dev.girg.qwatch.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.girg.qwatch.R
import dev.girg.qwatch.complication.CountdownComplicationService
import dev.girg.qwatch.complication.NextArtistComplicationService
import dev.girg.qwatch.complication.NowPlayingComplicationService
import dev.girg.qwatch.data.StageState
import dev.girg.qwatch.data.readSmartModeFlow
import dev.girg.qwatch.data.writeStageState
import dev.girg.qwatch.resolver.FestivalDay
import dev.girg.qwatch.resolver.FestivalSchedule
import dev.girg.qwatch.resolver.ResolveResult
import dev.girg.qwatch.resolver.StageConfidenceBuffer
import dev.girg.qwatch.resolver.StageResolver
import dev.girg.qwatch.resolver.TimetableData
import dev.girg.qwatch.resolver.confidenceKey
import dev.girg.qwatch.resolver.parseStages
import dev.girg.qwatch.resolver.parseTimetable
import dev.girg.qwatch.service.MotionState.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.ZonedDateTime
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var stageResolver: StageResolver
    private lateinit var schedule: FestivalSchedule

    /** The currently-running strategy loop/stream; cancelled when the mode toggles. */
    private var strategyJob: Job? = null

    /** Latest passive fix, opportunistically filled while smart mode is active. */
    @Volatile
    private var passiveFix: Location? = null

    private var activityPendingIntent: PendingIntent? = null
    private val activityReceiver = ActivityTransitionReceiver()
    private var activityReceiverRegistered = false

    // --- Dumb mode: the original continuous high-accuracy stream ------------------------------------

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            serviceScope.launch { publish(stageResolver.resolve(location.latitude, location.longitude, ZonedDateTime.now())) }
        }
    }

    /** Passive listener feeding [passiveFix] — a free fix cache, never drives the GPS radio itself. */
    private val passiveCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { passiveFix = it }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val timetable = loadTimetable() ?: run { Log.e(TAG, "timetable.json missing"); stopSelf(); return }
        val days = loadStages() ?: run { Log.e(TAG, "stages.json missing"); stopSelf(); return }
        stageResolver = StageResolver(timetable, days)
        schedule = FestivalSchedule(timetable)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!hasLocationPermission()) {
            serviceScope.launch {
                applicationContext.writeStageState(StageState(isGpsAvailable = false))
                requestComplicationUpdate()
            }
            stopSelf()
            return
        }

        // Swap strategy live whenever the dev toggle flips smart <-> dumb.
        serviceScope.launch {
            applicationContext.readSmartModeFlow().distinctUntilChanged().collect { smart ->
                applyMode(smart)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORCE_FETCH) {
            serviceScope.launch { forceFetch() }
        }
        return START_STICKY
    }

    /** Debug helper: grab one fix right now and publish the resolved state, bypassing all gates. */
    private suspend fun forceFetch() {
        if (!::stageResolver.isInitialized || !hasLocationPermission()) return
        val fix = awaitCurrentLocation()
        if (fix == null) {
            applicationContext.writeStageState(StageState(isGpsAvailable = false, lastUpdateMillis = System.currentTimeMillis()))
            requestComplicationUpdate()
            Log.d(TAG, "Force fetch: no fix")
            return
        }
        Log.d(TAG, "Force fetch: ${fix.latitude},${fix.longitude} acc=${fix.accuracy}")
        publish(stageResolver.resolve(fix.latitude, fix.longitude, ZonedDateTime.now()))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopStrategies()
        serviceScope.cancel()
    }

    // --- Strategy management -----------------------------------------------------------------------

    private fun applyMode(smart: Boolean) {
        stopStrategies()
        Log.d(TAG, "Applying ${if (smart) "SMART" else "DUMB"} location mode")
        if (smart) startSmartStrategy() else startDumbStrategy()
    }

    private fun stopStrategies() {
        strategyJob?.cancel()
        strategyJob = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
        fusedLocationClient.removeLocationUpdates(passiveCallback)
        passiveFix = null
        removeActivityUpdates()
    }

    // Permission is guaranteed: strategies only start after onCreate's hasLocationPermission() gate.
    @SuppressLint("MissingPermission")
    private fun startDumbStrategy() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, DUMB_INTERVAL_MS).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun startSmartStrategy() {
        // Free fix cache + motion signal, both only active while smart mode runs.
        val passiveRequest = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, PASSIVE_INTERVAL_MS).build()
        fusedLocationClient.requestLocationUpdates(passiveRequest, passiveCallback, Looper.getMainLooper())
        requestActivityUpdates()

        strategyJob = serviceScope.launch { runSmartLoop() }
    }

    // --- Smart loop --------------------------------------------------------------------------------

    private suspend fun runSmartLoop() {
        val buffer = StageConfidenceBuffer(BUFFER_CAPACITY, MAJORITY)
        val latestByKey = HashMap<String, ResolveResult>()
        var committedKey: String? = null
        var confirmedStage = false
        var wroteInactive = false

        while (coroutineContext.isActive) {
            val now = ZonedDateTime.now()

            if (!schedule.isOpen(now)) {
                if (!wroteInactive) {
                    publish(ResolveResult.FestivalInactive)
                    wroteInactive = true
                }
                buffer.reset()
                latestByKey.clear()
                committedKey = null
                confirmedStage = false
                delay(sleepUntilOpenMs(now))
                continue
            }
            wroteInactive = false

            val fix = freshPassiveFix() ?: awaitCurrentLocation()
            if (fix == null || !isUsableFix(fix)) {
                delay(RETRY_MS)
                continue
            }

            val resolved = stageResolver.resolve(fix.latitude, fix.longitude, now)
            val key = resolved.confidenceKey()
            latestByKey[key] = resolved

            val previousKey = committedKey
            committedKey = buffer.add(key)
            val committed = committedKey

            // Publish on a confirmed change, or to refresh set progress while the current fix still
            // agrees with the committed stage.
            if (committed != null && (committed != previousKey || key == committed)) {
                val result = if (key == committed) resolved else latestByKey[committed] ?: resolved
                publish(result)
                confirmedStage = result is ResolveResult.AtStage
            }

            delay(computeIntervalMs(MotionState.motion.value, confirmedStage))
        }
    }

    private fun sleepUntilOpenMs(now: ZonedDateTime): Long {
        val next = schedule.nextOpen(now) ?: return SCHEDULE_SLEEP_MS
        val untilMs = java.time.Duration.between(now, next).toMillis()
        return untilMs.coerceIn(RETRY_MS, SCHEDULE_SLEEP_MS)
    }

    private fun freshPassiveFix(): Location? {
        val fix = passiveFix ?: return null
        val age = System.currentTimeMillis() - fix.time
        return if (age in 0..PASSIVE_MAX_AGE_MS && fix.accuracy <= MAX_ACCURACY_M) fix else null
    }

    private fun isUsableFix(loc: Location): Boolean {
        if (loc.accuracy > MAX_ACCURACY_M) return false
        val age = System.currentTimeMillis() - loc.time
        return age in 0..MAX_FIX_AGE_MS
    }

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() above
    private suspend fun awaitCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun computeIntervalMs(motion: Motion, confirmed: Boolean): Long = when {
        confirmed && motion == Motion.MOVING -> ACTIVE_STILL_MS   // dancing at a confirmed stage
        confirmed -> CONFIRMED_MAX_MS
        motion == Motion.MOVING -> ACTIVE_MOVING_MS               // walking, no stage yet -> tighten
        else -> ACTIVE_STILL_MS
    }

    // --- Activity recognition ----------------------------------------------------------------------

    private fun requestActivityUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "ACTIVITY_RECOGNITION not granted; motion stays UNKNOWN")
            return
        }

        val transitions = listOf(
            DetectedActivity.STILL, DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT, DetectedActivity.RUNNING
        ).map { type ->
            ActivityTransition.Builder()
                .setActivityType(type)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }

        val intent = Intent(this, ActivityTransitionReceiver::class.java)
            .setAction(ActivityTransitionReceiver.ACTION)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        activityPendingIntent = pendingIntent

        ContextCompat.registerReceiver(
            this, activityReceiver, IntentFilter(ActivityTransitionReceiver.ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        activityReceiverRegistered = true

        ActivityRecognition.getClient(this)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent)
            .addOnFailureListener { Log.w(TAG, "Failed to request activity transitions", it) }
    }

    private fun removeActivityUpdates() {
        activityPendingIntent?.let { pi ->
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { ActivityRecognition.getClient(this).removeActivityTransitionUpdates(pi) }
            }
        }
        activityPendingIntent = null
        if (activityReceiverRegistered) {
            runCatching { unregisterReceiver(activityReceiver) }
            activityReceiverRegistered = false
        }
        MotionState.update(Motion.UNKNOWN)
    }

    // --- Shared publishing -------------------------------------------------------------------------

    private suspend fun publish(resolved: ResolveResult) {
        val state = when (resolved) {
            is ResolveResult.AtStage -> StageState(
                stageId = resolved.stageId,
                stageName = resolved.stageName,
                artistName = resolved.artistName,
                isGpsAvailable = true,
                isFestivalActive = true,
                lastUpdateMillis = System.currentTimeMillis(),
                setProgressPercent = resolved.setProgressPercent,
                minsToSetEnd = resolved.minsToSetEnd,
                nextArtistName = resolved.nextArtistName
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestComplicationUpdate() {
        val ctx = applicationContext
        listOf(
            NowPlayingComplicationService::class.java,
            CountdownComplicationService::class.java,
            NextArtistComplicationService::class.java
        ).forEach { serviceClass ->
            ComplicationDataSourceUpdateRequester
                .create(ctx, ComponentName(ctx, serviceClass))
                .requestUpdateAll()
        }
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

        /** Sent from the dev menu to force a one-shot location fetch. */
        const val ACTION_FORCE_FETCH = "dev.girg.qwatch.FORCE_FETCH"

        // Adaptive interval / gating constants (tunable).
        private const val DUMB_INTERVAL_MS = 30_000L
        private const val ACTIVE_MOVING_MS = 15_000L
        private const val ACTIVE_STILL_MS = 30_000L
        private const val CONFIRMED_MAX_MS = 120_000L
        private const val SCHEDULE_SLEEP_MS = 15 * 60_000L
        private const val RETRY_MS = 5_000L
        private const val PASSIVE_INTERVAL_MS = 30_000L
        private const val PASSIVE_MAX_AGE_MS = 45_000L
        private const val MAX_FIX_AGE_MS = 60_000L
        private const val MAX_ACCURACY_M = 50f
        private const val BUFFER_CAPACITY = 5
        private const val MAJORITY = 3
    }
}
