package dev.arrival300

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

data class Point(val east: Double, val north: Double) {
    fun distance(other: Point) = hypot(east - other.east, north - other.north)
    operator fun minus(other: Point) = Point(east - other.east, north - other.north)
    operator fun plus(other: Point) = Point(east + other.east, north + other.north)
    operator fun times(ratio: Double) = Point(east * ratio, north * ratio)
}

fun p(east: Int, north: Int) = Point(east.toDouble(), north.toDouble())

data class Coordinate(val latitude: Double, val longitude: Double) {
    private val northScale get() = 111_195.0
    private val eastScale get() = northScale * cos(Math.toRadians(latitude))

    fun offset(point: Point) = Coordinate(latitude + point.north / northScale, longitude + point.east / eastScale)

    fun toLocal(other: Coordinate) =
        Point((other.longitude - longitude) * eastScale, (other.latitude - latitude) * northScale)
}

enum class RadiusBand(val label: String) {
    NEAR("500m 이내"), FAR("500m 초과 · 1km 이내");

    companion object {
        fun from(meters: Double) = when {
            meters in 0.0..500.0 -> NEAR
            meters > 500.0 && meters <= 1_000.0 -> FAR
            else -> null
        }
    }
}

data class Tariff(
    val firstMinutes: Int,
    val firstWon: Int,
    val extraMinutes: Int,
    val extraWon: Int,
    val dailyMax: Int? = null
) {
    init {
        require(firstMinutes > 0 && extraMinutes > 0)
        require(firstWon >= 0 && extraWon >= 0 && (dailyMax == null || dailyMax >= 0))
    }

    // Each started billing unit is charged. The demo treats a day as 24 hours from entry.
    fun total(minutes: Int): Long {
        require(minutes >= 0)
        if (minutes == 0) return 0
        fun charge(block: Int): Long {
            val extra = (block - firstMinutes).coerceAtLeast(0)
            val units = (extra.toLong() + extraMinutes - 1) / extraMinutes
            val total = firstWon + units * extraWon
            return dailyMax?.let { total.coerceAtMost(it.toLong()) } ?: total
        }
        if (dailyMax == null) return charge(minutes)
        val fullDays = minutes / 1440
        val remainder = minutes % 1440
        return fullDays * charge(1440) + if (remainder > 0) charge(remainder) else 0
    }
}

data class Route(val points: List<Point>, val minutes: Int) {
    init { require(points.size >= 2 && minutes > 0) }

    private val segments = points.zipWithNext { from, to -> from.distance(to) }
    val length = segments.sum()
    val meters = length.roundToInt()

    fun at(fraction: Double): Point {
        var remaining = length * fraction.coerceIn(0.0, 1.0)
        for ((index, segment) in segments.withIndex()) {
            if (segment > 0 && remaining < segment) {
                val from = points[index]
                return from + (points[index + 1] - from) * (remaining / segment)
            }
            remaining -= segment
        }
        return points.last()
    }
}

fun walking(vararg points: Point): Route {
    val meters = points.asList().zipWithNext { from, to -> from.distance(to) }.sum()
    return Route(points.toList(), ceil(meters / 75).toInt().coerceAtLeast(1))
}

const val RIGHT_CUE = "다음 골목 오른쪽에 주차장 입구가 있어요"
const val LEFT_CUE = "다음 골목 왼쪽에 주차장 입구가 있어요"

data class Approach(val inboundFrom: Point, val turnAt: Point, val entrance: Point) {
    private val inbound = turnAt - inboundFrom
    private val intoEntrance = entrance - turnAt
    val right = inbound.east * intoEntrance.north - inbound.north * intoEntrance.east < 0
    val cue = if (right) RIGHT_CUE else LEFT_CUE

    fun isApproaching(previous: Point, current: Point): Boolean {
        val motion = current - previous
        val untilTurn = turnAt - current
        val travel = motion.distance(p(0, 0))
        val inboundLength = inbound.distance(p(0, 0))
        val heading = motion.east * inbound.east + motion.north * inbound.north
        val lateralOffset = abs(untilTurn.east * inbound.north - untilTurn.north * inbound.east) / inboundLength
        return travel >= 3 && lateralOffset <= 32 &&
            current.distance(entrance) <= 105 &&
            current.distance(entrance) < previous.distance(entrance) &&
            heading >= .6 * travel * inboundLength &&
            untilTurn.east * inbound.east + untilTurn.north * inbound.north >= 0
    }
}

class EntranceAlert(private val approach: Approach) {
    var played = false
        private set

    fun update(previous: Point, current: Point): String? {
        if (played || !approach.isApproaching(previous, current)) return null
        played = true
        return approach.cue
    }
}

data class Lot(
    val id: String,
    val name: String,
    val approach: Approach,
    val walk: Route,
    val drive: Route,
    val afterDropOff: Route?,
    val tariff: Tariff?,
    val photoTile: Int?,
    val entryDetails: String,
    val walkSteps: List<String>,
    val hours: String? = null,
    val indoor: Boolean? = null,
    val maxHeight: String? = null
) {
    val entrance get() = approach.entrance
    val pedestrianExit get() = walk.points.first()

    init {
        val finalApproach = listOf(approach.inboundFrom, approach.turnAt, entrance)
        require(drive.points.takeLast(3) == finalApproach)
        require(afterDropOff == null || afterDropOff.points.takeLast(3) == finalApproach)
        require(pedestrianExit != entrance)
    }
}

data class Destination(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val origin: Coordinate,
    val visitorEntrance: Point,
    val start: Point,
    val lots: List<Lot>,
    val dropOff: DropOff? = null
) {
    init {
        require(dropOff == null || dropOff.drive.points.first() == start)
        lots.forEach { lot ->
            require(lot.drive.points.first() == start && lot.walk.points.last() == visitorEntrance)
            require(lot.entrance != visitorEntrance && lot.pedestrianExit != visitorEntrance)
            require(lot.afterDropOff == null || lot.afterDropOff.points.first() == dropOff?.point)
        }
    }
}

data class DropOff(val name: String, val drive: Route) {
    val point get() = drive.points.last()
}

data class Candidate(val lot: Lot, val radius: RadiusBand, val directMeters: Double, val estimatedWon: Long?)

enum class LotOrder(val label: String) {
    CHEAP("저렴한 순"), WALK("가까운 순"), TOTAL_TIME("이동시간 짧은 순")
}

interface ParkingRepository {
    val demoData: Boolean get() = false
    fun search(query: String): List<Destination>
    fun forNavigation(destination: NaviDestination): Destination? = null

    fun candidates(destination: Destination, minutes: Int, order: LotOrder): List<Candidate> {
        val candidates = destination.lots.mapNotNull { lot ->
            val meters = destination.visitorEntrance.distance(lot.entrance)
            RadiusBand.from(meters)?.let { Candidate(lot, it, meters, lot.tariff?.total(minutes)) }
        }
        val comparator = when (order) {
            LotOrder.CHEAP -> compareBy<Candidate> { it.estimatedWon ?: Long.MAX_VALUE }
            LotOrder.WALK -> compareBy { it.lot.walk.meters }
            LotOrder.TOTAL_TIME -> compareBy { it.lot.drive.minutes + it.lot.walk.minutes }
        }
        return candidates.sortedWith(comparator.thenBy { it.lot.id })
    }
}

enum class VehicleStatus { PARKED, DRIVING, UNKNOWN }
fun canCompare(status: VehicleStatus) = status == VehicleStatus.PARKED

fun aaosStatus(restricted: Boolean?, parkedGear: Boolean?) = when {
    restricted == true || parkedGear == false -> VehicleStatus.DRIVING
    restricted == false && parkedGear == true -> VehicleStatus.PARKED
    else -> VehicleStatus.UNKNOWN
}

enum class Stage { COMPARE, GUIDING, ARRIVED, WALKING }

data class NavigationTarget(
    val label: String,
    val point: Point,
    val coordinate: Coordinate,
    val route: Route
)

fun navigationTarget(destination: Destination, lot: Lot): NavigationTarget =
    NavigationTarget("${lot.name} 차량 진입구", lot.entrance, destination.origin.offset(lot.entrance), lot.drive)
