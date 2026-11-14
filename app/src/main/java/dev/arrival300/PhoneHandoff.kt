package dev.arrival300

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.MainThread
import org.json.JSONObject
import java.util.UUID

/**
 * A trusted, paired vehicle adapter can deliver these events on the phone's main thread.
 * The bundled demo drives this interface locally. It does not open an IPC or network endpoint.
 */
internal interface VehicleHandoffInput {
    fun destinationSet(tripId: String, destination: NaviDestination)
    fun waypointSet(tripId: String, waypoint: VehicleWaypoint)
    fun ignitionTurnedOff(event: IgnitionOffEvent)
}

internal data class IgnitionOffEvent(
    val tripId: String,
    val safelyParked: Boolean,
    val confirmedPosition: Coordinate?
)

internal data class HandoffSession(
    val id: String,
    val destination: NaviDestination,
    val waypoint: VehicleWaypoint? = null,
    val ignitionOff: Boolean = false,
    val parkedAt: Coordinate? = null,
    val completed: Boolean = false,
    val notificationPosted: Boolean = false,
    val demoFix: DemoParkingFix? = null
) {
    fun accept(event: IgnitionOffEvent): HandoffSession {
        if (id != event.tripId || !event.safelyParked || ignitionOff || completed) return this
        return copy(ignitionOff = true, parkedAt = event.confirmedPosition?.takeIf(destination::acceptsParking))
    }

    fun confirmParking(point: Coordinate): HandoffSession {
        if (!ignitionOff || completed || !destination.acceptsParking(point)) return this
        return copy(parkedAt = point)
    }

    fun route(provider: ArrivalWalkProvider): ArrivalWalk? =
        parkedAt?.takeIf { ignitionOff }?.let { provider.verifiedRoute(destination, it) }
}

@MainThread
internal class PhoneHandoff(context: Context, private val provider: ArrivalWalkProvider = DemoArrivalWalks) : VehicleHandoffInput {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("arrival-handoff", Context.MODE_PRIVATE)
    private val notifications = app.getSystemService(NotificationManager::class.java)

    fun current(): HandoffSession? = preferences.getString(SESSION, null)?.let { raw ->
        runCatching {
            val json = JSONObject(raw)
            val location = json.getJSONObject("destination")
            val entrance = location.optJSONObject("entrance")?.let {
                ConfirmedEntrance(it.getString("id"), it.getString("name"), it.coordinate())
            }
            HandoffSession(
                id = json.getString("id"),
                destination = NaviDestination(location.getString("name"), location.getString("address"),
                    location.coordinate(), location.getBoolean("demo"), entrance),
                waypoint = json.optJSONObject("waypoint")?.let { VehicleWaypoint(it.getString("name"), it.coordinate()) },
                ignitionOff = json.optBoolean("off"),
                parkedAt = json.optJSONObject("parkedAt")?.coordinate(),
                completed = json.optBoolean("completed"),
                notificationPosted = json.optBoolean("notified"),
                demoFix = json.optString("demoFix").takeIf(String::isNotBlank)?.let(DemoParkingFix::valueOf)
            )
        }.getOrNull()
    }

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    override fun destinationSet(tripId: String, destination: NaviDestination) {
        if (tripId.isBlank() || !destination.coordinate.isValid()) return
        val previous = current()
        if (previous?.id == tripId && previous.destination == destination) return
        replace(HandoffSession(tripId, destination))
    }

    override fun waypointSet(tripId: String, waypoint: VehicleWaypoint) {
        val session = current() ?: return
        if (session.id == tripId && !session.ignitionOff && waypoint.coordinate.isValid()) {
            write(session.copy(waypoint = waypoint))
        }
    }

    override fun ignitionTurnedOff(event: IgnitionOffEvent) {
        val previous = current() ?: return
        val accepted = previous.accept(event)
        if (accepted != previous && write(accepted)) {
            saveParking(accepted)
            notifyIfReady(accepted)
        }
    }

    fun startDemo(fix: DemoParkingFix, entrance: ConfirmedEntrance, delayMillis: Long): String? {
        val session = HandoffSession(UUID.randomUUID().toString(), DemoArrivalWalks.target.copy(entrance = entrance), demoFix = fix)
        if (!replace(session)) return null
        app.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMillis, demoAlarm(session.id)
        )
        return session.id
    }

    fun runDemoOff(tripId: String) {
        val session = current()?.takeIf { it.id == tripId } ?: return
        val fix = session.demoFix ?: return
        ignitionTurnedOff(IgnitionOffEvent(tripId, safelyParked = true, confirmedPosition = DemoArrivalWalks.location(fix)))
    }

    fun confirmParking(tripId: String, point: Coordinate) {
        val session = current()?.takeIf { it.id == tripId } ?: return
        val next = session.confirmParking(point)
        if (next != session && write(next)) {
            saveParking(next)
            notifyIfReady(next)
        }
    }

    private fun saveParking(session: HandoffSession) {
        val point = session.parkedAt ?: return
        val value = SavedParking(point, System.currentTimeMillis(), demo = session.destination.demo, eventId = session.id,
            demoEntrance = session.destination.entrance?.coordinate.takeIf { session.destination.demo })
        if (ParkingPositionStore(app).record(value)) {
            Toast.makeText(app, "주차 위치가 저장되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshNotification() { current()?.let(::notifyIfReady) }

    fun finish(tripId: String) {
        val session = current()?.takeIf { it.id == tripId && !it.completed && it.route(provider) != null } ?: return
        if (write(session.copy(completed = true))) notifications?.cancel(NOTIFICATION_ID)
    }

    private fun replace(session: HandoffSession): Boolean {
        if (!write(session)) return false
        notifications?.cancel(NOTIFICATION_ID)
        return true
    }

    private fun write(session: HandoffSession): Boolean {
        val target = session.destination
        val destination = target.coordinate.json().put("name", target.name).put("address", target.address).put("demo", target.demo)
        target.entrance?.let { destination.put("entrance", it.coordinate.json().put("id", it.id).put("name", it.name)) }
        val json = JSONObject().put("id", session.id).put("destination", destination)
            .put("off", session.ignitionOff).put("completed", session.completed).put("notified", session.notificationPosted)
        session.waypoint?.let { json.put("waypoint", it.coordinate.json().put("name", it.name)) }
        session.parkedAt?.let { json.put("parkedAt", it.json()) }
        session.demoFix?.let { json.put("demoFix", it.name) }
        return preferences.edit().putString(SESSION, json.toString()).commit()
    }

    private fun notifyIfReady(session: HandoffSession) {
        if (session.completed || session.notificationPosted) return
        val route = session.route(provider) ?: return
        val manager = notifications ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "도보 안내", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val open = Intent(app, MainActivity::class.java).setAction(OPEN_WALK)
            .putExtra(TRIP_ID, session.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = "${route.destinationName} ${route.entranceName} · ${route.route.meters}m"
        val notification = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_walk_notification)
            .setContentTitle(if (session.destination.demo) "도보 안내 준비 완료 · 시뮬레이션" else "도보 안내 준비 완료")
            .setContentText(message)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .build()
        if (runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess) {
            write(session.copy(notificationPosted = true))
        }
    }

    private fun demoAlarm(id: String): PendingIntent {
        val intent = Intent(app, DemoIgnitionReceiver::class.java).putExtra(TRIP_ID, id)
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun Coordinate.json() = JSONObject().put("lat", latitude).put("lon", longitude)
    private fun JSONObject.coordinate() = Coordinate(getDouble("lat"), getDouble("lon"))

    companion object {
        const val OPEN_WALK = "dev.arrival300.OPEN_WALK"
        const val TRIP_ID = "dev.arrival300.TRIP_ID"
        const val SESSION = "active-trip"
        private const val CHANNEL = "walking-guidance"
        private const val NOTIFICATION_ID = 300
    }
}

class DemoIgnitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(PhoneHandoff.TRIP_ID)?.let { PhoneHandoff(context).runDemoOff(it) }
    }
}
