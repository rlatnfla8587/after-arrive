package dev.arrival300

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class WalkingMapTest {
    @Test fun portraitAndLandscapeKeepTheWholeWalkInsideTheMapWithMoreContext() {
        val route = requireNotNull(DemoArrivalWalks.fromParking(DemoArrivalWalks.target, DemoArrivalWalks.parkedInGarage)).route
        val inset = Offset(26f, 32f)
        for (viewport in listOf(Size(362f, 228f), Size(480f, 192f))) {
            val projection = fitWalkingRoute(route.points, viewport, inset)
            val oldWidth = route.points.maxOf { it.east } - route.points.minOf { it.east } + 100
            val oldHeight = route.points.maxOf { it.north } - route.points.minOf { it.north } + 110
            val oldScale = min(viewport.width / oldWidth, viewport.height / oldHeight)
            assertTrue(projection.scale < oldScale)
            route.points.forEach { point ->
                val pixel = projection.at(point)
                assertTrue(pixel.x in inset.x..(viewport.width - inset.x))
                assertTrue(pixel.y in inset.y..(viewport.height - inset.y))
            }
        }
    }

    @Test fun aSingleAxisRouteStillFitsAndLongerBoundsZoomOut() {
        val viewport = Size(360f, 228f)
        val inset = Offset(26f, 32f)
        val short = fitWalkingRoute(listOf(p(0, 0), p(20, 0)), viewport, inset)
        val long = fitWalkingRoute(listOf(p(0, 0), p(900, 0)), viewport, inset)
        assertTrue(long.scale in 0f..<short.scale)
        assertTrue(long.at(p(900, 0)).x <= viewport.width - inset.x)
    }

    @Test fun headingUsesTheScreenAxesAndCrossesNorthWithoutSpinning() {
        val flatNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val flatEast = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        val uprightNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        assertEquals(350f, mapHeading(flatNorth, -10f), .01f)
        assertEquals(90f, mapHeading(flatEast, 0f), .01f)
        assertEquals(0f, mapHeading(uprightNorth, 0f), .01f)
        assertEquals(355f, smoothHeading(350f, 10f), .01f)
        assertEquals(5f, smoothHeading(10f, 350f), .01f)
    }
}
