package dev.arrival300

import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

internal val arrivalPermissionNames = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    "android.car.permission.CAR_SPEED",
    "android.car.permission.READ_CAR_SEATS"
)

internal fun createArrivalSources(context: Context, trip: TripViewModel): ArrivalSources? =
    if (trip.automotive) NativeArrivalSources(context.applicationContext, trip) else null

private class NativeArrivalSources(private val context: Context, private val trip: TripViewModel) : ArrivalSources {
    private val main = Handler(Looper.getMainLooper())
    private val helper = NaviHelper(context)
    private val locations = context.getSystemService(LocationManager::class.java)
    private var closed = false
    private var connected = false
    private var activeDestination: NaviDestination? = null
    private var pendingArrival: DestinationArrivedInfo? = null
    private var recentFix: Location? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationChanged() = enqueue { queryDestination() }
        override fun onRoutingCompleted() = enqueue { queryDestination() }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = enqueue {
            pendingArrival = null
            // Replanning or adding a waypoint preserves the active final destination.
        }
        override fun onRouteCancelled() = enqueue {
            activeDestination = null
            pendingArrival = null
            trip.onNaviCancelled()
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) = enqueue {
            val point = Coordinate(destinationInfo.latitude, destinationInfo.longitude)
            if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0 ||
                (point.latitude == 0.0 && point.longitude == 0.0)) return@enqueue
            val destination = NaviDestination(
                destinationInfo.name.ifBlank { "내비 목적지" },
                destinationInfo.address,
                point
            )
            activeDestination = destination
            trip.onNaviDestination(destination)
            pendingArrival?.let { if (matches(it, destination)) publishArrival() }
            pendingArrival = null
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = enqueue {
            val destination = activeDestination
            if (destination != null && matches(destinationArrivedInfo, destination)) {
                publishArrival()
            } else {
                pendingArrival = destinationArrivedInfo
                queryDestination()
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!fresh(location)) return
            recentFix = location
            trip.onParkedLocation(location.coordinate(), location.elapsedRealtimeNanos)
        }

        override fun onProviderDisabled(provider: String) { recentFix = null }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private fun matches(event: DestinationArrivedInfo, destination: NaviDestination): Boolean {
        val name = event.name.trim()
        val address = event.address.trim()
        return (name.isNotEmpty() && name == destination.name.trim()) ||
            (address.isNotEmpty() && address == destination.address.trim())
    }

    private fun publishArrival() {
        // NaviHelper's arrival payload has no latitude/longitude.
        trip.onNaviArrived(recentFix?.takeIf(::fresh)?.coordinate())
    }

    private fun fresh(location: Location) = location.hasAccuracy() && location.accuracy in 0f..20f &&
        SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0L..5_000_000_000L

    private fun Location.coordinate() = Coordinate(latitude, longitude)

    override fun connect() {
        runCatching {
            helper.initialize(
                onConnected = { enqueue {
                    runCatching {
                        helper.addListener(listener)
                        connected = true
                        trip.onNaviConnection("차량 내비가 연결됐어요. 목적지를 설정해 주세요.")
                        Log.i("Arrival300", "NaviHelper connected")
                        queryDestination()
                    }.onFailure { trip.onNaviConnection("내비 이벤트를 구독할 수 없어요.") }
                } },
                onFailed = { enqueue {
                    trip.onNaviConnection("차량 내비 서비스 연결을 확인해 주세요.")
                    Log.w("Arrival300", "NaviHelper connection unavailable")
                } }
            )
        }.onFailure { trip.onNaviConnection("차량 내비 연결을 시작할 수 없어요.") }
    }

    private fun queryDestination() {
        if (connected) runCatching { helper.getDestinationInfo() }
            .onFailure { trip.onNaviConnection("내비 목적지를 조회할 수 없어요.") }
    }

    private fun enqueue(action: () -> Unit) { main.post { if (!closed) action() } }

    @SuppressLint("MissingPermission")
    override fun foreground(active: Boolean) {
        runCatching { locations?.removeUpdates(locationListener) }
        recentFix = null
        if (active && !closed &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                locations?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locationListener, Looper.getMainLooper())
            }
        }
        if (active) queryDestination()
    }

    override fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        foreground(false)
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
    }
}



