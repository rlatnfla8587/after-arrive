package dev.arrival300

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(20.dp)

fun won(amount: Long) = String.format(Locale.KOREA, "%,d원", amount)

private fun meters(value: Int) =
    if (value < 1_000) "${value}m" else String.format(Locale.KOREA, "%.2fkm", value / 1_000.0)

private fun timeLabel(minutes: Int) = if (minutes < 60) "${minutes}분" else "${minutes / 60}시간"

@Composable
internal fun ParkingScreen(trip: TripViewModel) {
    val focus = LocalFocusManager.current
    LaunchedEffect(trip.canInteract) { if (!trip.canInteract && trip.showingParking) focus.clearFocus(force = true) }
    BackHandler(enabled = trip.showingParking && trip.canInteract) {
        if (trip.stage == Stage.COMPARE) trip.closeParking() else trip.backToCompare()
    }

    Surface(color = Page, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ParkingHeader(trip)
            if (trip.canInteract) {
                if (trip.stage != Stage.COMPARE) TripProgress(trip.stage, trip.demoMode)
                key(trip.stage) { JourneyLayout(trip, Modifier.weight(1f)) }
            } else {
                RestrictedScreen(trip)
            }
        }
    }
}

@Composable
private fun ParkingHeader(trip: TripViewModel) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 41.dp).padding(start = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("주변 주차장", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        if (trip.demoParkingData && (trip.canInteract || trip.stage == Stage.GUIDING)) Pill(DemoDataLabel, Blue)
        if (trip.demoMode && !trip.automotive && trip.stage != Stage.GUIDING) DemoStateMenu(trip)
    }
}

@Composable
private fun DemoStateMenu(trip: TripViewModel) {
    var open by remember(trip.showingParking) { mutableStateOf(false) }
    val stateLabel = when (trip.demoStatus) {
        VehicleStatus.PARKED -> "주차"
        VehicleStatus.DRIVING -> "주행"
        VehicleStatus.UNKNOWN -> "미확인"
    }
    Box {
        TextButton(onClick = { open = true }) { Text("데모 $stateLabel ▾", fontSize = 11.sp) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                VehicleStatus.PARKED to "주차 상태 재현",
                VehicleStatus.DRIVING to "주행 상태 재현",
                VehicleStatus.UNKNOWN to "상태 미확인 재현"
            ).forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { trip.simulateStatus(value); open = false })
            }
        }
    }
}

@Composable
private fun TripProgress(stage: Stage, showEntrance: Boolean) {
    val active = when (stage) {
        Stage.COMPARE -> 0
        Stage.GUIDING, Stage.ARRIVED -> 1
        Stage.WALKING -> 2
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("주차 비교", if (showEntrance) "진입구 안내" else "운전 안내", "도보 이동").forEachIndexed { index, label ->
            val selected = index == active
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) Raised else Panel,
                border = BorderStroke(1.dp, if (selected) Blue.copy(alpha = .50f) else Border)
            ) {
                Text(
                    "${index + 1}  $label", Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = if (selected) Blue else Muted, fontSize = 12.sp, lineHeight = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun JourneyLayout(trip: TripViewModel, modifier: Modifier) {
    var selectedFromMap by remember { mutableIntStateOf(0) }
    val selectFromMap: (String) -> Unit = { id ->
        trip.selectLot(id)
        selectedFromMap++
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val mapHeight = if (maxHeight < 500.dp) 212.dp else 247.dp
        val panel: @Composable (Modifier) -> Unit = { panelModifier ->
            if (trip.stage == Stage.COMPARE) {
                ComparePanel(trip, selectedFromMap, panelModifier, inlineMap = if (wide) null else {
                    { ParkingMap(trip, Modifier.fillMaxWidth().height(mapHeight), selectFromMap) }
                })
            } else {
                Column(
                    panelModifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!wide) ParkingMap(trip, Modifier.fillMaxWidth().height(mapHeight), selectFromMap)
                    when (trip.stage) {
                        Stage.ARRIVED -> ArrivalPanel(trip)
                        Stage.WALKING -> WalkingPanel(trip)
                        Stage.COMPARE, Stage.GUIDING -> Unit
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ParkingMap(trip, Modifier.weight(1.15f).fillMaxHeight(), selectFromMap)
                    panel(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                panel(Modifier.weight(1f).fillMaxWidth())
            }
            JourneyAction(trip)
        }
    }
}

@Composable
private fun ComparePanel(
    trip: TripViewModel, selectedFromMap: Int, modifier: Modifier, inlineMap: (@Composable () -> Unit)?
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<Lot?>(null) }
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    val candidates = trip.candidates

    LaunchedEffect(trip.showingParking) {
        if (!trip.showingParking) {
            searchOpen = false
            details = null
            if (list.firstVisibleItemIndex != 0 || list.firstVisibleItemScrollOffset != 0) {
                list.requestScrollToItem(0)
            }
        }
    }

    LaunchedEffect(selectedFromMap) {
        if (selectedFromMap > 0) {
            searchOpen = false
            focus.clearFocus()
            val selectedId = trip.selectedId
            val index = candidates.indexOfFirst { it.lot.id == selectedId }
            if (index < 0) return@LaunchedEffect
            val layout = list.layoutInfo
            val visible = layout.visibleItemsInfo.firstOrNull { it.key == selectedId }
            if (visible == null || visible.offset < layout.viewportStartOffset ||
                visible.offset + visible.size > layout.viewportEndOffset
            ) list.requestScrollToItem(index + 1)
        }
    }

    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "compare-controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareControls(trip, candidates, searchOpen, { searchOpen = it }, inlineMap)
            }
        }
        items(candidates, key = { it.lot.id }) { candidate ->
            LotCard(
                candidate, candidate.lot.id == trip.selectedId, trip.demoMode,
                select = { trip.selectLot(candidate.lot.id) },
                details = { details = candidate.lot }
            )
        }
        if (trip.demoMode && trip.automotive && trip.showingParking) item(key = "pleos-tools") { PleosTools(trip) }
    }
    if (trip.showingParking) details?.let { EntryDialog(trip, it, onClose = { details = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareControls(
    trip: TripViewModel, candidates: List<Candidate>, searchOpen: Boolean, onSearchOpen: (Boolean) -> Unit,
    inlineMap: (@Composable () -> Unit)?
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember(trip.showingParking) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(trip.destination.category, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(trip.destination.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(trip.destination.address, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = {
            onSearchOpen(!searchOpen)
            query = ""
            if (searchOpen) focus.clearFocus()
        }) { Text(if (searchOpen) "닫기" else "목적지 검색") }
    }
    if (searchOpen) {
        val results = trip.search(query)
        fun choose(destination: Destination) {
            trip.selectDestination(destination)
            focus.clearFocus()
            onSearchOpen(false)
            query = ""
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("목적지 이름 또는 주소") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { results.singleOrNull()?.let(::choose) ?: focus.clearFocus() })
        )
        if (results.isEmpty()) Text("일치하는 시연 목적지가 없어요.", color = Muted)
        results.forEach { destination ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { choose(destination) },
                color = Panel, shape = CardShape, border = BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(destination.name, fontWeight = FontWeight.SemiBold)
                    Text(destination.address, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = Border)
    }

    ParkingHighlights(trip, candidates)
    inlineMap?.invoke()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        Text("예상 주차시간", modifier = Modifier.align(Alignment.CenterVertically).padding(end = 3.dp),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        listOf(30, 60, 120, 180).forEach { minutes ->
            FilterChip(selected = trip.minutes == minutes, onClick = { trip.setDuration(minutes) }, label = { Text(timeLabel(minutes)) })
        }
    }
    HorizontalDivider(color = Border)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("주차장 ${candidates.size}곳", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            val near = candidates.count { it.radius == RadiusBand.NEAR }
            Text("500m 안 $near · 500m 초과~1km ${candidates.size - near}", color = Muted, fontSize = 11.sp)
            Text("가까운 순 기준 · 보행 출구에서 건물 입구까지", color = Muted, fontSize = 11.sp)
        }
        Box {
            TextButton(onClick = { sortMenu = true }) { Text("${trip.order.label} ▾", fontSize = 12.sp) }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                LotOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(if (order == LotOrder.TOTAL_TIME) "${order.label} (차량+도보)" else order.label) },
                        onClick = { trip.sort(order); sortMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun LotCard(
    candidate: Candidate, selected: Boolean, showEntrance: Boolean, select: () -> Unit, details: () -> Unit
) {
    val lot = candidate.lot
    val amount = candidate.estimatedWon
    Surface(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.Button, onClick = select),
        shape = CardShape, color = if (selected) Raised else Panel,
        border = BorderStroke(1.dp, if (selected) Blue else Border)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(lot.id, if (selected) Blue else Muted)
                Column(Modifier.weight(1f)) {
                    Text(lot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${candidate.radius.label} · 직선 ${meters(candidate.directMeters.roundToInt())}", color = Muted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("예상 총요금", color = Muted, fontSize = 10.sp)
                    Text(amount?.let(::won) ?: "요금 정보 없음", color = if (amount != null) Blue else Muted,
                        fontSize = if (amount == null) 12.sp else 19.sp, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = Border)
            RouteStat("운전", lot.drive, Blue)
            RouteStat("도보", lot.walk, Gold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected) "● 지도에 표시 중" else "탭하여 경로 비교", Modifier.weight(1f),
                    color = if (selected) Blue else Muted, fontSize = 11.sp)
                TextButton(onClick = details) { Text(if (showEntrance) "입구·요금 상세" else "주차장·요금 상세", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun RouteStat(label: String, route: Route, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 12.sp)
        Text("${meters(route.meters)} · ${route.minutes}분", color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ArrivalPanel(trip: TripViewModel) {
    val target = trip.target ?: return
    Text(if (trip.demoMode) "주차장 진입구에 도착했어요" else "주차장 경로 안내를 마쳤어요",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold)
    Surface(color = Panel, shape = CardShape, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Pill("차량 안내 완료", Indigo)
            Text(if (trip.demoMode) target.label else trip.selected.name, style = MaterialTheme.typography.titleLarge)
            if (trip.demoMode) trip.cue?.let { Text(it, color = Blue) }
            Text("주차를 마치면 방문 출입구까지 도보로 안내합니다.", color = Muted)
        }
    }
    trip.error?.let { Text(it, color = Gold) }
    if (trip.demoMode) PleosTools(trip)
    TextButton(onClick = trip::backToCompare) { Text("주차장 비교로 돌아가기") }
}

@Composable
private fun JourneyAction(trip: TripViewModel) {
    val lot = trip.selected
    when (trip.stage) {
        Stage.COMPARE -> {
            trip.error?.let { Text(it, color = Gold) }
            Button(trip::start, Modifier.fillMaxWidth().heightIn(min = 49.dp), shape = CardShape) {
                Text("${lot.id} · 이 주차장으로 안내  →",
                    fontWeight = FontWeight.Bold)
            }
        }
        Stage.ARRIVED -> {
            Button(
                onClick = trip::confirmParking,
                Modifier.fillMaxWidth().heightIn(min = 49.dp), shape = CardShape
            ) { Text("주차 완료 · 도보 안내  →", fontWeight = FontWeight.Bold) }
        }
        Stage.WALKING -> OutlinedButton(trip::backToCompare, Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = CardShape) {
            Text("주차장 비교로 돌아가기")
        }
        Stage.GUIDING -> Unit
    }
}

@Composable
private fun ColumnScope.RestrictedScreen(trip: TripViewModel) {
    val guiding = trip.stage == Stage.GUIDING
    val message = when {
        guiding && trip.demoMode -> trip.cue ?: "목표 지점으로 이동 중이에요"
        guiding -> "주차장 경로를 표시하고 있어요"
        trip.status == VehicleStatus.DRIVING -> "주행 중에는 짧은 안내만 표시해요"
        else -> "주차 상태를 확인하고 있어요"
    }
    if (guiding) {
        ParkingMap(trip, Modifier.fillMaxWidth().weight(1f)) {}
    } else {
        Spacer(Modifier.weight(1f))
    }
    Surface(
        Modifier.fillMaxWidth(), color = Panel, shape = CardShape,
        border = BorderStroke(1.dp, if (trip.cue != null) Blue else Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(if (guiding) "차량 안내" else "차량 화면 제한", Blue)
            Text(message, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (guiding) {
                val label = if (trip.demoMode) trip.target?.label.orEmpty() else trip.selected.name
                Text("$label · 남은 거리 ${meters(trip.remainingMeters)}",
                    color = Muted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("주차가 확인되면 목적지 검색과 주차장 비교를 이용할 수 있어요.", color = Muted)
            }
        }
    }
    if (!guiding) Spacer(Modifier.weight(1f))
}

@Composable
fun Pill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = .14f)) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = color,
            fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
    }
}








