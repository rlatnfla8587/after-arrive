package dev.arrival300

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingGuidanceTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val parking = DemoArrivalWalks.parkedInGarage

    @Test fun parkingEventStoresTimeAndCoordinatesAndEditsSurviveRecreation() {
        val handoff = PhoneHandoff(app)
        val store = ParkingPositionStore(app)
        val id = requireNotNull(handoff.startDemo(DemoParkingFix.GARAGE, DemoArrivalWalks.east, 5_600))
        assertNull(store.load(true))
        handoff.ignitionTurnedOff(IgnitionOffEvent(id, false, parking))
        assertNull(store.load(true))
        handoff.runDemoOff(id)
        val original = requireNotNull(store.load(true))
        assertEquals(parking, original.coordinate)
        assertTrue(original.savedAt > 0)
        val edited = original.copy(coordinate = DemoArrivalWalks.parkedAtCurb, floor = "B2", zone = "A-12", memo = "기둥 옆")
        assertTrue(store.revise(original, edited))
        handoff.runDemoOff(id)
        assertEquals(edited, ParkingPositionStore(app).load(true))
        assertNull(store.load(false))
        assertEquals(parking, handoff.current()?.parkedAt)
    }

    @Test fun missingParkingNeedsConfirmationAndPoorManualGpsDoesNotOverwriteSavedLocation() {
        val handoff = PhoneHandoff(app)
        val id = requireNotNull(handoff.startDemo(DemoParkingFix.UNKNOWN, DemoArrivalWalks.east, 5_600))
        handoff.runDemoOff(id)
        val store = ParkingPositionStore(app)
        assertNull(store.load(true))
        handoff.confirmParking(id, parking)
        assertEquals(parking, store.load(true)?.coordinate)
        val guidance = ParkingGuidance(app, DemoArrivalWalks) {}
        guidance.recordGps(parking, 65f)
        assertNull(store.load(false))
        assertEquals(65f, guidance.edit?.accuracy)
        guidance.closeEdit()
        guidance.recordGps(parking, 8f)
        val accurate = requireNotNull(store.load(false))
        guidance.recordGps(DemoArrivalWalks.parkedAtCurb, 160f)
        assertEquals(accurate, store.load(false))
        guidance.close()
    }

    @Test fun returnRouteUsesSavedPinAndCanRerouteWithoutChangingTheOutboundPlan() {
        val messages = mutableListOf<String>()
        val guidance = ParkingGuidance(app, DemoArrivalWalks, messages::add)
        val saved = SavedParking(parking, 1, demo = true, demoEntrance = DemoArrivalWalks.east.coordinate)
        val origin = requireNotNull(saved.demoEntrance)
        val outbound = requireNotNull(DemoArrivalWalks.verifiedRoute(DemoArrivalWalks.target, parking))
        assertTrue(guidance.startReturn(saved, origin))
        val original = requireNotNull(guidance.carReturn).walk
        assertTrue(original.entranceCoordinate.distanceFrom(saved.coordinate) <= 2)
        assertTrue(original.origin.offset(original.route.points.first()).distanceFrom(origin) <= 2)
        val detour = DemoWalkingPaths.deviationPoint
        for (seconds in listOf(1L, 3L, 5L)) guidance.update(outbound, detour, 4f, seconds * 1_000_000_000L)
        val revised = requireNotNull(guidance.carReturn).walk
        assertNotEquals(original.route, revised.route)
        assertTrue(revised.origin.offset(revised.route.points.first()).distanceFrom(detour) <= 2)
        assertTrue(revised.entranceCoordinate.distanceFrom(saved.coordinate) <= 2)
        assertEquals(outbound, guidance.display(outbound))
        assertEquals(1, messages.size)
        guidance.closeReturn()
        assertNull(guidance.carReturn)
        assertEquals(outbound, guidance.display(outbound))
        guidance.close()
    }

    @Test fun outboundReroutesOnReliableSustainedDeviationAndRetainsPathOnFailure() {
        val messages = mutableListOf<String>()
        val guidance = ParkingGuidance(app, DemoArrivalWalks, messages::add)
        val outbound = requireNotNull(DemoArrivalWalks.verifiedRoute(DemoArrivalWalks.target, parking))
        val detour = DemoWalkingPaths.deviationPoint
        guidance.update(outbound, detour, 4f, 1_000_000_000)
        guidance.update(outbound, detour, 28f, 3_000_000_000)
        guidance.update(outbound, detour, 4f, 5_000_000_000)
        assertEquals(outbound, guidance.display(outbound))
        for (seconds in listOf(7L, 9L)) guidance.update(outbound, detour, 4f, seconds * 1_000_000_000L)
        val replacement = guidance.display(outbound)
        assertNotEquals(outbound, replacement)
        assertTrue(replacement.entranceCoordinate.distanceFrom(outbound.entranceCoordinate) <= 2)
        val outside = detour.offset(p(500, 500))
        for (seconds in 10L..75L step 2) guidance.update(outbound, outside, 4f, seconds * 1_000_000_000L)
        assertEquals(replacement, guidance.display(outbound))
        assertEquals(2, messages.size)
        guidance.close()
    }

    @Test fun samePathBackwardMovementMustPersistBeforeRerouting() {
        val detector = WalkingDeviation()
        val route = walking(p(0, 0), p(100, 0))
        assertFalse(detector.update(route, p(80, 0), 4f, 1_000_000_000))
        assertFalse(detector.update(route, p(70, 0), 4f, 3_000_000_000))
        assertFalse(detector.update(route, p(42, 0), 4f, 5_000_000_000))
        assertFalse(detector.update(route, p(38, 0), 4f, 7_000_000_000))
        assertTrue(detector.update(route, p(35, 0), 4f, 9_000_000_000))
    }
}
