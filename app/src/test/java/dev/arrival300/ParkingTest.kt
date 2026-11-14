package dev.arrival300

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingTest {
    private val hospital = DemoParking.search("한빛대학교병원").single()

    @Test fun radiusBoundariesAndOutsideCandidate() {
        assertEquals(RadiusBand.NEAR, RadiusBand.from(500.0))
        assertEquals(RadiusBand.FAR, RadiusBand.from(500.0001))
        assertEquals(RadiusBand.FAR, RadiusBand.from(1_000.0))
        assertNull(RadiusBand.from(1_000.0001))

        val candidates = DemoParking.candidates(hospital, 120, LotOrder.WALK)
        assertEquals(mapOf("A" to RadiusBand.NEAR, "B" to RadiusBand.NEAR,
            "C" to RadiusBand.FAR, "D" to RadiusBand.FAR), candidates.associate { it.lot.id to it.radius })
        assertTrue(candidates.all { it.directMeters < it.lot.walk.meters })
    }

    @Test fun startedUnitsAreRoundedUpAndCappedPerDay() {
        val tariff = Tariff(30, 1_000, 10, 500, 5_000)
        assertEquals(0L, tariff.total(0))
        assertEquals(1_000L, tariff.total(30))
        assertEquals(1_500L, tariff.total(31))
        assertEquals(1_500L, tariff.total(40))
        assertEquals(2_000L, tariff.total(41))
        assertEquals(5_000L, tariff.total(180))
        assertEquals(5_000L, tariff.total(1440))
        assertEquals(6_000L, tariff.total(1441))
    }

    @Test fun searchAcceptsNameOrAddressAndQuotesChangeWithDuration() {
        assertEquals(hospital, DemoParking.search("새봄로 120").single())
        assertEquals("mall", DemoParking.search("마루길").single().id)
        assertTrue(DemoParking.search("미등록").isEmpty())
        val short = DemoParking.candidates(hospital, 30, LotOrder.CHEAP)
        val long = DemoParking.candidates(hospital, 120, LotOrder.CHEAP)
        assertEquals(listOf("B", "C", "A", "D"), short.map { it.lot.id })
        assertEquals(listOf("C", "B", "A", "D"), long.map { it.lot.id })
        assertEquals(2_400L, long.first().lot.tariff?.total(120))
        assertNull(long.last().lot.tariff)
        assertEquals("A", DemoParking.candidates(hospital, 120, LotOrder.WALK).first().lot.id)
        val times = DemoParking.candidates(hospital, 120, LotOrder.TOTAL_TIME).map { it.lot.drive.minutes + it.lot.walk.minutes }
        assertEquals(times.sorted(), times)
    }

    @Test fun unknownAndDrivingStatesCloseTheGate() {
        assertTrue(canCompare(VehicleStatus.PARKED))
        assertFalse(canCompare(VehicleStatus.DRIVING))
        assertFalse(canCompare(VehicleStatus.UNKNOWN))
        assertEquals(VehicleStatus.PARKED, aaosStatus(restricted = false, parkedGear = true))
        assertEquals(VehicleStatus.DRIVING, aaosStatus(restricted = true, parkedGear = true))
        assertEquals(VehicleStatus.DRIVING, aaosStatus(restricted = false, parkedGear = false))
        assertEquals(VehicleStatus.UNKNOWN, aaosStatus(restricted = false, parkedGear = null))
        assertEquals(VehicleStatus.UNKNOWN, aaosStatus(restricted = null, parkedGear = true))
    }

    @Test fun selectedEntranceUsesDistinctCoordinates() {
        val lot = hospital.lots.single { it.id == "B" }
        val target = navigationTarget(hospital, lot)
        assertEquals(lot.drive, target.route)
        assertEquals(lot.entrance, target.route.points.last())
        assertEquals(hospital.origin.offset(lot.entrance), target.coordinate)
        assertNotEquals(target.coordinate, hospital.origin.offset(hospital.visitorEntrance))
        assertNotEquals(target.coordinate, hospital.origin.offset(lot.pedestrianExit))
    }

    @Test fun everyDemoApproachSpeaksOnceInTheCorrectDirection() {
        DemoParking.search("").forEach { destination ->
            val candidates = DemoParking.candidates(destination, 120, LotOrder.WALK)
            candidates.forEach { candidate ->
                val lot = candidate.lot
                val expected = if (lot.id in listOf("A", "C", "E")) "오른쪽" else "왼쪽"
                listOfNotNull(lot.drive, lot.afterDropOff).forEach { route ->
                    val alert = EntranceAlert(lot.approach)
                    val points = (0..72).map { route.at(it / 72.0) }
                    val cues = points.zipWithNext { from, to -> alert.update(from, to) }.filterNotNull()
                    assertEquals("lot ${lot.id}", 1, cues.size)
                    assertTrue(cues.single().contains(expected))
                    assertTrue(alert.played)
                }
                val approach = lot.approach
                val lastStreet = approach.inboundFrom + (approach.turnAt - approach.inboundFrom) * .93
                assertNull(EntranceAlert(approach).update(approach.turnAt, lastStreet))
            }
        }
    }
}
