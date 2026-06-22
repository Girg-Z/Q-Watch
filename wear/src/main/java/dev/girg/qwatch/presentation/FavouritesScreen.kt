package dev.girg.qwatch.presentation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.girg.qwatch.data.readFavouriteIdsFlow
import java.time.ZonedDateTime

@Composable
fun FavouritesScreen(
    context: Context,
    timetableRepo: TimetableRepository
) {
    val favouriteIds by context.readFavouriteIdsFlow().collectAsState(initial = emptySet())
    val now = remember { ZonedDateTime.now() }
    val favourites = remember(favouriteIds, now) {
        timetableRepo.getFavouriteSets(favouriteIds, now)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 48.dp, start = 28.dp, end = 28.dp, bottom = 60.dp)
        ) {
            if (favourites.isEmpty()) {
                item {
                    Text(
                        text = "No favourites yet.\nStar sets in the phone app.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF7A7A7A),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    )
                }
            } else {
                items(favourites) { fav ->
                    // The first item is the immediately-next favourite (now playing or soonest).
                    FavouriteRow(fav = fav, isNext = fav == favourites.first())
                    Spacer(Modifier.height(6.dp))
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
                text = "★ FAVOURITES",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF7A7A7A),
                letterSpacing = 0.24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun FavouriteRow(fav: FavouriteSet, isNext: Boolean) {
    val color = fav.stage?.color ?: Color(0xFF808080)
    val shape = RoundedCornerShape(14.dp)
    val bgColor = if (isNext) color.copy(alpha = 0.16f) else Color.Transparent
    val borderColor = if (isNext) color.copy(alpha = 0.55f) else Color(0xFF1F1F1F)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(1.5.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(38.dp)
                .background(color, RoundedCornerShape(5.dp))
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fav.artist,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isNext) color else Color(0xFFF2F2F2),
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${fav.stage?.displayName ?: fav.location} · ${fav.startTime}–${fav.endTime}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF7C7C7C),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        val badge = when {
            fav.isNow -> "NOW"
            fav.minsUntilStart < 60 -> "${fav.minsUntilStart}M"
            else -> fav.startTime
        }
        Text(
            text = badge,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (fav.isNow) color else Color(0xFF8A8A8A),
            letterSpacing = 0.1.sp
        )
    }
}
