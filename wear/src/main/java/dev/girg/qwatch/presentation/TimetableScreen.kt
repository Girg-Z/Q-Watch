package dev.girg.qwatch.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

@Composable
fun TimetableScreen(
    stageId: String,
    timetableRepo: TimetableRepository
) {
    val stage = remember(stageId) { FESTIVAL_STAGES.find { it.id == stageId } }
    val entries = remember(stageId) {
        stage?.let { timetableRepo.getUpcomingEntries(it.timetableLocation) } ?: emptyList()
    }
    val stageColor = stage?.color ?: Color(0xFF808080)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
        ) {
            item {
                Text(
                    text = stage?.displayName ?: "—",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = stageColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entries.isEmpty()) {
                item {
                    TimetableSlot(
                        active = false,
                        time = "—",
                        artist = "—",
                        label = "",
                        stageColor = stageColor,
                        progress = 0f,
                        endsInMins = 0
                    )
                }
            } else {
                items(entries) { entry ->
                    TimetableSlot(
                        active = entry.isNow,
                        time = entry.startTime,
                        artist = entry.artist,
                        label = if (entry.isNow) "NOW PLAYING" else "",
                        stageColor = stageColor,
                        progress = entry.progress / 100f,
                        endsInMins = entry.minsLeft
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableSlot(
    active: Boolean,
    time: String,
    artist: String,
    label: String,
    stageColor: Color,
    progress: Float,
    endsInMins: Int
) {
    val ink = if (active) stageColor else Color(0xFF6F6F6F)
    val dotColor = if (active) stageColor else Color(0xFF3A3A3A)
    val artistColor = if (active) Color.White else Color(0xFF9A9A9A)
    val artistSize = if (artist.length > 14) 14.sp else 16.sp

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time label
        Text(
            text = time,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = ink,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(33.dp)
                .padding(top = 3.dp)
        )

        Spacer(Modifier.width(3.dp))

        // Timeline dot
        Box(
            modifier = Modifier.width(12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
        }

        Spacer(Modifier.width(3.dp))

        // Content column
        Column(modifier = Modifier.weight(1f)) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = ink,
                    letterSpacing = 0.12.sp
                )
            }
            Text(
                text = artist,
                fontWeight = FontWeight.Bold,
                fontSize = artistSize,
                color = artistColor,
                lineHeight = artistSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )

            if (active) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(stageColor, RoundedCornerShape(2.dp))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (endsInMins > 0) "ENDS IN ${endsInMins}M" else "ENDING",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF8A8A8A)
                )
            }
        }
    }
}
