package dev.arrival300

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ParkingHighlights(trip: TripViewModel, candidates: List<Candidate>) {
    val nearest = candidates.minByOrNull { it.lot.walk.meters }?.lot ?: return
    val cheapest = candidates.minByOrNull { it.estimatedWon ?: Long.MAX_VALUE } ?: return
    val price = cheapest.estimatedWon
    Text("추천 주차장", Modifier.padding(start = 2.dp), fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ParkingHighlight(
            "가장 가까운 곳", "${nearest.walk.meters}m", nearest, Glyph.WALK, Raised, Blue,
            Modifier.weight(1f), onClick = { trip.selectLot(nearest.id) }
        )
        ParkingHighlight(
            "가장 저렴한 곳", price?.let(::won) ?: "요금 미정", cheapest.lot, Glyph.PARKING, Panel, Ink,
            Modifier.weight(1f), onClick = { trip.selectLot(cheapest.lot.id) }
        )
    }
}

@Composable
private fun ParkingHighlight(
    title: String, value: String, lot: Lot, glyph: Glyph, background: Color, tone: Color,
    modifier: Modifier, onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 125.dp),
        shape = DashboardCard, color = background, contentColor = tone) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DashboardGlyph(glyph, tone, Modifier.size(22.dp))
                Surface(shape = CircleShape, color = White) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        DashboardGlyph(Glyph.CHEVRON, tone, Modifier.size(15.dp))
                    }
                }
            }
            Text(value, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
            Text("${lot.id} · ${lot.name}", fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
