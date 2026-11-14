package dev.arrival300

data class ConfirmedEntrance(val id: String, val name: String, val coordinate: Coordinate)

data class NaviDestination(
    val name: String,
    val address: String,
    val coordinate: Coordinate,
    val demo: Boolean = false,
    val entrance: ConfirmedEntrance? = null
)

data class NaviArrival(val point: Coordinate?)

data class ArrivalVehicle(
    val status: VehicleStatus = VehicleStatus.UNKNOWN,
    val stationary: Boolean? = null,
    val ignitionOff: Boolean? = null,
    val driverSeated: Boolean? = null
) {
    val safelyParked get() = status == VehicleStatus.PARKED && stationary == true
}

data class VehicleWaypoint(val name: String, val coordinate: Coordinate)

data class ArrivalWalk(
    val destinationName: String,
    val entranceName: String,
    val origin: Coordinate,
    val route: Route,
    val startLabel: String,
    val pedestrianExit: Point?,
    val steps: List<String>,
    val demo: Boolean
) {
    val entranceCoordinate get() = origin.offset(route.points.last())
}

interface ArrivalWalkProvider {
    fun fromParking(destination: NaviDestination, parkedAt: Coordinate): ArrivalWalk?
    fun between(from: Coordinate, to: Coordinate, demo: Boolean): WalkingPath? = null
}

internal fun Coordinate.isValid() = latitude in -90.0..90.0 && longitude in -180.0..180.0
internal fun Coordinate.distanceFrom(other: Coordinate) = toLocal(other).distance(p(0, 0))
internal fun NaviDestination.acceptsParking(point: Coordinate) =
    point.isValid() && coordinate.distanceFrom(point) <= 1_200.0

internal fun ArrivalWalkProvider.verifiedRoute(destination: NaviDestination, parkedAt: Coordinate): ArrivalWalk? {
    val entrance = destination.entrance ?: return null
    return fromParking(destination, parkedAt)?.takeIf {
        it.demo == destination.demo && it.entranceName == entrance.name &&
            it.origin.offset(it.route.points.first()).distanceFrom(parkedAt) <= 2.0 &&
            it.entranceCoordinate.distanceFrom(entrance.coordinate) <= 2.0
    }
}

data class ArrivalJourney(
    val destination: NaviDestination? = null,
    val arrival: NaviArrival? = null,
    val vehicle: ArrivalVehicle = ArrivalVehicle(),
    val waypoint: VehicleWaypoint? = null,
    val parkedAt: Coordinate? = null,
    val prepared: ArrivalWalk? = null,
    val walking: Boolean = false,
    val completed: Boolean = false
) {
    val canStartManually get() =
        vehicle.safelyParked && vehicle.ignitionOff == null && prepared != null && !walking && !completed

    fun receiveDestination(value: NaviDestination): ArrivalJourney =
        if (destination?.coordinate == value.coordinate && destination.demo == value.demo &&
            (destination.name == value.name || destination.address.isNotBlank() && destination.address == value.address)) {
            if (value.entrance == null || destination.entrance == value.entrance) {
                copy(destination = value.copy(entrance = value.entrance ?: destination.entrance))
            } else {
                copy(destination = value, prepared = null, parkedAt = null, walking = false, completed = false)
            }
        } else {
            ArrivalJourney(destination = value, vehicle = vehicle)
        }

    fun arrive(value: NaviArrival): ArrivalJourney =
        if (destination == null || arrival != null) this else copy(arrival = value)

    fun receiveVehicle(value: ArrivalVehicle): ArrivalJourney {
        if (completed) return copy(vehicle = value)
        if (!value.safelyParked) {
            return copy(vehicle = value, parkedAt = null, prepared = null, walking = false)
        }
        return copy(vehicle = value).openWhenOff()
    }

    fun recordParking(point: Coordinate, provider: ArrivalWalkProvider, confirmed: Boolean = false): ArrivalJourney {
        val target = destination ?: return this
        if (!vehicle.safelyParked || completed || walking || parkedAt != null && !confirmed || !target.acceptsParking(point)) {
            return this
        }
        return copy(parkedAt = point, prepared = provider.verifiedRoute(target, point)).openWhenOff()
    }

    fun startManually(): ArrivalJourney = if (canStartManually) copy(walking = true) else this

    fun finishWalking(): ArrivalJourney = if (walking) copy(walking = false, completed = true) else this

    private fun openWhenOff(): ArrivalJourney =
        if (vehicle.safelyParked && vehicle.ignitionOff == true && prepared != null && !completed) {
            copy(walking = true)
        } else this
}

internal enum class DemoParkingFix { GARAGE, CURB, UNKNOWN }

/** Authored routes for the two registered parking spots and confirmed entrances in the demo map. */
object DemoArrivalWalks : ArrivalWalkProvider {
    val hospital = DemoParking.search("한빛").single()
    val east = ConfirmedEntrance("hospital-east", "본관 동문", hospital.origin.offset(hospital.visitorEntrance))
    private val mainDoor = p(-34, -15)
    val main = ConfirmedEntrance("hospital-main", "본관 정문", hospital.origin.offset(mainDoor))
    val target = NaviDestination(hospital.name, hospital.address, hospital.origin.offset(p(180, 110)), demo = true, entrance = east)
    val navigationStop = hospital.origin.offset(p(160, 100))
    private val garageSpot = p(118, 72)
    private val streetSpot = p(76, -12)
    val parkedInGarage = hospital.origin.offset(garageSpot)
    val parkedAtCurb = hospital.origin.offset(streetSpot)

    override fun between(from: Coordinate, to: Coordinate, demo: Boolean): WalkingPath? =
        if (demo) DemoWalkingPaths.between(from, to) else null

    internal fun location(fix: DemoParkingFix) = when (fix) {
        DemoParkingFix.GARAGE -> parkedInGarage
        DemoParkingFix.CURB -> parkedAtCurb
        DemoParkingFix.UNKNOWN -> null
    }

    override fun fromParking(destination: NaviDestination, parkedAt: Coordinate): ArrivalWalk? {
        if (!destination.demo || destination.coordinate != target.coordinate) return null
        val entrance = destination.entrance?.takeIf { it == east || it == main } ?: return null
        val garage = hospital.lots.first { it.id == "A" }
        val (path, startLabel, exit, steps) = when (parkedAt) {
            parkedInGarage -> WalkLeg(
                listOf(garageSpot, p(108, 72)) + garage.walk.points,
                "본관 지하주차장 · A구역", garage.pedestrianExit,
                listOf("A구역 보행 통로를 따라 엘리베이터로 이동하세요.") + garage.walkSteps
            )
            parkedAtCurb -> WalkLeg(
                listOf(streetSpot, p(76, 20), p(42, 20), p(42, 0), hospital.visitorEntrance),
                "새봄로 지상 정차 구역", null,
                listOf("인도로 올라와 횡단보도를 건너세요.", "본관 동문 표지를 따라 오른쪽 자동문으로 들어가세요.")
            )
            else -> return null
        }
        val route = if (entrance == main) walking(*(path + listOf(p(0, -15), mainDoor)).toTypedArray()) else walking(*path.toTypedArray())
        val instructions = if (entrance == main) {
            steps.dropLast(1) + "본관 동문 앞에서 왼쪽 보도를 따라 정문 자동문으로 들어가세요."
        } else steps
        return ArrivalWalk(hospital.name, entrance.name, hospital.origin, route, startLabel, exit, instructions, demo = true)
    }

    private data class WalkLeg(val path: List<Point>, val label: String, val exit: Point?, val steps: List<String>)
}
