package dev.arrival300

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ParkLocationDialog(destination: NaviDestination, onDismiss: () -> Unit, onConfirm: (Coordinate) -> Unit) {
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    val lat = latitude.toDoubleOrNull()
    val lon = longitude.toDoubleOrNull()
    val position = if (lat != null && lon != null) Coordinate(lat, lon).takeIf(destination::acceptsParking) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("주차 위치 확인") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (destination.demo) "시연에서 실제로 주차한 위치를 선택하세요." else "지도에서 확인한 실제 주차 위치의 좌표를 입력하세요.")
                if (destination.demo) {
                    TextButton(onClick = { onConfirm(DemoArrivalWalks.parkedInGarage) }) { Text("본관 지하주차장 · A구역") }
                    TextButton(onClick = { onConfirm(DemoArrivalWalks.parkedAtCurb) }) { Text("새봄로 지상 정차 구역") }
                } else {
                    OutlinedTextField(latitude, { latitude = it }, Modifier.fillMaxWidth(), label = { Text("위도") }, singleLine = true)
                    OutlinedTextField(longitude, { longitude = it }, Modifier.fillMaxWidth(), label = { Text("경도") }, singleLine = true)
                    if (latitude.isNotBlank() && longitude.isNotBlank() && position == null) {
                        Text("목적지 주변의 유효한 좌표를 입력해 주세요.", color = Gold)
                    }
                }
            }
        },
        confirmButton = {
            if (destination.demo) TextButton(onClick = onDismiss) { Text("닫기") }
            else TextButton(onClick = { position?.let(onConfirm) }, enabled = position != null) { Text("위치 확인") }
        },
        dismissButton = { if (!destination.demo) TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
