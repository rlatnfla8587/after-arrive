package dev.arrival300

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneHandoffTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun alarmReceiverNotifiesWithoutLaunchingActivityAndDeepLinkNamesPersistedTrip() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val handoff = PhoneHandoff(app)
        val id = requireNotNull(handoff.startDemo(DemoParkingFix.CURB, DemoArrivalWalks.east, 5_600))
        val waypoint = VehicleWaypoint("추천 주차장", DemoArrivalWalks.target.coordinate.offset(p(450, 0)))
        handoff.waypointSet(id, waypoint)
        handoff.ignitionTurnedOff(IgnitionOffEvent(id, false, DemoArrivalWalks.parkedAtCurb))
        assertFalse(requireNotNull(handoff.current()).ignitionOff)
        val alarm = app.getSystemService(android.app.AlarmManager::class.java)
        val scheduled = requireNotNull(shadowOf(alarm).nextScheduledAlarm)
        requireNotNull(scheduled.operation).send()
        shadowOf(Looper.getMainLooper()).idle()
        val record = requireNotNull(PhoneHandoff(app).current())
        assertEquals(DemoArrivalWalks.target, record.destination)
        assertEquals(waypoint, record.waypoint)
        assertEquals(DemoArrivalWalks.parkedAtCurb, record.parkedAt)
        assertTrue(record.notificationPosted)
        val manager = app.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        assertTrue(notification.extras.getString(Notification.EXTRA_TITLE).orEmpty().contains("도보 안내"))
        val launch = shadowOf(notification.contentIntent).savedIntent
        assertEquals(PhoneHandoff.OPEN_WALK, launch.action)
        assertEquals(id, launch.getStringExtra(PhoneHandoff.TRIP_ID))
        assertNull(shadowOf(app).nextStartedActivity)
        handoff.runDemoOff(id)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        handoff.finish(id)
        assertTrue(requireNotNull(handoff.current()).completed)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun missingLocationAndDeniedNotificationsKeepRouteAvailableAfterConfirmation() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val handoff = PhoneHandoff(app)
        val id = requireNotNull(handoff.startDemo(DemoParkingFix.UNKNOWN, DemoArrivalWalks.main, 5_600))
        handoff.runDemoOff(id)
        val off = requireNotNull(handoff.current())
        assertTrue(off.ignitionOff)
        assertNull(off.route(DemoArrivalWalks))
        handoff.confirmParking(id, DemoArrivalWalks.parkedInGarage)
        val ready = requireNotNull(PhoneHandoff(app).current())
        assertEquals(DemoArrivalWalks.main.coordinate, ready.route(DemoArrivalWalks)?.entranceCoordinate)
        assertFalse(ready.notificationPosted)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
