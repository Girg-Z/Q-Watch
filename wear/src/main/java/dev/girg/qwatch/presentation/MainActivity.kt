package dev.girg.qwatch.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import dev.girg.qwatch.complication.NextArtistComplicationService
import dev.girg.qwatch.complication.NowPlayingComplicationService
import dev.girg.qwatch.complication.StageComplicationService
import dev.girg.qwatch.data.StageState
import dev.girg.qwatch.data.readStageStateFlow
import dev.girg.qwatch.data.writeStageState
import dev.girg.qwatch.presentation.theme.QWatchTheme
import dev.girg.qwatch.service.LocationForegroundService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationService()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        setContent {
            QWatchTheme {
                DebugScreen(context = this)
            }
        }
    }

    private fun startLocationService() {
        startForegroundService(Intent(this, LocationForegroundService::class.java))
    }
}

@Composable
fun DebugScreen(context: Context) {
    val state by context.readStageStateFlow().collectAsState(initial = StageState())
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
                    ) { Text("Stages") }
                }

                val stageIds = listOf(
                    "blue" to "BLUE", "indigo" to "INDIGO", "black" to "BLACK",
                    "magenta" to "MAGENTA", "yellow" to "YELLOW", "purple" to "PURPLE",
                    "uv" to "UV", "gold" to "GOLD", "brown" to "BROWN",
                    "red" to "RED", "orange_light_district" to "ORANGE",
                    "green" to "GREEN", "silver" to "SILVER", "pink" to "PINK"
                )
                stageIds.forEach { (id, displayName) ->
                    item {
                        Button(
                            onClick = {
                                writeDebugState(StageState(
                                    stageId = id,
                                    stageName = displayName,
                                    artistName = "Test Artist",
                                    isGpsAvailable = true,
                                    isFestivalActive = true,
                                    lastUpdateMillis = System.currentTimeMillis(),
                                    setProgressPercent = 60,
                                    minsToSetEnd = 37,
                                    nextArtistName = "Headhunterz"
                                ))
                            },
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                            transformation = SurfaceTransformation(transformSpec)
                        ) { Text(id) }
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
