package dev.arrival300

import org.junit.Assert.*
import org.junit.Test

class ArrivalJourneyTest {
    private val moving = ArrivalVehicle(VehicleStatus.DRIVING, stationary = false, ignitionOff = false, driverSeated = true)
    private val parked = moving.copy(status = VehicleStatus.PARKED, stationary = true)
    private val entered = ArrivalJourney(vehicle = moving).receiveDestination(DemoArrivalWalks.target)
    private val arrived = entered.arrive(NaviArrival(DemoArrivalWalks.navigationStop))

    @Test fun arrivalAloneWaitsAndNeverUsesTheNavigationStopAsTheWalkingStart() {
        assertFalse(arrived.walking)
        assertFalse(arrived.canStartManually)
        assertNull(arrived.recordParking(DemoArrivalWalks.navigationStop, DemoArrivalWalks).parkedAt)
        val prepared = arrived.receiveVehicle(parked).recordParking(DemoArrivalWalks.parkedInGarage, DemoArrivalWalks)
        val walk = requireNotNull(prepared.prepared)
        assertEquals(DemoArrivalWalks.parkedInGarage, walk.origin.offset(walk.route.points.first()))
        assertNotEquals(arrived.arrival?.point, walk.origin.offset(walk.route.points.first()))
        assertEquals(DemoArrivalWalks.east.coordinate, walk.entranceCoordinate)
        assertTrue(walk.route.points.contains(walk.pedestrianExit))
        assertFalse(prepared.walking)
    }

    @Test fun parkedIgnitionOffStartsWithoutAnExitSignalOrVehicleArrivalEvent() {
        val curb = entered.receiveVehicle(parked).recordParking(DemoArrivalWalks.parkedAtCurb, DemoArrivalWalks)
        assertNull(curb.arrival)
        assertFalse(curb.receiveVehicle(parked.copy(driverSeated = false)).walking)
        assertFalse(curb.receiveVehicle(moving.copy(ignitionOff = true)).walking)
        assertTrue(curb.receiveVehicle(parked.copy(ignitionOff = true, driverSeated = true)).walking)
    }

    @Test fun missingIgnitionSignalOffersManualStartOnlyWhileSafelyParked() {
        val unknownPower = parked.copy(ignitionOff = null)
        val state = arrived.receiveVehicle(unknownPower).recordParking(DemoArrivalWalks.parkedAtCurb, DemoArrivalWalks)
        assertTrue(state.canStartManually)
        assertTrue(state.startManually().walking)
        assertFalse(state.receiveVehicle(unknownPower.copy(stationary = null)).startManually().walking)
        assertFalse(state.receiveVehicle(unknownPower.copy(stationary = false)).canStartManually)
        assertEquals("새봄로 지상 정차 구역", state.prepared?.startLabel)
        assertNull(state.prepared?.pedestrianExit)
    }

    @Test fun movementClearsOldParkingAndConfirmedCorrectionCanStartAfterOff() {
        val firstPark = arrived.receiveVehicle(parked).recordParking(DemoArrivalWalks.parkedInGarage, DemoArrivalWalks)
        val moved = firstPark.receiveVehicle(moving)
        assertNull(moved.parkedAt)
        assertNull(moved.prepared)
        val waitingForFix = moved.receiveVehicle(parked.copy(ignitionOff = true))
        assertFalse(waitingForFix.walking)
        val corrected = waitingForFix.recordParking(DemoArrivalWalks.parkedAtCurb, DemoArrivalWalks, confirmed = true)
        assertEquals("새봄로 지상 정차 구역", corrected.prepared?.startLabel)
        assertTrue(corrected.walking)
        val done = corrected.finishWalking()
        assertTrue(done.completed)
        assertFalse(done.receiveVehicle(parked.copy(ignitionOff = true)).walking)
    }

    @Test fun explicitEntranceAndWaypointStayIndependentOfParkingAndUnverifiedData() {
        val target = DemoArrivalWalks.target.copy(entrance = DemoArrivalWalks.main)
        val waypoint = VehicleWaypoint("다른 주차장", target.coordinate.offset(p(380, 0)))
        val state = ArrivalJourney(vehicle = parked.copy(ignitionOff = true)).receiveDestination(target).copy(waypoint = waypoint)
            .recordParking(DemoArrivalWalks.parkedAtCurb, DemoArrivalWalks)
        assertEquals(target, state.destination)
        assertEquals(waypoint, state.waypoint)
        assertEquals(DemoArrivalWalks.main.coordinate, state.prepared?.entranceCoordinate)
        assertNotEquals(waypoint.coordinate, state.parkedAt)
        val unknownEntrance = ArrivalJourney(vehicle = parked.copy(ignitionOff = true))
            .receiveDestination(target.copy(entrance = null))
            .recordParking(DemoArrivalWalks.parkedAtCurb, DemoArrivalWalks)
        assertNull(unknownEntrance.prepared)
        assertFalse(unknownEntrance.walking)
        assertNull(DemoArrivalWalks.fromParking(target.copy(demo = false), DemoArrivalWalks.parkedAtCurb))
    }
}
