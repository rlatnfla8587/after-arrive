package dev.arrival300

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private val trip: TripViewModel by viewModels()
    private var safety: AaosSafety? = null
    private var sources: ArrivalSources? = null
    private var speech: KoreanSpeech? = null
    private var foreground by mutableStateOf(false)
    private var requestablePermissions by mutableStateOf(emptyList<String>())
    private var fineLocation by mutableStateOf(false)
    private var skipStartupIntro by mutableStateOf(false)
    private var pendingParkingAction: ParkingFixAction? = null
    private var parkingFixJob: Job? = null
    private val parkingPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pendingParkingAction
        pendingParkingAction = null
        refreshPermissions()
        if (action != null && result[Manifest.permission.ACCESS_FINE_LOCATION] == true) parkingAction(action)
        else if (action != null) trip.parkingMessage("정확한 위치 권한이 필요해요.")
    }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
        if (trip.automotive) {
            safety?.close()
            safety?.connect()
            sources?.foreground(true)
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        trip.refreshPhoneNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Arrival)
        super.onCreate(savedInstanceState)
        skipStartupIntro = savedInstanceState != null
        speech = KoreanSpeech(this)
        if (trip.automotive) {
            safety = AaosSafety(this) {
                trip.onHostVehicle(it)
                if (!trip.canHearCue) speech?.stop()
            }
            sources = createArrivalSources(this, trip)
            sources?.connect()
        }
        openNotification(intent)
        setContent {
            LaunchedEffect(trip.pendingSpeech) {
                trip.takeSpeech()?.let { speech?.speak(it) }
            }
            LaunchedEffect(trip.arrival.destination) { if (!trip.automotive) refreshPermissions() }
            val carReturn = trip.parkingGuidance.carReturn
            val canTrack = foreground && fineLocation && !trip.automotive
            val trackEntrance = trip.arrival.prepared?.takeIf {
                canTrack && carReturn == null && trip.arrival.walking && !it.demo
            }
            val trackReturn = canTrack && carReturn != null && !carReturn.completed && !carReturn.walk.demo
            DisposableEffect(trackEntrance, trackReturn) {
                val stop = if (trackEntrance != null || trackReturn) observeWalkingLocation() else null
                onDispose {
                    stop?.invoke()
                    trip.clearWalkingLocation()
                }
            }
            ArrivalTheme {
                val request = if (requestablePermissions.isNotEmpty()) {
                    { permissionRequest.launch(requestablePermissions.toTypedArray()) }
                } else null
                ArrivalStartup(window, show = !skipStartupIntro) {
                    ArrivalScreen(trip, request, onParkingAction = ::parkingAction, onDemo = ::startSimulation)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }

    private fun openNotification(intent: Intent?) {
        if (intent?.action == PhoneHandoff.OPEN_WALK) {
            skipStartupIntro = true
            intent.getStringExtra(PhoneHandoff.TRIP_ID)?.let(trip::openPhoneHandoff)
        }
    }

    private fun startSimulation(fix: DemoParkingFix, entrance: ConfirmedEntrance) {
        if (!trip.startArrivalDemo(fix, entrance)) return
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val preferences = getSharedPreferences("app-settings", MODE_PRIVATE)
        if (!preferences.getBoolean("notification-permission-asked", false)) {
            preferences.edit().putBoolean("notification-permission-asked", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        refreshPermissions()
        safety?.connect()
        sources?.foreground(true)
        trip.refreshPhoneNotification()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onStop() {
        foreground = false
        parkingFixJob?.cancel()
        speech?.stop()
        sources?.foreground(false)
        super.onStop()
    }

    private fun parkingAction(action: ParkingFixAction) {
        if (!trip.canInteract || parkingFixJob?.isActive == true) return
        val saved = if (action == ParkingFixAction.FIND) trip.parkingGuidance.actual ?: return else null
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingParkingAction = action
            parkingPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        trip.parkingMessage("현재 GPS 위치를 확인하고 있어요.")
        parkingFixJob = lifecycleScope.launch {
            val location = readParkingFix()
            if (!trip.canInteract) return@launch
            if (location == null) {
                trip.parkingMessage("GPS 위치를 확인할 수 없어요. 위치 수신이 가능한 곳에서 다시 시도해 주세요.")
                return@launch
            }
            val point = Coordinate(location.latitude, location.longitude)
            if (saved == null) trip.parkingGuidance.recordGps(point, location.accuracy)
            else if (location.accuracy <= 20f && saved == trip.parkingGuidance.actual) trip.startCarReturn(saved, point)
            else trip.parkingMessage("현재 위치가 부정확하거나 저장된 위치가 바뀌었어요. 다시 시도해 주세요.")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun readParkingFix(): Location? {
        val manager = getSystemService(LocationManager::class.java) ?: return null
        var best: Location? = null
        val fix = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val now = SystemClock.elapsedRealtimeNanos()
                        if (now - location.elapsedRealtimeNanos !in 0L..5_000_000_000L ||
                            !location.hasAccuracy() || location.accuracy !in 0f..100f) return
                        val previous = best?.takeIf { now - it.elapsedRealtimeNanos <= 5_000_000_000L }
                        if (previous == null || location.accuracy <= previous.accuracy) best = location
                        if (location.accuracy <= 20f && continuation.isActive) {
                            manager.removeUpdates(this)
                            continuation.resume(location)
                        }
                    }

                    override fun onProviderDisabled(provider: String) {
                        if (!continuation.isActive) return
                        best = null
                        manager.removeUpdates(this)
                        continuation.resume(null)
                    }

                    override fun onProviderEnabled(provider: String) = Unit
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                runCatching {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
        return fix ?: best?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0L..5_000_000_000L }
    }

    override fun onDestroy() {
        sources?.close()
        safety?.close()
        speech?.close()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun refreshPermissions() {
        fineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val names = if (trip.automotive) arrivalPermissionNames
            else if (trip.arrival.destination?.demo == false) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            else emptyArray()
        val missing = names.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED &&
                runCatching { packageManager.getPermissionInfo(it, 0) }.isSuccess
        }
        requestablePermissions = if (Manifest.permission.ACCESS_FINE_LOCATION in missing) {
            (missing + Manifest.permission.ACCESS_COARSE_LOCATION).distinct()
        } else missing
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun observeWalkingLocation(): (() -> Unit)? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = getSystemService(LocationManager::class.java) ?: return null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                if (age in 0L..5_000_000_000L && location.hasAccuracy()) {
                    trip.onWalkingLocation(Coordinate(location.latitude, location.longitude), location.accuracy, location.elapsedRealtimeNanos)
                }
            }

            override fun onProviderDisabled(provider: String) = trip.clearWalkingLocation()
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        return runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            val stop: () -> Unit = { manager.removeUpdates(listener) }
            stop
        }.getOrNull()
    }
}

