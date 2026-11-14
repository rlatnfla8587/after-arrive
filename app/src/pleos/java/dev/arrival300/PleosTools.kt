package dev.arrival300

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A separate emulator test surface, so simulated parking never changes the vehicle's route. */
@Composable
internal fun PleosTools(trip: TripViewModel) {
    val context = LocalContext.current
    if (!trip.automotive || !trip.canInteract || context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("Pleos 연동 시험") }
    if (!open) return

    val target = trip.target ?: navigationTarget(trip.destination, trip.selected)
    PleosDialog(trip, target) { open = false }
}

@Composable
private fun PleosDialog(trip: TripViewModel, target: NavigationTarget, onClose: () -> Unit) {
    val context = LocalContext.current
    var navStatus by remember(target) { mutableStateOf("내비 연결 대기") }
    var gpsStatus by remember(target) { mutableStateOf("위치 수신 대기") }
    val finalDestination = trip.arrival.destination
    val navigation = remember(target, finalDestination) {
        PleosNavigator(context, finalDestination, trip::recordVehicleWaypoint) { navStatus = it }
    }
    val gps = remember(target) {
        ForegroundApproach(context, trip.destination.origin, trip.selected.approach, { gpsStatus = it }, trip::announceTestCue)
    }
    DisposableEffect(navigation, gps) {
        navigation.connect()
        onDispose {
            gps.close()
            navigation.close()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true && trip.canInteract) gps.start()
        else gpsStatus = "정확한 위치 권한이 필요해요."
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pleos 연동 시험") },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("에뮬레이터 전용 시연 좌표입니다.")
                Text(target.label, color = Blue)
                Text(String.format(Locale.ROOT, "%.6f, %.6f", target.coordinate.latitude, target.coordinate.longitude))
                Text(navStatus)
                OutlinedButton(onClick = {
                    if (trip.canInteract) navigation.navigate(target)
                }, enabled = navigation.canRequest) { Text("Pleos 내비 경유지 추가") }
                if (finalDestination == null) Text("시스템 내비에서 최종 목적지를 먼저 설정하세요.")
                Text(gpsStatus, color = Gold)
                OutlinedButton(onClick = {
                    if (trip.canInteract) {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            gps.start()
                        } else {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }
                }) { Text("GPS 접근 감지 시작") }
            }
        }
    )
}

private class PleosNavigator(
    context: Context,
    private val finalDestination: NaviDestination?,
    private val onAccepted: (NavigationTarget) -> Unit,
    private val show: (String) -> Unit
) : Navigator {
    private val helper = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var target: NavigationTarget? = null
    private var connected by mutableStateOf(false)
    private var awaiting by mutableStateOf(false)
    private var closed = false
    private var originalConfirmed = false
    private var waypointConfirmed = false
    val canRequest get() = connected && !awaiting && finalDestination != null

    private val timeout = Runnable {
        finish("내비 응답 시간이 지났어요.")
        connected = false
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRoutingCompleted() { main.post(::queryRoute) }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) { main.post(::queryRoute) }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            main.post {
                if (closed || !awaiting) return@post
                val actual = Coordinate(destinationInfo.latitude, destinationInfo.longitude)
                val original = finalDestination ?: return@post
                originalConfirmed = original.coordinate.distanceFrom(actual) <= 35
                finishIfConfirmed()
            }
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            main.post {
                val requested = target ?: return@post
                if (closed || !awaiting) return@post
                waypointConfirmed = waypointInfoList.any {
                    requested.coordinate.distanceFrom(Coordinate(it.latitude, it.longitude)) <= 35
                }
                finishIfConfirmed()
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            main.post { if (!closed && awaiting) finish("내비 응답 오류 · $errorCode") }
        }
    }

    fun connect() {
        runCatching {
            helper.initialize(
                onConnected = { main.post {
                    if (!closed) runCatching {
                        helper.addListener(listener)
                        connected = true
                        show("내비 서비스 연결됨")
                    }.onFailure { show("내비 이벤트 등록 실패") }
                } },
                onFailed = { main.post { if (!closed) show("내비 서비스에 연결할 수 없어요.") } }
            )
        }.onFailure { show("내비 SDK 초기화 실패") }
    }

    override fun navigate(target: NavigationTarget): Boolean {
        if (closed || !canRequest) return false
        this.target = target
        originalConfirmed = false
        waypointConfirmed = false
        awaiting = true
        show("내비 경로 요청 중…")
        main.postDelayed(timeout, 15_000)
        return runCatching {
            helper.addWaypoint(
                RequestWaypointInfo(
                    latitude = target.coordinate.latitude,
                    longitude = target.coordinate.longitude,
                    poiName = target.label,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = "",
                    address = "",
                    poiSubId = ""
                )
            )
            true
        }.getOrElse {
            finish("내비 경로 요청에 실패했어요.")
            false
        }
    }

    private fun queryRoute() {
        if (closed || !awaiting) return
        runCatching {
            helper.getDestinationInfo()
            helper.getWaypointInfo()
        }.onFailure { finish("최종 목적지와 경유지를 조회할 수 없어요.") }
    }

    private fun finishIfConfirmed() {
        if (!originalConfirmed || !waypointConfirmed) return
        val requested = target ?: return
        onAccepted(requested)
        finish("최종 목적지 유지 · 경유지 추가 완료")
    }

    private fun finish(message: String) {
        main.removeCallbacks(timeout)
        awaiting = false
        target = null
        show(message)
        Log.i("Arrival300Pleos", message)
    }

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
    }
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class ForegroundApproach(
    context: Context,
    private val origin: Coordinate,
    private val approach: Approach,
    private val show: (String) -> Unit,
    private val announce: (String) -> Unit
) : LocationListener {
    private val location = context.getSystemService(LocationManager::class.java)
    private var running = false
    private var previous: Point? = null
    private var lastFix = 0L
    private var alert = EntranceAlert(approach)

    @SuppressLint("MissingPermission")
    fun start() {
        close()
        previous = null
        lastFix = 0L
        alert = EntranceAlert(approach)
        running = true
        runCatching {
            requireNotNull(location).requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper())
            show("GPS 위치 수신 중…")
        }.onFailure {
            running = false
            show("GPS를 시작할 수 없어요.")
        }
    }

    override fun onLocationChanged(fix: Location) {
        val age = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
        if (!running || !fix.hasAccuracy() || fix.accuracy > 35f || age !in 0L..5_000_000_000L ||
            fix.elapsedRealtimeNanos <= lastFix) return
        lastFix = fix.elapsedRealtimeNanos
        val point = origin.toLocal(Coordinate(fix.latitude, fix.longitude))
        val message = previous?.let { alert.update(it, point) }
        previous = point
        if (message != null) {
            show(message)
            announce(message)
            close()
        } else {
            show("진입구까지 직선 ${point.distance(approach.entrance).toInt()}m · GPS")
        }
    }

    override fun onProviderDisabled(provider: String) { if (running) show("GPS가 꺼져 있어요.") }
    override fun onProviderEnabled(provider: String) = Unit
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    fun close() {
        running = false
        runCatching { location?.removeUpdates(this) }
    }
}




