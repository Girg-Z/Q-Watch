package dev.girg.qwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import java.time.ZonedDateTime

@Composable
fun TimetableScreen(
    stageId: String?,
    timetableRepo: TimetableRepository
) {
    val stage = remember(stageId) { FESTIVAL_STAGES.find { it.id == stageId } }
    val stageColor = stage?.color ?: Color(0xFF808080)
    val locationName = stage?.timetableLocation ?: ""

    val info = remember(stageId) {
        if (locationName.isNotBlank()) timetableRepo.getNowAndNext(locationName, ZonedDateTime.now())
        else NowAndNext(null, null, null, 0, 0, null, null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 76.dp, start = 44.dp, end = 44.dp, bottom = 64.dp)
    ) {
        Column {
            // Header: stage name + TIMETABLE label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stage?.displayName ?: "—",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = stageColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "TIMETABLE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF7A7A7A),
                    letterSpacing = 0.18.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            if (info.nowArtist != null) {
                TimetableSlot(
                    active = true,
                    time = info.nowStart ?: "",
                    artist = info.nowArtist,
                    label = "NOW PLAYING",
                    stageColor = stageColor,
                    progress = info.nowProgress / 100f,
                    endsInMins = info.nowMinsLeft
                )
            } else {
                TimetableSlot(
                    active = false,
                    time = "—",
                    artist = "Nothing playing",
                    label = "NOW PLAYING",
                    stageColor = stageColor,
                    progress = 0f,
                    endsInMins = 0
                )
            }

            if (info.nextArtist != null) {
                TimetableSlot(
                    active = false,
                    time = info.nextStart ?: info.nowEnd ?: "",
                    artist = info.nextArtist,
                    label = "UP NEXT",
                    stageColor = stageColor,
                    progress = 0f,
                    endsInMins = 0
                )
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
    val artistSize = if (artist.length > 14) 22.sp else 28.sp

    Row(verticalAlignment = Alignment.Top) {
        // Time label
        Text(
            text = time,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = ink,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp)
        )

        Spacer(Modifier.width(14.dp))

        // Timeline dot
        Box(
            modifier = Modifier.width(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(11.dp)
                    .background(dotColor, CircleShape)
            )
        }

        Spacer(Modifier.width(10.dp))

        // Text + progress
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = ink,
                letterSpacing = 0.18.sp
            )
            Text(
                text = artist,
                fontWeight = FontWeight.Bold,
                fontSize = artistSize,
                color = artistColor,
                lineHeight = artistSize,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (active) {
                Spacer(Modifier.height(10.dp))
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 6.dp)
                        .height(5.dp)
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(stageColor, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (endsInMins > 0) "ENDS IN ${endsInMins}M" else "ENDING",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF8A8A8A),
                    letterSpacing = 0.04.sp
                )
            }
        }
    }
}
