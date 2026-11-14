package dev.arrival300

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun FindCarScreen(
    trip: TripViewModel,
    saved: SavedParking?,
    onFindCar: (SavedParking) -> Unit,
    onEdit: (SavedParking) -> Unit,
    onSave: () -> Unit
) {
    val returning = trip.parkingGuidance.carReturn
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 650.dp
        val mapModifier = Modifier.fillMaxWidth().height(if (wide) 192.dp else 228.dp)
        val map: @Composable () -> Unit = {
            if (returning != null) {
                ArrivalWalkMap(
                    returning.walk, !returning.completed, !trip.automotive,
                    if (returning.walk.demo) trip.demoWalkingLocation else trip.walkingLocation,
                    mapModifier, returning = true
                )
            } else if (saved != null) {
                ParkingPositionMap(saved, saved.coordinate, route = null, modifier = mapModifier)
            }
        }
        val details: @Composable () -> Unit = {
            when {
                returning != null -> CarReturnDetails(returning, trip::closeCarReturn)
                saved != null -> SavedCarCard(saved, onFindCar = { onFindCar(saved) }, onEdit = { onEdit(saved) })
                else -> Surface(Modifier.fillMaxWidth(), color = Panel, shape = DashboardCard) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("아직 저장된 주차 위치가 없어요.", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("주차 위치를 저장하면 나중에 내 차를 쉽게 찾을 수 있어요.",
                            color = Muted, style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = onSave) { Text("현재 위치 저장") }
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (wide && (saved != null || returning != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { map() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
                }
            } else {
                map()
                details()
            }
        }
    }
}

@Composable
private fun SavedCarCard(saved: SavedParking, onFindCar: () -> Unit, onEdit: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = DashboardCard) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (saved.demo) "저장된 주차 위치 · 시연" else "저장된 주차 위치",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (saved.demo && saved.coordinate == DemoArrivalWalks.parkedInGarage) {
                Text(DemoArrivalWalks.hospital.lots.first { it.id == "A" }.name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(String.format(Locale.ROOT, "%.6f, %.6f", saved.coordinate.latitude, saved.coordinate.longitude),
                color = Muted, style = MaterialTheme.typography.bodySmall)
            val floorAndZone = listOfNotNull(
                saved.floor.takeIf(String::isNotBlank)?.let { "층 $it" },
                saved.zone.takeIf(String::isNotBlank)?.let { "구역 $it" }
            )
            if (floorAndZone.isNotEmpty()) Text(floorAndZone.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            if (saved.memo.isNotBlank()) Text(saved.memo, style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onFindCar) { Text("도보 안내 시작") }
            TextButton(onClick = onEdit) { Text("주차 위치 수정") }
        }
    }
}
