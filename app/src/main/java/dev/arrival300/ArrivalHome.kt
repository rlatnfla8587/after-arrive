package dev.arrival300

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ArrivalHome(
    trip: TripViewModel, requestPermissions: (() -> Unit)?, hospitalPhoto: ImageBitmap?,
    onOpenCar: () -> Unit, onStatus: () -> Unit, onStart: () -> Unit,
    onFindCar: () -> Unit
) {
    val journey = trip.arrival
    val original = journey.prepared?.takeIf { journey.vehicle.safelyParked && trip.canInteract }
    val returning = trip.parkingGuidance.carReturn?.takeIf { trip.canInteract }
    val plan = returning?.walk ?: original?.let(trip.parkingGuidance::display)
    val browse = trip.canInteract && (!trip.arrivalDemoRunning || journey.vehicle.safelyParked)
    val showIntro = !trip.automotive && journey.destination == null && browse
    val showSample = journey.destination?.demo ?: !trip.automotive
    var confirmLocation by remember(journey.destination) { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 650.dp
        val preview: @Composable () -> Unit = {
            if (plan != null) {
                ArrivalWalkMap(
                    plan, returning?.let { !it.completed } ?: journey.walking, !trip.automotive,
                    if (plan.demo) trip.demoWalkingLocation else trip.walkingLocation,
                    Modifier.fillMaxWidth().height(if (wide) 192.dp else 228.dp),
                    returning = returning != null,
                    parkedStart = original?.takeIf { returning == null && plan != original }?.let { it.origin.offset(it.route.points.first()) }
                )
            } else {
                DestinationPhoto(showSample, journey, hospitalPhoto, Modifier.fillMaxWidth().height(if (wide) 186.dp else 208.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(
                    Glyph.WALK, "도보 안내",
                    if (plan != null) "${plan.route.meters}m · 약 ${plan.route.minutes}분" else "마지막 300m",
                    VioletTile, Indigo, Modifier.weight(1f), onClick = if (trip.demoMode && showIntro) onStart else null
                )
                QuickTile(
                    Glyph.PARKING, "주변 주차장 비교", "가격 · 도보 거리",
                    HoneyTile, Gold, Modifier.weight(1f), onClick = if (trip.canOpenParking && browse) trip::openParking else null
                )
            }
        }
        val details: @Composable () -> Unit = {
            if (returning != null) {
                CarReturnDetails(returning, trip::closeCarReturn)
            } else if (plan != null && (journey.walking || journey.completed)) {
                WalkSummary(plan, journey.completed, trip.handoffStatus)
                WalkSteps(plan)
                if (trip.demoMode && !trip.automotive && browse) TextButton(onClick = onStart) { Text("시연 다시 시작") }
            } else {
                ArrivalSummary(trip, plan, showIntro, onStart, onConfirm = { confirmLocation = true })
                if (journey.arrival != null && journey.vehicle.safelyParked && !trip.hasComparisonData) {
                    Text("이 목적지의 주차장 정보가 아직 없어요.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (browse && requestPermissions != null) {
                TextButton(onClick = requestPermissions) { Text("차량 신호·위치 권한") }
            }
            if (browse && returning == null && journey.completed && trip.parkingGuidance.saved != null) {
                TextButton(onClick = onFindCar) { Text("내 차 찾기") }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 19.dp else 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.height(48.dp).background(Raised, RoundedCornerShape(17.dp)).padding(horizontal = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardGlyph(Glyph.WALK, Blue, Modifier.size(20.dp))
                    Text("도착 안내", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                RoundAction(Glyph.PARKING, "주차장 열기", browse && trip.canOpenParking, trip::openParking)
                RoundAction(Glyph.CAR, "내 차 찾기 열기", browse, onOpenCar)
                RoundAction(Glyph.CHECK, "자동 연동 상태", browse, onStatus)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { preview() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
                }
            } else {
                preview()
                details()
            }
        }
    }
    if (confirmLocation && trip.needsParkingConfirmation) {
        journey.destination?.let { destination ->
            ParkLocationDialog(destination, onDismiss = { confirmLocation = false }) { point ->
                trip.confirmParkedLocation(point)
                confirmLocation = false
            }
        }
    }
}

@Composable
private fun DestinationPhoto(showSample: Boolean, journey: ArrivalJourney, hospitalPhoto: ImageBitmap?, modifier: Modifier) {
    Box(modifier.clip(DashboardCard).background(Panel)) {
        if (showSample) {
            if (hospitalPhoto != null) {
                Image(
                    bitmap = hospitalPhoto,
                    contentDescription = "시연용 한빛대학교병원 본관 동문 사진",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color(0x640B1720), Color.Transparent, Color(0xB8132026)
            ))))
            Row(
                Modifier.align(Alignment.TopStart).padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", color = Color(0xFFAAED8A), fontSize = 11.sp)
                Text(DemoDataLabel, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text("한빛대학교병원 · 본관 동문", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardGlyph(Glyph.PIN, Blue, Modifier.size(38.dp))
                Text(journey.destination?.name ?: "차량 내비", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("도착 후 도보 안내", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QuickTile(
    glyph: Glyph, title: String, subtitle: String, color: Color, tone: Color,
    modifier: Modifier, onClick: (() -> Unit)?
) {
    val action = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.heightIn(min = 76.dp).clip(DashboardCard).background(color).then(action).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        DashboardGlyph(glyph, tone, Modifier.size(21.dp))
        Column {
            Text(title, color = tone, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = tone, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ArrivalSummary(
    trip: TripViewModel, plan: ArrivalWalk?, showIntro: Boolean, onStart: () -> Unit, onConfirm: () -> Unit
) {
    val journey = trip.arrival
    val destination = journey.destination
    val title = when {
        plan != null -> "도보 경로가 준비됐어요"
        journey.parkedAt != null -> "건물 입구 경로가 아직 없어요"
        journey.arrival != null && journey.vehicle.safelyParked -> "주차 위치를 확인하고 있어요"
        journey.arrival != null -> "목적지 주변에 도착했어요"
        destination != null -> "목적지로 이동 중이에요"
        else -> "내비 도착 후, 건물 입구까지"
    }
    val subtitle = when {
        plan != null && journey.canStartManually -> "정차한 위치에서 도보 안내를 직접 열 수 있어요."
        plan != null && journey.vehicle.ignitionOff == true -> "시동 OFF를 확인했어요."
        plan != null -> "정차 상태에서 시동을 끄면 도보 안내를 이어서 열어요."
        journey.parkedAt != null -> "이 주차 위치에서 건물 입구까지 등록된 도보 경로가 없어요."
        trip.needsParkingConfirmation -> "정확한 주차 위치를 확인하면 도보 경로를 준비해요."
        journey.arrival != null && journey.vehicle.safelyParked -> "확인된 주차 위치를 기준으로 도보 경로를 준비해요."
        journey.arrival != null -> "정차하고 P에 두면 도보 경로를 준비해요."
        destination != null -> "내비의 도착 신호를 기다리고 있어요."
        trip.automotive -> trip.naviConnection
        else -> "차에서 내린 자리부터 찾아갈 입구까지"
    }

    Surface(Modifier.fillMaxWidth(), shape = DashboardCard, color = PeachTile, contentColor = PeachInk) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SummaryLabel("내비에서 받은 목적지")
            if (showIntro) {
                Text("300m", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            if (plan != null) {
                Text("${plan.entranceName} · 도보 ${plan.route.meters}m / 약 ${plan.route.minutes}분", fontWeight = FontWeight.SemiBold)
                if (journey.canStartManually) {
                    Button(onClick = trip::openWalkingManually, modifier = Modifier.fillMaxWidth()) { Text("여기서 도보 안내") }
                }
                AutomaticHandoffStatus(trip.handoffStatus)
            }
            if (trip.needsParkingConfirmation) {
                AutomaticHandoffStatus(trip.handoffStatus)
                FilledTonalButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Raised, contentColor = Blue)) {
                    Text("주차 위치 확인")
                }
            }
            if (trip.demoMode && showIntro) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.widthIn(min = 130.dp).align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = PeachButton, contentColor = PeachInk)
                ) {
                    DashboardGlyph(Glyph.PLAY, PeachInk, Modifier.size(17.dp).padding(end = 2.dp))
                    Text("시연 시작", Modifier.padding(start = 5.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryLabel(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        DashboardGlyph(Glyph.PIN, PeachInk, Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun CarReturnDetails(returning: CarReturn, onClose: () -> Unit) {
    val status = when {
        returning.completed -> if (returning.walk.demo) "시연 · 차량 위치 도착 · 안내 종료" else "차량 위치 도착 · 안내 종료"
        returning.walk.demo -> "시연 데이터 · 차량으로 복귀 중"
        else -> "차량으로 복귀 중"
    }
    WalkSummary(returning.walk, returning.completed, status, returning = true)
    WalkSteps(returning.walk, returning = true)
    val details = listOf(returning.parking.floor, returning.parking.zone, returning.parking.memo).filter(String::isNotBlank)
    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onClose) { Text("복귀 안내 닫기") }
}

@Composable
private fun WalkSummary(plan: ArrivalWalk, completed: Boolean, status: String, returning: Boolean = false) {
    Surface(Modifier.fillMaxWidth(), shape = DashboardCard, color = PeachTile, contentColor = PeachInk) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryLabel(when {
                returning && completed -> "차량 위치 도착"
                returning -> "내 차 찾기"
                completed -> "출입구 도착"
                else -> "도보 안내 시작"
            })
            Text(plan.entranceName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("도보 ${plan.route.meters}m · 약 ${plan.route.minutes}분", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("${plan.startLabel} → ${plan.destinationName}", style = MaterialTheme.typography.bodySmall)
            AutomaticHandoffStatus(status)
        }
    }
}

@Composable
private fun WalkSteps(plan: ArrivalWalk, returning: Boolean = false) {
    Surface(shape = DashboardCard, color = Panel) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (returning) "차량까지 이렇게 가세요" else "입구까지 이렇게 가세요",
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            plan.steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Pill("${index + 1}", Blue)
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun AutomaticHandoffStatus(status: String) {
    Surface(Modifier.fillMaxWidth().heightIn(min = 40.dp), shape = CircleShape, color = Raised, contentColor = Blue) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardGlyph(Glyph.CHECK, Blue, Modifier.size(16.dp))
            Text(status, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
