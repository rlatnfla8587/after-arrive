package dev.arrival300

import java.util.PriorityQueue

data class WalkingPath(val origin: Coordinate, val route: Route, val steps: List<String>)

internal fun ArrivalWalkProvider.pathBetween(from: Coordinate, to: Coordinate, demo: Boolean): WalkingPath? {
    if (!from.isValid() || !to.isValid()) return null
    return runCatching { between(from, to, demo) }.getOrNull()?.takeIf {
        it.origin.isValid() &&
            it.origin.offset(it.route.points.first()).distanceFrom(from) <= 2 &&
            it.origin.offset(it.route.points.last()).distanceFrom(to) <= 2
    }
}

internal object DemoWalkingPaths {
    private val origin get() = DemoArrivalWalks.hospital.origin
    private val sidePath = listOf(p(40, 54), p(40, 130), p(95, 130))
    val deviationPoint get() = origin.offset(sidePath.last())
    private val edges by lazy {
        val parkingSpots = listOf(DemoArrivalWalks.parkedInGarage, DemoArrivalWalks.parkedAtCurb)
        val entrances = listOf(DemoArrivalWalks.east, DemoArrivalWalks.main)
        val paths = entrances.flatMap { entrance ->
            parkingSpots.map { point ->
                requireNotNull(DemoArrivalWalks.fromParking(DemoArrivalWalks.target.copy(entrance = entrance), point)).route.points
            }
        }
        (paths + listOf(sidePath)).flatMap { points -> points.zipWithNext() }.distinct()
    }

    fun between(from: Coordinate, to: Coordinate): WalkingPath? {
        val start = origin.toLocal(from)
        val end = origin.toLocal(to)
        val entry = snap(start) ?: return null
        val exit = snap(end) ?: return null
        val links = mutableMapOf<Point, MutableList<Point>>()
        fun link(a: Point, b: Point) {
            if (a == b) return
            links.getOrPut(a) { mutableListOf() }.add(b)
            links.getOrPut(b) { mutableListOf() }.add(a)
        }
        edges.forEach { (a, b) -> link(a, b) }
        for (snap in listOf(entry, exit)) {
            link(snap.edge.first, snap.point)
            link(snap.edge.second, snap.point)
        }
        if (entry.edge == exit.edge) link(entry.point, exit.point)
        val network = shortestPath(links, entry.point, exit.point) ?: return null
        val points = (listOf(start) + network + end).fold(mutableListOf<Point>()) { clean, point ->
            if (clean.lastOrNull()?.distance(point)?.let { it < .01 } != true) clean.add(point)
            clean
        }
        val route = if (points.size > 1) walking(*points.toTypedArray()) else walking(start, end)
        return WalkingPath(origin, route, listOf("지도에 표시된 보행 경로를 따라 이동하세요."))
    }

    private data class Snap(val edge: Pair<Point, Point>, val point: Point)

    private fun snap(point: Point): Snap? = edges.asSequence().map { edge ->
        val (a, b) = edge
        Snap(edge, a + (b - a) * segmentFraction(point, a, b))
    }.minByOrNull { point.distance(it.point) }?.takeIf { point.distance(it.point) <= 12 }

    private fun shortestPath(links: Map<Point, List<Point>>, start: Point, end: Point): List<Point>? {
        val queue = PriorityQueue(compareBy<Pair<Point, Double>> { it.second })
        val distance = mutableMapOf(start to 0.0)
        val previous = mutableMapOf<Point, Point>()
        queue.add(start to 0.0)
        while (queue.isNotEmpty()) {
            val (point, cost) = queue.remove()
            if (point == end) return generateSequence(end) { previous[it] }.toList().asReversed()
            if (cost > distance.getValue(point)) continue
            for (neighbor in links[point].orEmpty()) {
                val next = cost + point.distance(neighbor)
                if (next < (distance[neighbor] ?: Double.POSITIVE_INFINITY)) {
                    previous[neighbor] = point
                    distance[neighbor] = next
                    queue.add(neighbor to next)
                }
            }
        }
        return null
    }
}

private fun segmentFraction(point: Point, a: Point, b: Point): Double {
    val edge = b - a
    val lengthSquared = edge.east * edge.east + edge.north * edge.north
    if (lengthSquared == 0.0) return 0.0
    val offset = point - a
    return ((offset.east * edge.east + offset.north * edge.north) / lengthSquared).coerceIn(0.0, 1.0)
}

internal class WalkingDeviation {
    private var furthest = 0.0
    private var first = 0L
    private var last = 0L
    private var samples = 0
    private var lastAttempt: Long? = null
    val hasDeviation get() = samples > 0

    fun clearEvidence() {
        first = 0
        last = 0
        samples = 0
    }

    fun update(route: Route, point: Point, accuracy: Float, timestamp: Long): Boolean {
        if (accuracy !in 0f..15f || timestamp <= 0) {
            clearEvidence()
            return false
        }
        if (last != 0L && timestamp <= last) return false
        if (timestamp - last > 6_000_000_000L) clearEvidence()
        last = timestamp

        var closest = Double.POSITIVE_INFINITY
        var along = 0.0
        var progress = 0.0
        route.points.zipWithNext().forEach { (a, b) ->
            val length = a.distance(b)
            val fraction = segmentFraction(point, a, b)
            val offset = point.distance(a + (b - a) * fraction)
            if (offset < closest) {
                closest = offset
                progress = along + length * fraction
            }
            along += length
        }
        val offRoute = closest - accuracy > 20
        val backwards = !offRoute && furthest - progress > 25 + accuracy * 2
        if (!offRoute && !backwards) {
            furthest = maxOf(furthest, progress)
            first = 0
            samples = 0
            return false
        }
        if (samples == 0) first = timestamp
        samples++
        if (samples < 3 || timestamp - first < 4_000_000_000L ||
            lastAttempt?.let { timestamp - it < 20_000_000_000L } == true) return false
        lastAttempt = timestamp
        return true
    }

    fun newPath(keepCooldown: Boolean = true) {
        furthest = 0.0
        if (!keepCooldown) lastAttempt = null
        clearEvidence()
    }
}
