package dev.arrival300

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.UUID

/** The SDK adapter receives the requested entry coordinates independently of parking data. */
fun interface Navigator {
    fun navigate(target: NavigationTarget): Boolean
}

private object DemoNavigator : Navigator {
    override fun navigate(target: NavigationTarget) = target.route.points.last() == target.point
}

data class ParkingNote(val floor: String, val zone: String) {
    val label get() = listOf(floor, zone).filter(String::isNotBlank).joinToString(" · ")
}

class TripViewModel(application: Application) : AndroidViewModel(application) {
    private val parking: ParkingRepository = DemoParking
    private val navigator: Navigator = DemoNavigator
    private val arrivalWalks: ArrivalWalkProvider = DemoArrivalWalks
    private val notes = application.getSharedPreferences("parking-notes", 0)
    val automotive = application.packageManager.hasSystemFeature("android.hardware.type.automotive")
    internal val demoParkingData get() = parking.demoData
    internal var demoMode by mutableStateOf(false)
        private set

    var destination by mutableStateOf(parking.search("").first())
        private set
    var minutes by mutableStateOf(120)
        private set
    var order by mutableStateOf(LotOrder.WALK)
        private set
    private val candidateState = derivedStateOf { parking.candidates(destination, minutes, order) }
    val candidates get() = candidateState.value
    var selectedId by mutableStateOf(candidates.first().lot.id)
        private set
    val selected get() = candidates.first { it.lot.id == selectedId }.lot

    var stage by mutableStateOf(Stage.COMPARE)
        private set
    var target by mutableStateOf<NavigationTarget?>(null)
        private set
    var position by mutableStateOf(destination.start)
        private set
    var remainingMeters by mutableStateOf(0)
        private set
    var cue by mutableStateOf<String?>(null)
        private set
    var pendingSpeech by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var playback: Job? = null

    private var hostStatus by mutableStateOf(if (automotive) VehicleStatus.UNKNOWN else VehicleStatus.PARKED)
    var demoStatus by mutableStateOf(VehicleStatus.PARKED)
        private set
    val status: VehicleStatus
        get() = when {
            hostStatus != VehicleStatus.PARKED -> hostStatus
            stage == Stage.GUIDING -> VehicleStatus.DRIVING
            automotive -> VehicleStatus.PARKED
            else -> demoStatus
        }
    val canInteract get() = canCompare(status)
    val canHearCue get() = hostStatus == VehicleStatus.PARKED

    var arrival by mutableStateOf(ArrivalJourney())
        private set
    var showingParking by mutableStateOf(false)
        private set
    var naviConnection by mutableStateOf("차량 내비 연결을 기다리고 있어요")
        private set
    private var arrivalPlayback: Job? = null
    private var parkedSinceNanos: Long? = null
    private var hostSawIgnitionOn = false
    private var parkingEvent: String? = null
    private var savedParkingEvent: String? = null
    private var parkedFix: Pair<Coordinate, Long>? = null
    private val handoff = PhoneHandoff(application, arrivalWalks)
    internal val parkingGuidance = ParkingGuidance(application, arrivalWalks, ::parkingMessage)
    private var deviationPlayback: Job? = null
    internal var demoWalkingLocation by mutableStateOf<Coordinate?>(null)
        private set
    private var phoneSession by mutableStateOf<HandoffSession?>(null)
    private var nearEntranceFixAt = 0L
    var openWalkRequest by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private val handoffListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PhoneHandoff.SESSION) syncPhoneHandoff()
    }

    init {
        if (automotive) {
            handoff.current()?.destination?.takeUnless { it.demo }?.let { arrival = arrival.receiveDestination(it) }
        } else {
            handoff.observe(handoffListener)
            syncPhoneHandoff(restoreDraft = true)
        }
    }

    val needsParkingConfirmation get() = canInteract && arrival.vehicle.safelyParked &&
        arrival.vehicle.ignitionOff == true && arrival.prepared == null && arrival.destination != null

    val handoffStatus get() = when {
        arrival.completed -> if (arrival.destination?.demo == true) "시연 · 출입구 도착 · 안내 종료" else "출입구 도착 · 안내 종료"
        automotive -> "스마트폰 자동 연동 미연결"
        needsParkingConfirmation && arrival.parkedAt == null -> "시동 OFF · 주차 위치 확인 필요"
        needsParkingConfirmation -> "확인된 출입구 경로 없음"
        phoneSession?.notificationPosted == true -> if (arrival.destination?.demo == true) "시뮬레이션 · 도보 안내 알림 표시" else "도보 안내 알림 표시"
        arrival.walking -> if (arrival.destination?.demo == true) "시뮬레이션 · 앱에서 도보 안내 중" else "앱에서 도보 안내 중"
        arrival.destination?.demo == true -> "시뮬레이션 · 시동 OFF 대기"
        else -> "차량 연동 미연결"
    }
    val arrivalDemoRunning get() = !automotive && arrival.destination?.demo == true &&
        !arrival.walking && !arrival.completed && phoneSession?.ignitionOff != true
    private val comparisonDestination get() = arrival.destination?.let(parking::forNavigation)
    val hasComparisonData get() = arrival.destination == null || comparisonDestination != null
    val canOpenParking get() = canInteract &&
        hasComparisonData &&
        (!arrivalDemoRunning || arrival.vehicle.safelyParked)

    fun openParking() {
        if (showingParking || !canOpenParking) return
        comparisonDestination?.takeIf { it.id != destination.id }?.let(::selectDestination)
        showingParking = true
    }

    fun closeParking() {
        if (!canInteract) return
        reset()
        showingParking = false
    }

    internal fun toggleDemoMode() {
        if (canInteract) demoMode = !demoMode
    }

    internal fun startArrivalDemo(fix: DemoParkingFix = DemoParkingFix.GARAGE, entrance: ConfirmedEntrance = DemoArrivalWalks.east): Boolean {
        if (!demoMode || automotive || !canInteract || arrivalDemoRunning) return false
        val id = handoff.startDemo(fix, entrance, DEMO_OFF_DELAY) ?: return false
        closeCarReturn()
        parkingGuidance.resetOutbound()
        val moving = ArrivalVehicle(VehicleStatus.DRIVING, stationary = false, ignitionOff = false, driverSeated = true)
        arrival = ArrivalJourney(vehicle = moving).receiveDestination(DemoArrivalWalks.target.copy(entrance = entrance))
        arrivalPlayback?.cancel()
        arrivalPlayback = viewModelScope.launch {
            delay(1_500)
            if (phoneSession?.id != id || phoneSession?.ignitionOff == true) return@launch
            arrival = arrival.arrive(NaviArrival(DemoArrivalWalks.navigationStop))
            delay(1_500)
            if (phoneSession?.id != id || phoneSession?.ignitionOff == true) return@launch
            val parked = ArrivalVehicle(VehicleStatus.PARKED, stationary = true, ignitionOff = false, driverSeated = true)
            arrival = arrival.receiveVehicle(parked)
            DemoArrivalWalks.location(fix)?.let { arrival = arrival.recordParking(it, arrivalWalks) }
            delay(DEMO_OFF_DELAY - 3_000)
            handoff.runDemoOff(id)
        }
        return true
    }

    private fun syncPhoneHandoff(restoreDraft: Boolean = false) {
        val session = handoff.current()
        phoneSession = session
        if (session == null) return
        if (!session.ignitionOff) {
            if (restoreDraft && arrival.destination == null) {
                arrival = ArrivalJourney(
                    destination = session.destination,
                    waypoint = session.waypoint,
                    vehicle = if (session.destination.demo) ArrivalVehicle(VehicleStatus.DRIVING, stationary = false) else ArrivalVehicle()
                )
            }
            return
        }
        var value = arrival.receiveDestination(session.destination).copy(waypoint = session.waypoint)
        if (session.destination.demo) value = value.arrive(NaviArrival(DemoArrivalWalks.navigationStop))
        if (session.parkedAt != value.parkedAt) {
            value = value.copy(parkedAt = null, prepared = null, walking = false)
        }
        value = value.receiveVehicle(ArrivalVehicle(VehicleStatus.PARKED, stationary = true, ignitionOff = true))
        session.parkedAt?.let { value = value.recordParking(it, arrivalWalks, confirmed = true) }
        updateArrival(if (session.completed) value.finishWalking() else value)
    }

    fun openPhoneHandoff(id: String) {
        if (automotive || !canInteract || phoneSession?.id != id || phoneSession?.completed == true ||
            phoneSession?.route(arrivalWalks) == null) return
        closeCarReturn()
        syncPhoneHandoff()
        reset()
        showingParking = false
        openWalkRequest++
    }

    fun refreshPhoneNotification() {
        if (!automotive) handoff.refreshNotification()
    }

    fun confirmParkedLocation(point: Coordinate) {
        if (!needsParkingConfirmation) return
        if (automotive) updateArrival(arrival.recordParking(point, arrivalWalks, confirmed = true))
        else phoneSession?.let { handoff.confirmParking(it.id, point) }
    }

    fun simulateEntranceArrival() {
        if (demoMode && !automotive && canInteract && arrival.walking && arrival.prepared?.demo == true) {
            phoneSession?.let { handoff.finish(it.id) }
        }
    }

    internal var walkingLocation by mutableStateOf<Coordinate?>(null)
        private set

    internal fun clearWalkingLocation() {
        walkingLocation = null
        parkingGuidance.clearEvidence()
    }

    fun onWalkingLocation(point: Coordinate, accuracy: Float, timestampNanos: Long) {
        val plan = arrival.prepared?.takeIf { arrival.walking && !it.demo && !automotive }
        walkingLocation = point.takeIf { it.isValid() && accuracy in 0f..40f }
        parkingGuidance.update(plan, point, accuracy, timestampNanos)
        if (parkingGuidance.carReturn != null || plan == null) return
        val near = point.isValid() && accuracy in 0f..12f && plan.entranceCoordinate.distanceFrom(point) + accuracy <= 24
        if (!near) {
            nearEntranceFixAt = 0L
        } else if (nearEntranceFixAt == 0L || timestampNanos - nearEntranceFixAt > 10_000_000_000L) {
            nearEntranceFixAt = timestampNanos
        } else if (timestampNanos - nearEntranceFixAt >= 1_000_000_000L) {
            nearEntranceFixAt = 0L
            phoneSession?.let { handoff.finish(it.id) }
        }
    }

    override fun onCleared() {
        if (!automotive) handoff.stopObserving(handoffListener)
        parkingGuidance.close()
        super.onCleared()
    }

    internal fun parkingMessage(message: String) {
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_SHORT).show()
    }

    internal fun startCarReturn(saved: SavedParking, point: Coordinate? = saved.demoEntrance) {
        if (!canInteract) return
        if (point == null) {
            parkingMessage("현재 위치를 확인한 뒤 다시 시도해 주세요.")
        } else if (parkingGuidance.startReturn(saved, point)) {
            demoWalkingLocation = null
            if (showingParking) closeParking()
        }
    }

    internal fun closeCarReturn() {
        deviationPlayback?.cancel()
        demoWalkingLocation = null
        parkingGuidance.closeReturn()
    }

    internal val canSimulateDeviation get() = demoMode && !automotive &&
        !showingParking && (parkingGuidance.carReturn?.let { !it.completed && it.walk.demo }
            ?: (arrival.walking && arrival.prepared?.demo == true))

    internal fun simulateDeviation() {
        if (!canInteract || !canSimulateDeviation || deviationPlayback?.isActive == true) return
        deviationPlayback = viewModelScope.launch {
            val point = DemoWalkingPaths.deviationPoint
            demoWalkingLocation = point
            try {
                repeat(3) { index ->
                    if (index > 0) delay(2_100)
                    if (!canInteract || !canSimulateDeviation) return@launch
                    val outbound = arrival.prepared?.takeIf { arrival.walking && it.demo }
                    parkingGuidance.update(outbound, point, 4f, SystemClock.elapsedRealtimeNanos())
                }
                delay(750)
            } finally {
                demoWalkingLocation = null
            }
        }
    }

    private companion object {
        const val DEMO_OFF_DELAY = 5_600L
    }

    fun onNaviConnection(message: String) {
        if (automotive) naviConnection = message
    }

    fun onNaviDestination(destination: NaviDestination) {
        if (!automotive) return
        val next = arrival.receiveDestination(destination)
        if (showingParking && next.destination != arrival.destination && parking.forNavigation(destination) == null) {
            reset()
            showingParking = false
        }
        if (next.destination != arrival.destination) {
            next.destination?.let {
                val saved = handoff.current()
                if (saved?.destination != it) handoff.destinationSet(UUID.randomUUID().toString(), it)
            }
        }
        arrival = next
    }

    fun onNaviCancelled() {
        if (automotive && arrival.arrival == null && arrival.waypoint == null && arrival.parkedAt == null && !arrival.walking) {
            arrival = ArrivalJourney(vehicle = arrival.vehicle)
        }
    }

    fun onNaviArrived(point: Coordinate?) {
        if (automotive) arrival = arrival.arrive(NaviArrival(point))
    }

    fun onParkedLocation(point: Coordinate, capturedAtNanos: Long) {
        val since = parkedSinceNanos ?: return
        if (automotive && capturedAtNanos >= since) {
            parkedFix = point to capturedAtNanos
            saveHostParking()
            updateArrival(arrival.recordParking(point, arrivalWalks))
        }
    }

    private fun saveHostParking() {
        val event = parkingEvent ?: return
        if (event == savedParkingEvent) return
        val (point, capturedAt) = parkedFix ?: return
        if (SystemClock.elapsedRealtimeNanos() - capturedAt !in 0L..5_000_000_000L || !point.isValid()) return
        parkingGuidance.record(SavedParking(point, System.currentTimeMillis(), eventId = event))
        savedParkingEvent = event
    }

    fun openWalkingManually() {
        if (canInteract) updateArrival(arrival.startManually())
    }

    private fun updateArrival(value: ArrivalJourney) {
        if (value.walking && !arrival.walking) {
            reset()
            showingParking = false
        }
        arrival = value
    }

    fun search(query: String) = if (canInteract) parking.search(query) else emptyList()

    fun selectDestination(value: Destination) {
        if (!canInteract || stage != Stage.COMPARE) return
        destination = value
        selectedId = candidates.first().lot.id
        position = value.start
        error = null
    }

    fun selectLot(id: String) {
        if (canInteract && stage == Stage.COMPARE && candidates.any { it.lot.id == id }) {
            selectedId = id
            error = null
        }
    }

    fun setDuration(value: Int) {
        if (canInteract && stage == Stage.COMPARE && value > 0) minutes = value
    }

    fun sort(value: LotOrder) {
        if (canInteract && stage == Stage.COMPARE) order = value
    }

    fun simulateStatus(value: VehicleStatus) {
        if (demoMode && !automotive && stage != Stage.GUIDING) demoStatus = value
    }

    fun onHostVehicle(value: ArrivalVehicle) {
        if (!automotive) return
        hostStatus = if (value.stationary == false) VehicleStatus.DRIVING else value.status
        if (!value.safelyParked) {
            parkedSinceNanos = null
            parkedFix = null
            if (parkingGuidance.carReturn != null) closeCarReturn()
        }
        else if (parkedSinceNanos == null) parkedSinceNanos = SystemClock.elapsedRealtimeNanos()
        if (value.ignitionOff == false) {
            hostSawIgnitionOn = true
            parkingEvent = null
        }
        val observed = if (value.ignitionOff == true && !hostSawIgnitionOn) value.copy(ignitionOff = null) else value
        if (observed.safelyParked && observed.ignitionOff == true) {
            if (parkingEvent == null) parkingEvent = UUID.randomUUID().toString()
            saveHostParking()
        }
        updateArrival(arrival.receiveVehicle(observed))
        if (hostStatus != VehicleStatus.PARKED && stage == Stage.GUIDING) reset()
    }

    fun start() {
        if (!canInteract || stage != Stage.COMPARE) return
        guide(navigationTarget(destination, selected))
    }

    fun recordVehicleWaypoint(next: NavigationTarget) {
        if (arrival.destination == null) return
        val waypoint = VehicleWaypoint(next.label, next.coordinate)
        arrival = arrival.copy(waypoint = waypoint)
        handoff.current()?.let { handoff.waypointSet(it.id, waypoint) }
    }

    private fun guide(next: NavigationTarget) {
        if (!navigator.navigate(next)) {
            error = "안내를 시작할 수 없어요. 다시 시도해 주세요."
            return
        }
        recordVehicleWaypoint(next)
        error = null
        cue = null
        pendingSpeech = null
        target = next
        position = next.route.points.first()
        remainingMeters = next.route.meters
        stage = Stage.GUIDING
        val alert = if (demoMode) EntranceAlert(selected.approach) else null
        playback = viewModelScope.launch {
            val ticks = 72
            for (tick in 1..ticks) {
                delay(100)
                val fraction = tick.toDouble() / ticks
                val point = next.route.at(fraction)
                alert?.update(position, point)?.let { message ->
                    cue = message
                    pendingSpeech = message
                }
                position = point
                remainingMeters = (next.route.length * (1 - fraction)).roundToInt()
            }
            // Leave the final spoken cue visible for a beat before revealing the parked controls.
            if (cue != null) delay(1_600)
            stage = Stage.ARRIVED
        }
    }

    fun announceTestCue(message: String) {
        if (demoMode && automotive && canInteract) pendingSpeech = message
    }

    fun takeSpeech(): String? {
        val message = pendingSpeech
        pendingSpeech = null
        return message?.takeIf { canHearCue }
    }

    fun confirmParking() {
        if (canInteract && stage == Stage.ARRIVED && target != null) stage = Stage.WALKING
    }

    fun backToCompare() {
        if (canInteract) reset()
    }

    private fun reset() {
        playback?.cancel()
        stage = Stage.COMPARE
        target = null
        cue = null
        pendingSpeech = null
        error = null
        position = destination.start
        remainingMeters = 0
    }

    private var savedNotes by mutableStateOf<Map<String, ParkingNote>>(emptyMap())

    private fun noteKey(lot: Lot) = "${destination.id}/${lot.id}"

    fun parkingNoteFor(lot: Lot): ParkingNote {
        val key = noteKey(lot)
        return savedNotes[key] ?: ParkingNote(
            notes.getString("$key/floor", "").orEmpty(),
            notes.getString("$key/zone", "").orEmpty()
        )
    }

    fun saveNote(floor: String, zone: String) {
        if (!canInteract || stage != Stage.WALKING) return
        val key = noteKey(selected)
        val note = ParkingNote(floor.trim(), zone.trim())
        notes.edit().putString("$key/floor", note.floor).putString("$key/zone", note.zone).apply()
        savedNotes = savedNotes + (key to note)
    }
}








