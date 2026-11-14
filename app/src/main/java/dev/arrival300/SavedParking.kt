package dev.arrival300

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.UUID

internal data class SavedParking(
    val coordinate: Coordinate,
    val savedAt: Long,
    val demo: Boolean = false,
    val eventId: String = UUID.randomUUID().toString(),
    val demoEntrance: Coordinate? = null,
    val floor: String = "",
    val zone: String = "",
    val memo: String = ""
)

internal class ParkingPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences("parking-notes", Context.MODE_PRIVATE)

    fun load(demo: Boolean): SavedParking? = preferences.getString(key(demo), null)?.let { raw ->
        runCatching {
            val json = JSONObject(raw)
            SavedParking(
                coordinate = json.point(),
                savedAt = json.getLong("time"),
                demo = demo,
                eventId = json.getString("event"),
                demoEntrance = json.optJSONObject("demoEntrance")?.point(),
                floor = json.optString("floor"),
                zone = json.optString("zone"),
                memo = json.optString("memo")
            ).takeIf { it.coordinate.isValid() }
        }.getOrNull()
    }

    fun record(value: SavedParking): Boolean =
        load(value.demo)?.eventId != value.eventId && write(value)

    fun revise(original: SavedParking, value: SavedParking): Boolean =
        load(original.demo) == original && original.eventId == value.eventId && original.demo == value.demo && write(value)

    private fun write(value: SavedParking): Boolean {
        if (!value.coordinate.isValid()) return false
        val json = value.coordinate.json().put("time", value.savedAt).put("event", value.eventId)
            .put("floor", value.floor).put("zone", value.zone).put("memo", value.memo)
        value.demoEntrance?.let { json.put("demoEntrance", it.json()) }
        return preferences.edit().putString(key(value.demo), json.toString()).commit()
    }

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    private fun Coordinate.json() = JSONObject().put("lat", latitude).put("lon", longitude)
    private fun JSONObject.point() = Coordinate(getDouble("lat"), getDouble("lon"))

    companion object {
        fun key(demo: Boolean) = if (demo) "saved-position/demo" else "saved-position/real"
    }
}

internal enum class ParkingFixAction { SAVE, FIND }

internal data class ParkingEdit(val value: SavedParking, val newFix: Boolean = false, val accuracy: Float? = null)

internal data class CarReturn(val parking: SavedParking, val walk: ArrivalWalk, val completed: Boolean = false)

internal class ParkingGuidance(
    context: Context,
    private val provider: ArrivalWalkProvider,
    private val message: (String) -> Unit
) {
    private val store = ParkingPositionStore(context)
    var actual by mutableStateOf(store.load(false))
        private set
    var sample by mutableStateOf(store.load(true))
        private set
    var edit by mutableStateOf<ParkingEdit?>(null)
        private set
    var carReturn by mutableStateOf<CarReturn?>(null)
        private set
    private var replacement by mutableStateOf<Pair<ArrivalWalk, ArrivalWalk>?>(null)
    private val monitor = WalkingDeviation()
    private var monitored: ArrivalWalk? = null
    private var wasReturning = false
    private var reportedFailure = false
    private var nearCarAt = 0L
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            ParkingPositionStore.key(false) -> actual = store.load(false)
            ParkingPositionStore.key(true) -> sample = store.load(true)
        }
    }

    init { store.observe(listener) }

    val saved get() = actual ?: sample

    fun record(value: SavedParking) {
        if (store.record(value)) message("주차 위치가 저장되었습니다")
    }

    fun recordGps(point: Coordinate, accuracy: Float) {
        if (!point.isValid() || accuracy !in 0f..100f) {
            message("정확한 GPS 위치를 확인할 수 없어요.")
            return
        }
        val value = SavedParking(point, System.currentTimeMillis())
        if (accuracy <= 20f) record(value) else edit = ParkingEdit(value, newFix = true, accuracy = accuracy)
    }

    fun showEdit(saved: SavedParking) { edit = ParkingEdit(saved) }
    fun closeEdit() { edit = null }

    fun saveEdit(value: SavedParking) {
        val original = edit ?: return
        val revised = value.copy(savedAt = original.value.savedAt)
        val saved = if (original.newFix) store.record(revised) else store.revise(original.value, revised)
        if (saved) {
            edit = null
            message("주차 위치가 저장되었습니다")
        } else message("주차 위치가 바뀌었어요. 저장된 위치를 다시 열어 주세요.")
    }

    fun startReturn(saved: SavedParking, point: Coordinate): Boolean {
        val path = provider.pathBetween(point, saved.coordinate, saved.demo)
        if (path == null) {
            message("현재 위치에서 차량까지 확인된 보행 경로가 없어요.")
            return false
        }
        val label = if (saved.demo) "시연 · 저장된 주차 위치" else "저장된 주차 위치"
        carReturn = CarReturn(saved, ArrivalWalk(label, "주차 위치", path.origin, path.route,
            "현재 위치", null, path.steps, saved.demo))
        nearCarAt = 0
        monitor.newPath(keepCooldown = false)
        return true
    }

    fun closeReturn() {
        if (carReturn == null) return
        carReturn = null
        monitor.newPath(keepCooldown = false)
    }
    fun display(plan: ArrivalWalk): ArrivalWalk = replacement?.takeIf { it.first == plan }?.second ?: plan

    fun update(outbound: ArrivalWalk?, point: Coordinate, accuracy: Float, timestamp: Long) {
        val returning = carReturn?.takeUnless { it.completed }
        if (carReturn?.completed == true || !point.isValid()) return
        val source = returning?.walk ?: outbound ?: return
        val active = if (returning == null) display(source) else source
        if (returning != null && arrivedAtCar(returning, point, accuracy, timestamp)) {
            carReturn = returning.copy(completed = true)
            return
        }
        val isReturn = returning != null
        if (active != monitored || isReturn != wasReturning) {
            monitor.newPath()
            monitored = active
            wasReturning = isReturn
            reportedFailure = false
        }
        val shouldReroute = monitor.update(active.route, active.origin.toLocal(point), accuracy, timestamp)
        if (!monitor.hasDeviation && accuracy in 0f..15f) reportedFailure = false
        if (!shouldReroute) return
        val path = provider.pathBetween(point, active.entranceCoordinate, active.demo)
        if (path == null) {
            if (!reportedFailure) message("새 경로를 찾지 못했어요. 기존 경로를 계속 표시합니다.")
            reportedFailure = true
            return
        }
        val revised = active.copy(origin = path.origin, route = path.route, startLabel = "현재 위치",
            pedestrianExit = null, steps = path.steps)
        if (returning != null) carReturn = returning.copy(walk = revised)
        else replacement = source to revised
        message("경로를 벗어났어요. 새로운 경로로 안내합니다.")
    }

    private fun arrivedAtCar(returning: CarReturn, point: Coordinate, accuracy: Float, timestamp: Long): Boolean {
        if (accuracy !in 0f..12f || point.distanceFrom(returning.parking.coordinate) + accuracy > 24) {
            nearCarAt = 0
            return false
        }
        if (nearCarAt == 0L || timestamp - nearCarAt > 10_000_000_000L) {
            nearCarAt = timestamp
            return false
        }
        return timestamp - nearCarAt >= 1_000_000_000L
    }

    fun clearEvidence() {
        nearCarAt = 0
        monitor.clearEvidence()
    }

    fun resetOutbound() {
        replacement = null
        monitor.newPath(keepCooldown = false)
    }

    fun close() { store.stopObserving(listener) }
}
