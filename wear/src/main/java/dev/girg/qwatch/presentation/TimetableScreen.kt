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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import java.time.ZonedDateTime

@Composable
fun TimetableScreen(
    stageId: String?,
    timetableRepo: TimetableRepository
) {
    val stage = remember(stageId) { FESTIVAL_STAGES.find { it.id == stageId } }
    val info = remember(stageId) {
        stage?.let { timetableRepo.getNowAndNext(it.timetableLocation, ZonedDateTime.now()) }
    }
    TimetableScreenContent(
        stageName = stage?.displayName,
        stageColor = stage?.color ?: Color(0xFF808080),
        info = info
    )
}

@Composable
private fun TimetableScreenContent(
    stageName: String?,
    stageColor: Color,
    info: NowAndNext?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = stageName ?: "—",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = stageColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        TimetableSlot(
            active = true,
            time = info?.nowStart ?: "—",
            artist = info?.nowArtist ?: "—",
            label = "NOW PLAYING",
            stageColor = stageColor,
            progress = (info?.nowProgress ?: 0) / 100f,
            endsInMins = info?.nowMinsLeft ?: 0
        )

        val nextArtist = info?.nextArtist
        if (!nextArtist.isNullOrBlank()) {
            TimetableSlot(
                active = false,
                time = info?.nextStart ?: "—",
                artist = nextArtist,
                label = "UP NEXT",
                stageColor = stageColor,
                progress = 0f,
                endsInMins = 0
            )
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
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = ink,
                letterSpacing = 0.12.sp
            )
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

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun TimetableScreenPreview() {
    TimetableScreenContent(
        stageName = "BLUE",
        stageColor = Color(0xFF0BDBEF),
        info = NowAndNext(
            nowArtist = "Headhunterz",
            nowStart = "21:00",
            nowEnd = "22:00",
            nowProgress = 60,
            nowMinsLeft = 24,
            nextArtist = "Noisecontrollers",
            nextStart = "22:00"
        )
    )
}
