package dev.arrival300

import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WalkingPanel(trip: TripViewModel) {
    val lot = trip.selected
    val note = trip.parkingNoteFor(lot)
    var floor by rememberSaveable(lot.id) { mutableStateOf(note.floor) }
    var zone by rememberSaveable(lot.id) { mutableStateOf(note.zone) }
    var entry by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Pill("주차 완료", Gold)
    Text("${lot.walk.meters}m · 약 ${lot.walk.minutes}분", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("${lot.name} 보행 출구 → ${trip.destination.name} 방문 출입구", color = Muted)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            lot.walkSteps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${index + 1}", Gold)
                    Text(step, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    AutomaticHandoffStatus("주차장에서 도보 안내 중")
    TextButton(onClick = { entry = true }) {
        Text(if (trip.demoMode) "입구 사진과 상세 다시 보기" else "주차장 상세 다시 보기")
    }
    Text("내 주차 위치", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(if (note.label.isBlank()) "층과 구역을 저장해 두세요." else "저장된 위치 · ${note.label}", color = Blue)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(floor, { floor = it.take(14) }, Modifier.weight(1f), label = { Text("층") },
            placeholder = { Text("예: B2") }, singleLine = true)
        OutlinedTextField(zone, { zone = it.take(20) }, Modifier.weight(1f), label = { Text("구역") },
            placeholder = { Text("예: A-12") }, singleLine = true)
    }
    OutlinedButton(onClick = {
        trip.saveNote(floor, zone)
        focus.clearFocus()
    }) { Text("층·구역 저장") }
    if (entry) EntryDialog(trip, lot) { entry = false }
}

@Composable
internal fun EntryDialog(trip: TripViewModel, lot: Lot, onClose: () -> Unit) {
    val note = trip.parkingNoteFor(lot)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${lot.id} · ${lot.name}") },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trip.demoParkingData) Text(DemoDataLabel, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (trip.demoMode) {
                    lot.photoTile?.let { EntrancePhoto(it) }
                    Text("차량 진입구", color = Blue, fontWeight = FontWeight.Bold)
                    Text(lot.entryDetails)
                }
                lot.hours?.let { Text("운영시간  $it") }
                lot.indoor?.let { Text(if (it) "실내 주차장" else "실외 주차장") }
                lot.maxHeight?.let { Text("높이 제한  $it") }
                lot.tariff?.let { tariff ->
                    Text("최초 ${tariff.firstMinutes}분 ${won(tariff.firstWon.toLong())}")
                    Text("추가 ${tariff.extraMinutes}분당 ${won(tariff.extraWon.toLong())}")
                    tariff.dailyMax?.let { Text("일 최대 ${won(it.toLong())}") }
                } ?: Text("요금 정보 없음")
                if (note.label.isNotBlank()) Text("저장된 주차 위치 · ${note.label}", color = Blue)
            }
        }
    )
}

@Suppress("DEPRECATION")
@Composable
private fun EntrancePhoto(tile: Int) {
    val assets = LocalContext.current.assets
    val photo by produceState<ImageBitmap?>(null, assets, tile) {
        value = withContext(Dispatchers.IO) {
            assets.open("entrances.png").use { input ->
                val decoder = BitmapRegionDecoder.newInstance(input, false) ?: return@use null
                try {
                    val tileWidth = decoder.width / 3
                    val tileHeight = decoder.height / 2
                    val x = tile % 3 * tileWidth
                    val y = tile / 3 * tileHeight
                    decoder.decodeRegion(Rect(x + 2, y + 2, x + tileWidth - 2, y + tileHeight - 2), null)?.asImageBitmap()
                } finally {
                    decoder.recycle()
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))) {
        photo?.let { bitmap ->
            Image(bitmap, contentDescription = "시연용 주차장 차량 진입구 사진",
                modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
    Text("시연용 합성 사진", color = Muted, fontSize = 11.sp)
}
