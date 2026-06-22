package dev.girg.qwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.girg.qwatch.festival.FestivalRepository
import dev.girg.qwatch.festival.FestivalSet
import dev.girg.qwatch.festival.StageInfo
import dev.girg.qwatch.festival.favouriteId
import dev.girg.qwatch.festival.readFavouriteIdsFlow
import dev.girg.qwatch.festival.syncFavouritesToWatch
import dev.girg.qwatch.festival.toggleFavourite
import dev.girg.qwatch.ui.theme.QWatchTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QWatchTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FavouritesApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun FavouritesApp(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FestivalRepository(context) }
    val stages = remember { repo.stagesWithSets() }
    var selectedStage by remember { mutableStateOf(stages.firstOrNull()) }
    val favouriteIds by context.readFavouriteIdsFlow().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    // Re-push current favourites once on launch so the watch is in sync even if it missed an update.
    LaunchedEffect(Unit) {
        context.syncFavouritesToWatch(context.readFavouriteIdsFlow().first())
    }

    val sets = remember(selectedStage) {
        selectedStage?.let { repo.setsForLocation(it.timetableLocation) } ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Text(
            text = "★ FAVOURITE SETS",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = Color(0xFFE0C24A),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // Stage selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stages.forEach { stage ->
                StageChip(
                    stage = stage,
                    selected = stage.id == selectedStage?.id,
                    onClick = { selectedStage = stage }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sets) { set ->
                val id = favouriteId(set.location, set.rawStart)
                SetRow(
                    set = set,
                    stageColor = selectedStage?.color ?: Color.Gray,
                    isFavourite = id in favouriteIds,
                    onToggle = { scope.launch { context.toggleFavourite(id) } }
                )
            }
        }
    }
}

@Composable
private fun StageChip(stage: StageInfo, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) stage.color.copy(alpha = 0.20f) else Color(0xFF161616)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(stage.color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stage.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (selected) stage.color else Color(0xFFCfcfcf)
        )
    }
}

@Composable
private fun SetRow(
    set: FestivalSet,
    stageColor: Color,
    isFavourite: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = set.artist,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFFF2F2F2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${set.dayLabel} · ${set.startTime}–${set.endTime}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF7C7C7C)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (isFavourite) "★" else "☆",
            fontSize = 22.sp,
            color = if (isFavourite) Color(0xFFE0C24A) else Color(0xFF555555)
        )
    }
}
