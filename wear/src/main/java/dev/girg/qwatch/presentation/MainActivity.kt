package dev.girg.qwatch.presentation

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dev.girg.qwatch.complication.AppLauncher
import dev.girg.qwatch.complication.NextArtistComplicationService
import dev.girg.qwatch.complication.NowPlayingComplicationService
import dev.girg.qwatch.complication.StageComplicationService
import dev.girg.qwatch.data.StageState
import dev.girg.qwatch.data.readSmartModeFlow
import dev.girg.qwatch.data.readStageStateFlow
import dev.girg.qwatch.data.writeSmartMode
import dev.girg.qwatch.data.writeStageState
import dev.girg.qwatch.presentation.theme.QWatchTheme
import dev.girg.qwatch.service.LocationForegroundService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class Screen {
    object Main : Screen()
    object Debug : Screen()
    object Favourites : Screen()
    data class Timetable(val stageId: String) : Screen()
}

class MainActivity : ComponentActivity() {

    // Bumped whenever a new intent asks us to jump to the stage list screen (e.g. watch-face tap).
    private val openStagesRequest = mutableStateOf(0)

    // ComponentActivity (Compose) handles ActivityResult correctly; the Fragment-version lint
    // check does not apply since we don't use FragmentActivity.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fineGranted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // Location is required; ACTIVITY_RECOGNITION is optional (only sharpens the smart interval).
        if (fineGranted) startLocationService()
        val toRequest = buildList {
            if (!fineGranted) add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (toRequest.isNotEmpty()) requestPermissionsLauncher.launch(toRequest.toTypedArray())

        if (intent?.getBooleanExtra(AppLauncher.EXTRA_OPEN_STAGES, false) == true) {
            openStagesRequest.value++
        }

        val timetableRepo = TimetableRepository(this)

        setContent {
            QWatchTheme {
                AppContent(
                    context = this,
                    timetableRepo = timetableRepo,
                    openStagesRequest = openStagesRequest.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AppLauncher.EXTRA_OPEN_STAGES, false)) {
            openStagesRequest.value++
        }
    }

    private fun startLocationService() {
        startForegroundService(Intent(this, LocationForegroundService::class.java))
    }
}

@Composable
private fun AppContent(context: Context, timetableRepo: TimetableRepository, openStagesRequest: Int) {
    val stageState by context.readStageStateFlow().collectAsState(initial = StageState())
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    // Hoisted here (the parent that survives screen switches) so the stage list keeps its scroll
    // position when returning from the timetable.
    val stageListState = rememberLazyListState()

    // Jump to the stage list whenever the activity is (re)launched from a watch-face tap.
    LaunchedEffect(openStagesRequest) {
        if (openStagesRequest > 0) screen = Screen.Main
    }

    BackHandler(enabled = screen != Screen.Main) {
        screen = Screen.Main
    }

    when (val s = screen) {
        Screen.Debug -> DebugScreen(context = context)
        Screen.Favourites -> FavouritesScreen(context = context, timetableRepo = timetableRepo)
        Screen.Main -> StageListScreen(
            context = context,
            selectedStageId = stageState.stageId,
            timetableRepo = timetableRepo,
            listState = stageListState,
            onStageSelected = { stageId -> screen = Screen.Timetable(stageId) },
            onNavigateToDebug = { screen = Screen.Debug },
            onNavigateToFavourites = { screen = Screen.Favourites }
        )
        is Screen.Timetable -> TimetableScreen(
            stageId = s.stageId,
            timetableRepo = timetableRepo
        )
    }
}

@Composable
fun DebugScreen(context: Context) {
    val state by context.readStageStateFlow().collectAsState(initial = StageState())
    val smartMode by context.readSmartModeFlow().collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val listState = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun writeDebugState(newState: StageState) {
        scope.launch {
            context.writeStageState(newState)
            Log.d("MainActivity", "Debug state written: $newState")
            listOf(
                StageComplicationService::class.java,
                NowPlayingComplicationService::class.java,
                NextArtistComplicationService::class.java
            ).forEach { serviceClass ->
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, serviceClass))
                    .requestUpdateAll()
            }
        }
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) { padding ->
            TransformingLazyColumn(contentPadding = padding, state = listState) {

                item {
                    Button(
                        onClick = {
                            context.startForegroundService(
                                Intent(context, LocationForegroundService::class.java)
                                    .setAction(LocationForegroundService.ACTION_FORCE_FETCH)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("⟳ Force fetch position") }
                }

                item {
                    Button(
                        onClick = { scope.launch { context.writeSmartMode(!smartMode) } },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("Mode: ${if (smartMode) "SMART" else "DUMB"}") }
                }

                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("QWatch Debug") }
                }

                item {
                    Text(
                        text = buildString {
                            appendLine("Stage: ${state.stageId ?: "—"}")
                            appendLine("Artist: ${state.artistName ?: "none"}")
                            appendLine("Next: ${state.nextArtistName ?: "none"}")
                            appendLine("Progress: ${state.setProgressPercent}%")
                            appendLine("Mins left: ${state.minsToSetEnd}")
                            appendLine("GPS: ${if (state.isGpsAvailable) "yes" else "no"}")
                            appendLine("Festival: ${if (state.isFestivalActive) "yes" else "no"}")
                            append("Updated: ${if (state.lastUpdateMillis > 0) timeFormat.format(Date(state.lastUpdateMillis)) else "—"}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("Set name length (long → short)") }
                }

                // Sample set names from longest to shortest for testing watch-face text fit.
                // The leading [n] is the character count so the limit is easy to eyeball.
                val testSetNames = listOf(
                    "D-Block & S-te-Fan pres. Music Made Addict",
                    "The Opening Ceremony with Outsiders",
                    "Sub Zero Project & Phuture Noize",
                    "Brennan Heart & Toneshifterz",
                    "Headhunterz b2b Wildstylez",
                    "Warface & Rooler",
                    "Da Tweekaz",
                    "Rebelion",
                    "Sefa",
                    "REBL",
                    "X"
                ).sortedByDescending { it.length }

                testSetNames.forEach { name ->
                    item {
                        Button(
                            onClick = {
                                writeDebugState(StageState(
                                    stageId = "red",
                                    stageName = "RED",
                                    artistName = name,
                                    isGpsAvailable = true,
                                    isFestivalActive = true,
                                    lastUpdateMillis = System.currentTimeMillis(),
                                    setProgressPercent = 60,
                                    minsToSetEnd = 37,
                                    nextArtistName = name
                                ))
                            },
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                            transformation = SurfaceTransformation(transformSpec)
                        ) {
                            Text(
                                text = "[${name.length}] $name",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            writeDebugState(StageState(
                                isFestivalActive = true,
                                isGpsAvailable = true,
                                lastUpdateMillis = System.currentTimeMillis()
                            ))
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("between") }
                }

                item {
                    Button(
                        onClick = {
                            writeDebugState(StageState(
                                isGpsAvailable = false,
                                lastUpdateMillis = System.currentTimeMillis()
                            ))
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("gps_error") }
                }

                item {
                    Button(
                        onClick = { writeDebugState(StageState()) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec)
                    ) { Text("Reset") }
                }
            }
        }
    }
}
