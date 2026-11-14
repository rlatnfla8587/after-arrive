package dev.arrival300

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DashboardTab(val label: String, val glyph: Glyph) {
    HOME("홈", Glyph.HOME),
    CAR("내 차 찾기", Glyph.CAR),
    PARKING("주차장", Glyph.PARKING)
}

internal val DashboardCard = RoundedCornerShape(25.dp)
internal const val DemoDataLabel = "데모 데이터 · 시연용 정보"

@Composable
internal fun ArrivalScreen(
    trip: TripViewModel,
    requestPermissions: (() -> Unit)? = null,
    onParkingAction: (ParkingFixAction) -> Unit = {},
    onDemo: (DemoParkingFix, ConfirmedEntrance) -> Unit
) {
    var homeTab by rememberSaveable { mutableStateOf(DashboardTab.HOME) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val hospitalPhoto by produceState<ImageBitmap?>(null, resources) {
        value = withContext(Dispatchers.IO) { ImageBitmap.imageResource(resources, R.drawable.arrival_hospital) }
    }
    val journey = trip.arrival
    val canBrowse = trip.canInteract && (!trip.arrivalDemoRunning || journey.vehicle.safelyParked)
    val parkingVisible = trip.showingParking
    val active = if (parkingVisible) DashboardTab.PARKING else if (canBrowse) homeTab else DashboardTab.HOME
    val canPark = trip.canOpenParking
    val canFindDestination = canBrowse && canPark && trip.stage == Stage.COMPARE
    var dashboardPlaced by remember { mutableStateOf(false) }
    var prepareParking by remember { mutableStateOf(false) }

    LaunchedEffect(dashboardPlaced, canFindDestination) {
        if (dashboardPlaced && canFindDestination && !prepareParking) {
            withFrameNanos { }
            withFrameNanos { }
            prepareParking = true
        }
    }
    val home: () -> Unit = {
        if (trip.showingParking) trip.closeParking()
        homeTab = DashboardTab.HOME
    }
    val openCar: () -> Unit = {
        if (trip.showingParking) trip.closeParking()
        homeTab = DashboardTab.CAR
    }
    val parking = trip.parkingGuidance
    val saved = parking.saved
    val returning = parking.carReturn != null
    val findCar: (SavedParking) -> Unit = { point ->
        info = false
        if (point.demo) trip.startCarReturn(point) else onParkingAction(ParkingFixAction.FIND)
    }

    LaunchedEffect(canBrowse) {
        if (!canBrowse) {
            picker = false
            info = false
            parking.closeEdit()
        }
    }
    LaunchedEffect(journey.walking, trip.openWalkRequest) { if (journey.walking) homeTab = DashboardTab.HOME }
    LaunchedEffect(returning) { if (returning && homeTab != DashboardTab.CAR) home() }
    BackHandler(enabled = active == DashboardTab.CAR && canBrowse, onBack = home)
    BackHandler(enabled = returning && active != DashboardTab.PARKING && canBrowse, onBack = trip::closeCarReturn)

    Surface(color = Page, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DestinationBar(
                destination = journey.destination?.name,
                rainbow = active == DashboardTab.HOME && canBrowse,
                canSearch = canFindDestination,
                canShowInfo = canBrowse,
                onSearch = { picker = true },
                onInfo = { info = true }
            )
            Layout(
                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { dashboardPlaced = true },
                content = {
                    Box(if (parkingVisible) Modifier else Modifier.clearAndSetSemantics { }) {
                        if (parkingVisible || (prepareParking && canFindDestination)) ParkingScreen(trip)
                    }
                    when (active) {
                        DashboardTab.HOME -> ArrivalHome(
                            trip, requestPermissions, hospitalPhoto, onOpenCar = openCar,
                            onStatus = { info = true },
                            onStart = { onDemo(DemoParkingFix.GARAGE, DemoArrivalWalks.east) },
                            onFindCar = { saved?.let(findCar) }
                        )
                        DashboardTab.CAR -> FindCarScreen(
                            trip, saved, onFindCar = findCar, onEdit = parking::showEdit,
                            onSave = { onParkingAction(ParkingFixAction.SAVE) }
                        )
                        DashboardTab.PARKING -> Unit
                    }
                }
            ) { children, constraints ->
                // Measure the parking layout before the tap, but place only the selected tab.
                val parkingPage = children.first().measure(constraints)
                val currentPage = if (parkingVisible) parkingPage else children[1].measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    currentPage.placeRelative(0, 0)
                }
            }
            DashboardNavigation(active, canBrowse, canPark) { tab ->
                when (tab) {
                    DashboardTab.HOME -> home()
                    DashboardTab.CAR -> openCar()
                    DashboardTab.PARKING -> trip.openParking()
                }
            }
        }
    }

    if (picker && canFindDestination) {
        DestinationPicker(trip, onDismiss = { picker = false }) { destination ->
            trip.openParking()
            trip.selectDestination(destination)
            picker = false
        }
    }
    if (info && canBrowse) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text("도착의 완성") },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    Text(if (trip.automotive) trip.naviConnection else "차량과 스마트폰의 자동 연동은 미연결 상태예요.")
                    TextButton(onClick = trip::toggleDemoMode) {
                        Text(if (trip.demoMode) "데모 모드 끄기" else "데모 모드 켜기")
                    }
                    if (trip.demoMode && !trip.automotive) {
                        TextButton(onClick = { info = false; onDemo(DemoParkingFix.CURB, DemoArrivalWalks.east) },
                            enabled = !trip.arrivalDemoRunning) { Text("시동 OFF 시뮬레이션 · 도로변") }
                        TextButton(onClick = { info = false; onDemo(DemoParkingFix.UNKNOWN, DemoArrivalWalks.east) },
                            enabled = !trip.arrivalDemoRunning) { Text("주차 위치 미확인 시뮬레이션") }
                        TextButton(onClick = { info = false; onDemo(DemoParkingFix.CURB, DemoArrivalWalks.main) },
                            enabled = !trip.arrivalDemoRunning) { Text("본관 정문 경로 시뮬레이션") }
                        if (journey.walking && journey.prepared?.demo == true) {
                            TextButton(onClick = { info = false; trip.simulateEntranceArrival() }) { Text("출입구 도착 시뮬레이션") }
                        }
                        if (trip.canSimulateDeviation) {
                            TextButton(onClick = { info = false; trip.simulateDeviation() }) { Text("도보 경로 이탈 시뮬레이션") }
                        }
                    }
                    if (!trip.automotive) {
                        TextButton(onClick = { info = false; onParkingAction(ParkingFixAction.SAVE) }) { Text("현재 위치 저장") }
                    }
                    if (saved != null) {
                        TextButton(onClick = { info = false; parking.showEdit(saved) }) {
                            Text(if (saved.demo) "저장된 주차 위치 · 시연" else "저장된 주차 위치")
                        }
                        if (!returning) TextButton(onClick = { findCar(saved) }) { Text("내 차 찾기") }
                    } else {
                        Text("내 차를 찾으려면 주차 위치를 먼저 저장하세요.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text("닫기") } }
        )
    }
    if (canBrowse) parking.edit?.let { draft ->
        SavedParkingDialog(draft, journey.prepared, parking::closeEdit, parking::saveEdit)
    }
}

@Composable
private fun DestinationBar(
    destination: String?, rainbow: Boolean, canSearch: Boolean, canShowInfo: Boolean,
    onSearch: () -> Unit, onInfo: () -> Unit
) {
    val edge = Brush.horizontalGradient(listOf(Color(0xFF4389FF), Color(0xFF7BA4FF), Color(0xFFFF9A74), Color(0xFFF3CF5B), Color(0xFF70BA8E)))
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val halo = if (rainbow) {
            Modifier.shadow(4.dp, CircleShape, ambientColor = Color(0xFF6EA0FF), spotColor = Color(0xFF6EA0FF))
                .border(1.5.dp, edge, CircleShape)
        } else Modifier
        Row(
            Modifier.weight(1f).heightIn(min = 51.dp).then(halo).clip(CircleShape).background(White)
                .clickable(enabled = canSearch, role = Role.Button, onClickLabel = "목적지 검색", onClick = onSearch)
                .padding(start = 9.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(32.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                DashboardGlyph(Glyph.PIN, Muted, Modifier.size(17.dp))
            }
            Text(destination ?: "내비 목적지", color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundAction(Glyph.PLUS, "목적지 추가", canSearch, onSearch, background = White)
        RoundAction(Glyph.INFO, "앱 정보", canShowInfo, onInfo, background = VioletTile)
    }
}

@Composable
internal fun RoundAction(
    icon: Glyph, label: String, enabled: Boolean, onClick: () -> Unit, background: Color = Panel
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        DashboardGlyph(icon, if (enabled) Ink else Muted.copy(alpha = .55f), Modifier.size(21.dp))
    }
}

@Composable
private fun DashboardNavigation(
    active: DashboardTab, canBrowse: Boolean, canPark: Boolean, onSelect: (DashboardTab) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardTab.entries.forEach { tab ->
            val selected = tab == active
            val enabled = canBrowse && (tab != DashboardTab.PARKING || canPark || selected)
            val tone = if (selected) Blue else if (enabled) Ink else Muted.copy(alpha = .55f)
            Column(
                Modifier.weight(1f).heightIn(min = 53.dp)
                    .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
            ) {
                Box(
                    Modifier.width(53.dp).height(27.dp).background(if (selected) Raised else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) { DashboardGlyph(tab.glyph, tone, Modifier.size(20.dp)) }
                Text(tab.label, color = tone, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DestinationPicker(trip: TripViewModel, onDismiss: () -> Unit, onSelect: (Destination) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("목적지 검색") },
        text = {
            Column(
                Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("이름 또는 주소") },
                    singleLine = true, shape = RoundedCornerShape(18.dp))
                val matches = trip.search(query)
                if (matches.isEmpty()) Text("일치하는 시연 목적지가 없어요.", color = Muted)
                if (matches.isNotEmpty() && trip.demoParkingData) {
                    Text(DemoDataLabel, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                matches.forEach { destination ->
                    Surface(
                        onClick = { onSelect(destination) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp), color = Panel
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text(destination.name, fontWeight = FontWeight.SemiBold)
                            Text(destination.address, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}



