package dev.girg.qwatch.presentation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import java.time.ZonedDateTime

@Composable
fun StageListScreen(
    context: Context,
    selectedStageId: String?,
    timetableRepo: TimetableRepository,
    onStageSelected: (stageId: String) -> Unit,
    onNavigateToDebug: () -> Unit
) {
    val listState = rememberLazyListState()

    val now = remember { ZonedDateTime.now() }
    val visibleStages = remember(now) {
        FESTIVAL_STAGES.filter { timetableRepo.hasEventsToday(it.timetableLocation, now) }
    }
    val currentArtists = remember(now) {
        visibleStages.associate { stage ->
            stage.id to timetableRepo.getCurrentArtist(stage.timetableLocation, now)
        }
    }

    val selectedIndex = remember(selectedStageId) {
        visibleStages.indexOfFirst { it.id == selectedStageId }.takeIf { it >= 0 }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex == null) return@LaunchedEffect
        // Instant scroll to bring selected item near view, then fine-adjust to centre — no animation, no overshoot
        listState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (itemInfo != null) {
            val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            listState.scrollBy((itemCenter - viewportCenter).toFloat())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 48.dp, start = 36.dp, end = 36.dp, bottom = 96.dp)
        ) {
            items(visibleStages) { stage ->
                val selected = stage.id == selectedStageId
                val artist = currentArtists[stage.id]
                StageRow(
                    stage = stage,
                    artist = artist,
                    selected = selected,
                    onClick = { onStageSelected(stage.id) }
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A))
                        .clickable { onNavigateToDebug() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DEV",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF555555),
                        letterSpacing = 0.15.sp
                    )
                }
            }
        }

        // Header with top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black,
                            0.60f to Color.Black,
                            1.0f to Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "STAGES",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF7A7A7A),
                letterSpacing = 0.24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                )
        )
    }
}

@Composable
private fun StageRow(
    stage: StageInfo,
    artist: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) stage.color.copy(alpha = 0.16f) else Color.Transparent
    val borderColor = if (selected) stage.color.copy(alpha = 0.55f) else Color.Transparent
    val nameColor = if (selected) stage.color else Color(0xFFF2F2F2)

    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(1.5.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        // Color bar
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(38.dp)
                .background(stage.color, RoundedCornerShape(5.dp))
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = nameColor,
                lineHeight = 24.sp
            )
            if (!artist.isNullOrBlank()) {
                Text(
                    text = artist,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF7C7C7C),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (selected) {
            Text(
                text = "LIVE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = stage.color,
                letterSpacing = 0.1.sp
            )
        }
    }
}
