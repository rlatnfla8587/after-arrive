package dev.arrival300

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SavedParkingDialog(
    draft: ParkingEdit,
    route: ArrivalWalk?,
    onDismiss: () -> Unit,
    onSave: (SavedParking) -> Unit
) {
    val saved = draft.value
    var latitude by rememberSaveable(saved.eventId) { mutableDoubleStateOf(saved.coordinate.latitude) }
    var longitude by rememberSaveable(saved.eventId) { mutableDoubleStateOf(saved.coordinate.longitude) }
    var floor by rememberSaveable(saved.eventId) { mutableStateOf(saved.floor) }
    var zone by rememberSaveable(saved.eventId) { mutableStateOf(saved.zone) }
    var memo by rememberSaveable(saved.eventId) { mutableStateOf(saved.memo) }
    val selected = Coordinate(latitude, longitude)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (saved.demo) "저장된 주차 위치 · 시연" else "저장된 주차 위치") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.accuracy != null) {
                    Text("GPS 오차가 약 ${draft.accuracy.toInt()}m예요. 실제 위치를 확인한 뒤 저장하세요.")
                } else {
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(saved.savedAt)),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("지도를 탭하면 주차 핀이 이동해요.", style = MaterialTheme.typography.bodySmall)
                ParkingPositionMap(saved, selected, route, Modifier.fillMaxWidth().height(132.dp)) { point ->
                    latitude = point.latitude
                    longitude = point.longitude
                }
                Text(String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(floor, { floor = it.take(14) }, Modifier.weight(1f), label = { Text("층 · 선택") }, singleLine = true)
                    OutlinedTextField(zone, { zone = it.take(14) }, Modifier.weight(1f), label = { Text("구역 · 선택") }, singleLine = true)
                }
                OutlinedTextField(memo, { memo = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("메모 · 선택") }, maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(saved.copy(coordinate = selected, floor = floor.trim(), zone = zone.trim(), memo = memo.trim())) },
                enabled = selected.isValid()) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
