package dev.arrival300

import android.graphics.ComposeShader
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val MapBackground = Color(0xFFF0F4F6)
private val MapBlock = Color(0xFFDBE5EB)
private val MapStreet = Color(0xFFFBFDFE)
private val MapPark = Color(0xFFD8EBE0)
private val RadiusInk = Color(0xFF647982)
private val MapFrame = DashboardCard
private val PinShape = RoundedCornerShape(10.dp)

internal class Projection(
    val center: Offset,
    val scale: Float,
    val anchor: Point = p(0, 0)
) {
    fun at(point: Point) = Offset(
        center.x + ((point.east - anchor.east) * scale).toFloat(),
        center.y - ((point.north - anchor.north) * scale).toFloat()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParkingMap(trip: TripViewModel, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    if (trip.stage == Stage.WALKING) {
        ParkingWalkMap(trip, modifier)
        return
    }
    val comparing = trip.stage == Stage.COMPARE
    val guiding = trip.stage == Stage.GUIDING
    val target = trip.target
    val lot = trip.selected
    val candidates = if (comparing) trip.candidates else emptyList()
    val vehicleRoute = target?.route ?: lot.drive
    val walkingRoute = lot.walk.takeIf { comparing }

    BoxWithConstraints(
        modifier.clip(MapFrame).background(MapBackground).border(1.dp, Border, MapFrame)
    ) {
        val projection = Projection(
            Offset(constraints.maxWidth * .5f, constraints.maxHeight * .5f),
            min(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()) / 2300f
        )
        Canvas(Modifier.fillMaxSize()) {
            streetMap(projection, trip.destination.id == "hospital")
            val center = projection.at(trip.destination.visitorEntrance)
            if (comparing) {
                drawCircle(RadiusInk.copy(alpha = .08f), 500 * projection.scale, center)
                drawCircle(RadiusInk.copy(alpha = .60f), 1_000 * projection.scale, center, style = Stroke(
                    width = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                ))
                drawCircle(RadiusInk.copy(alpha = .85f), 500 * projection.scale, center, style = Stroke(width = 1.6.dp.toPx()))
            }
            drawRoute(vehicleRoute, projection, Blue, false)
            walkingRoute?.let { drawRoute(it, projection, Gold, true) }
            walkingRoute?.let {
                drawCircle(Ink, 7.dp.toPx(), projection.at(lot.pedestrianExit))
                drawCircle(Gold, 4.5.dp.toPx(), projection.at(lot.pedestrianExit))
            }
            val dot = projection.at(trip.position)
            drawCircle(Ink, 9.dp.toPx(), dot)
            drawCircle(Blue, 6.5.dp.toPx(), dot)
            drawCircle(White, 2.4.dp.toPx(), dot)
            if (!guiding) {
                drawCircle(Ink, 8.dp.toPx(), center)
                drawCircle(White, 5.dp.toPx(), center)
            }
            if (target != null && trip.demoMode) {
                drawCircle(Ink, 10.dp.toPx(), projection.at(target.point))
                drawCircle(Indigo, 6.dp.toPx(), projection.at(target.point))
            }
        }

        if (comparing) {
            MapTag("현재 위치", Modifier.pinAt(projection.at(trip.position), vertical = -.3f), Blue)
            MapTag("500m", Modifier.pinAt(projection.at(p(0, 500)), vertical = .7f), RadiusInk)
            MapTag("1km", Modifier.pinAt(projection.at(p(0, 1000)), vertical = .55f), RadiusInk)
            Canvas(Modifier.fillMaxSize()) {
                val stem = Offset(0f, 8.dp.toPx())
                candidates.forEach { candidate ->
                    val selected = candidate.lot.id == trip.selectedId
                    val position = projection.at(candidate.lot.entrance)
                    drawLine(if (selected) Blue else Border, position, position - stem, 2.dp.toPx())
                    drawCircle(if (selected) Blue else White, 3.5.dp.toPx(), position)
                }
            }
            candidates.forEach { candidate ->
                val selected = candidate.lot.id == trip.selectedId
                val lotPrice = candidate.estimatedWon?.let(::won)
                val position = projection.at(candidate.lot.entrance)
                PricePin(
                    candidate.lot.id, lotPrice, selected,
                    Modifier.pinAt(position).zIndex(if (selected) 2f else 1f),
                    onClick = { onSelect(candidate.lot.id) }
                )
            }
            MapTag("방문 출입구", Modifier.pinAt(projection.at(trip.destination.visitorEntrance), vertical = -.30f), Ink)
        }

        Text(
            "주변 안내도",
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(White.copy(alpha = .94f), PinShape).padding(horizontal = 9.dp, vertical = 6.dp),
            color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Text("↑ 북", Modifier.align(Alignment.TopEnd).padding(16.dp), color = Ink, fontSize = 12.sp)

        if (!guiding) {
            FlowRow(
                Modifier.align(Alignment.BottomStart).padding(10.dp).zIndex(3f)
                    .background(White.copy(alpha = .96f), PinShape).padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Legend("차량", Blue)
                if (walkingRoute != null) Legend("도보", Gold, dashed = true)
                if (comparing) {
                    Legend("500m", RadiusInk)
                    Legend("1km", RadiusInk, dashed = true)
                }
            }
        }
    }
}

private fun Modifier.pinAt(position: Offset, vertical: Float = 1f) =
    offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
        .graphicsLayer {
            translationX = -size.width * .5f
            translationY = -size.height * vertical - if (vertical == 1f) 5.dp.toPx() else 0f
        }

@Composable
private fun PricePin(id: String, price: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val description = "$id 주차장 · ${price ?: "요금 정보 없음"}"
    Surface(
        modifier.clip(PinShape).selectable(selected, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = PinShape,
        color = if (selected) Raised else White,
        contentColor = if (selected) Blue else Ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Blue else Border)
    ) {
        Column(
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(id, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
            Text(price ?: "정보 없음", fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MapTag(text: String, modifier: Modifier, color: Color) {
    Text(text, modifier.background(White.copy(alpha = .94f), CircleShape).padding(horizontal = 6.dp, vertical = 3.dp),
        color = color, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Legend(text: String, color: Color, dashed: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 13.dp, height = 7.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null)
        }
        Text(text, color = Muted, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

private fun DrawScope.drawRoute(route: Route, projection: Projection, color: Color, dashed: Boolean) {
    val path = Path()
    route.points.forEachIndexed { index, point ->
        val position = projection.at(point)
        if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
    }
    drawPath(path, White.copy(alpha = .98f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(
        width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())) else null
    ))
}

private val mapXs = listOf(-1_700, -1_245, -900, -570, -300, 160, 430, 730, 900, 1_230, 1_700)
private val mapYs = listOf(-1_600, -910, -555, -245, 100, 335, 680, 1_035, 1_600)
private val mapColumns = mapXs.zipWithNext()
private val mapRows = mapYs.zipWithNext()

private fun DrawScope.streetMap(projection: Projection, hospital: Boolean, walkingDetail: Boolean = false) {
    val spacing = 15
    val buildingMargin = 7.dp.toPx()
    mapColumns.forEachIndexed { x, (west, east) ->
        mapRows.forEachIndexed row@{ y, (south, north) ->
            val top = projection.at(p(west + spacing, north - spacing))
            val bottom = projection.at(p(east - spacing, south + spacing))
            if (bottom.x < -buildingMargin || bottom.y < -buildingMargin ||
                top.x > size.width + buildingMargin || top.y > size.height + buildingMargin
            ) return@row
            val park = (x + 2 * y) % 8 == 0
            drawRoundRect(if (park) MapPark else MapBlock, top, Size(bottom.x - top.x, bottom.y - top.y), CornerRadius(4.dp.toPx()))
            if (!park && (x + y) % 2 == 0) {
                val inset = if (walkingDetail) min(bottom.x - top.x, bottom.y - top.y) * .08f else 6.dp.toPx()
                val width = (bottom.x - top.x) * .48f
                val height = (bottom.y - top.y) * .42f
                val building = top + Offset(inset, inset)
                val footprint = Size(width, height)
                val corner = CornerRadius(2.dp.toPx())
                drawRoundRect(Color(0xFFC9D8E2), building, footprint, corner)
                if (walkingDetail) drawRoundRect(RadiusInk.copy(alpha = .24f), building, footprint, corner, style = Stroke(.7.dp.toPx()))
            }
        }
    }
    val roadWidth = (18 * projection.scale).coerceAtLeast(1.dp.toPx())
    if (walkingDetail) drawStreets(projection, RadiusInk.copy(alpha = .27f), roadWidth + 1.6.dp.toPx())
    drawStreets(projection, MapStreet, roadWidth)
    val top = projection.at(p(-95, 75))
    val bottom = projection.at(p(115, -55))
    val footprint = Size(bottom.x - top.x, bottom.y - top.y)
    val corner = CornerRadius(3.dp.toPx())
    drawRoundRect(if (hospital) Color(0xFFC3DCE6) else Color(0xFFD0D9EF), top, footprint, corner)
    if (walkingDetail) drawRoundRect(RadiusInk.copy(alpha = .38f), top, footprint, corner, style = Stroke(.8.dp.toPx()))
}

private fun DrawScope.drawStreets(projection: Projection, color: Color, width: Float) {
    mapXs.forEach { x -> drawLine(color, projection.at(p(x, -1700)), projection.at(p(x, 1700)), width) }
    mapYs.forEach { y -> drawLine(color, projection.at(p(-1750, y)), projection.at(p(1750, y)), width) }
}

internal fun fitWalkingRoute(points: List<Point>, viewport: Size, inset: Offset): Projection {
    val minEast = points.minOf { it.east }
    val maxEast = points.maxOf { it.east }
    val minNorth = points.minOf { it.north }
    val maxNorth = points.maxOf { it.north }
    val width = maxEast - minEast
    val height = maxNorth - minNorth
    val context = max(130.0, max(width, height) * .35)
    val scale = min(
        (viewport.width - inset.x * 2).coerceAtLeast(1f) / (width + context * 2),
        (viewport.height - inset.y * 2).coerceAtLeast(1f) / (height + context * 2)
    ).toFloat()
    return Projection(Offset(viewport.width / 2, viewport.height / 2), scale, Point((minEast + maxEast) / 2, (minNorth + maxNorth) / 2))
}

@Composable
internal fun ArrivalWalkMap(
    plan: ArrivalWalk,
    walking: Boolean,
    phone: Boolean,
    location: Coordinate?,
    modifier: Modifier = Modifier,
    returning: Boolean = false,
    parkedStart: Coordinate? = null
) {
    val points = plan.route.points
    val start = parkedStart?.let(plan.origin::toLocal) ?: points.first()
    val startLabel = if (returning) "현재 위치" else "주차 위치"
    WalkingMap(
        plan.route, plan.origin, plan.demo, hospital = true, showPosition = walking && phone, location = location,
        modifier = modifier.semantics {
            contentDescription = if (returning) "저장된 주차 위치까지의 도보 지도" else "실제 주차 위치에서 ${plan.entranceName}까지의 도보 지도"
        }
    ) { projection ->
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Ink, 9.dp.toPx(), projection.at(start))
            drawCircle(Blue, 5.5.dp.toPx(), projection.at(start))
            drawCircle(Ink, 9.dp.toPx(), projection.at(points.last()))
            drawCircle(Gold, 5.5.dp.toPx(), projection.at(points.last()))
            plan.pedestrianExit?.let { drawCircle(Ink, 3.dp.toPx(), projection.at(it)) }
        }
        MapTag(startLabel, Modifier.pinAt(projection.at(start)).zIndex(1f), Blue)
        MapTag(plan.entranceName, Modifier.pinAt(projection.at(points.last()), vertical = -.4f).zIndex(1f), Gold)
        if (plan.pedestrianExit != null) {
            Text("● 보행 출구", Modifier.align(Alignment.BottomEnd).padding(12.dp).zIndex(1f), color = Ink, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun ParkingPositionMap(
    saved: SavedParking,
    selected: Coordinate,
    route: ArrivalWalk?,
    modifier: Modifier = Modifier,
    onPick: ((Coordinate) -> Unit)? = null
) {
    val nearbyRoute = route?.takeIf {
        it.demo == saved.demo && it.origin.distanceFrom(saved.coordinate) <= 500
    }
    val origin = nearbyRoute?.origin ?: if (saved.demo) DemoArrivalWalks.hospital.origin else saved.coordinate
    val start = origin.toLocal(saved.coordinate)
    BoxWithConstraints(
        modifier.clip(MapFrame).background(MapBackground).border(1.dp, Border, MapFrame)
            .semantics { contentDescription = if (onPick == null) "저장된 주차 위치 지도" else "탭하여 주차 위치 수정" }
    ) {
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val inset = with(LocalDensity.current) { Offset(18.dp.toPx(), 18.dp.toPx()) }
        val projection = fitWalkingRoute(listOf(start) + nearbyRoute?.route?.points.orEmpty(), size, inset)
        val pin = projection.at(origin.toLocal(selected))
        val input = if (onPick == null) Modifier else Modifier.pointerInput(saved, size, nearbyRoute) {
            detectTapGestures { pixel ->
                val east = (pixel.x - projection.center.x) / projection.scale
                val north = (projection.center.y - pixel.y) / projection.scale
                onPick(origin.offset(projection.anchor + Point(east.toDouble(), north.toDouble())))
            }
        }
        Canvas(Modifier.fillMaxSize().then(input)) {
            if (saved.demo) streetMap(projection, hospital = true, walkingDetail = true)
            nearbyRoute?.let { drawRoute(it.route, projection, Gold, true) }
            drawCircle(Ink, 9.dp.toPx(), pin)
            drawCircle(Blue, 5.5.dp.toPx(), pin)
        }
        MapTag("주차 위치", Modifier.pinAt(pin), Blue)
        Text("↑ 북", Modifier.align(Alignment.TopEnd).padding(10.dp), color = Ink, fontSize = 12.sp)
    }
}

@Composable
private fun ParkingWalkMap(trip: TripViewModel, modifier: Modifier) {
    val lot = trip.selected
    val entrance = trip.destination.visitorEntrance
    WalkingMap(
        lot.walk, trip.destination.origin, demo = true, hospital = trip.destination.id == "hospital",
        showPosition = !trip.automotive, location = null, modifier = modifier
    ) { projection ->
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Ink, 7.dp.toPx(), projection.at(lot.pedestrianExit))
            drawCircle(Gold, 4.5.dp.toPx(), projection.at(lot.pedestrianExit))
            drawCircle(Ink, 8.dp.toPx(), projection.at(entrance))
            drawCircle(White, 5.dp.toPx(), projection.at(entrance))
        }
        MapTag("보행 출구", Modifier.pinAt(projection.at(lot.pedestrianExit)).zIndex(1f), Gold)
        MapTag("방문 출입구", Modifier.pinAt(projection.at(entrance), vertical = -.30f).zIndex(1f), Ink)
        Text("건물까지 걷는 길", Modifier.align(Alignment.TopStart).padding(12.dp).zIndex(1f)
            .background(White.copy(alpha = .94f), PinShape).padding(horizontal = 9.dp, vertical = 6.dp),
            color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("↑ 북", Modifier.align(Alignment.TopEnd).padding(16.dp).zIndex(1f), color = Ink, fontSize = 12.sp)
        Row(Modifier.align(Alignment.BottomStart).padding(10.dp).zIndex(1f)
            .background(White.copy(alpha = .96f), PinShape).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Legend("도보", Gold, dashed = true)
        }
    }
}

private val LivePositionBlue = Color(0xFF1A73E8)
private const val DirectionFanSweep = 30f

private fun directionFanBrush(radius: Float): ShaderBrush {
    val radial = RadialGradient(
        0f, 0f, radius,
        intArrayOf(
            LivePositionBlue.copy(alpha = .35f).toArgb(),
            LivePositionBlue.copy(alpha = .15f).toArgb(),
            LivePositionBlue.copy(alpha = 0f).toArgb()
        ),
        floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
    )
    val sweepFraction = DirectionFanSweep / 360f
    // Multiply the radial opacity by a mask that fades toward the two sides.
    val clear = Color.Transparent.toArgb()
    val opaque = Color.White.toArgb()
    val angular = SweepGradient(
        0f, 0f,
        intArrayOf(clear, opaque, opaque, clear, clear),
        floatArrayOf(0f, sweepFraction * .27f, sweepFraction * .73f, sweepFraction, 1f)
    )
    return ShaderBrush(ComposeShader(radial, angular, PorterDuff.Mode.DST_IN))
}

@Composable
private fun WalkingMap(
    route: Route,
    origin: Coordinate,
    demo: Boolean,
    hospital: Boolean,
    showPosition: Boolean,
    location: Coordinate?,
    modifier: Modifier,
    markers: @Composable BoxScope.(Projection) -> Unit
) {
    val heading = rememberWalkingHeading(showPosition, origin)
    val fanRadius = with(LocalDensity.current) { 42.dp.toPx() }
    val fanBrush = remember(fanRadius) { directionFanBrush(fanRadius) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Demo coordinates describe a fictional site. Animate only the displayed demo position.
    val demoProgress = produceState(0.0, route, origin, showPosition, demo, lifecycle) {
        value = 0.0
        if (!showPosition || !demo) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (value < 1.0) {
                delay(250)
                value = (value + 250.0 / (route.minutes * 60_000.0)).coerceAtMost(1.0)
            }
        }
    }
    val userPoint: () -> Point? = {
        when {
            !showPosition -> null
            location != null -> origin.toLocal(location)
            demo -> route.at(demoProgress.value)
            else -> null
        }
    }
    BoxWithConstraints(modifier.clip(MapFrame).background(MapBackground).border(1.dp, Border, MapFrame)) {
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val inset = with(LocalDensity.current) { Offset(26.dp.toPx(), 32.dp.toPx()) }
        val fitted = remember(route, viewport, inset) { fitWalkingRoute(route.points, viewport, inset) }
        var zoom by remember(route, origin, viewport) { mutableStateOf(1f) }
        var pan by remember(route, origin, viewport) { mutableStateOf(Offset.Zero) }
        val projection = remember(fitted, zoom, pan) { Projection(fitted.center + pan, fitted.scale * zoom, fitted.anchor) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(route, origin, viewport) {
                    detectTransformGestures { focus, shift, magnify, _ ->
                        val next = (zoom * magnify).coerceIn(.65f, 5f)
                        pan = focus + (fitted.center + pan - focus) * (next / zoom) + shift - fitted.center
                        zoom = next
                    }
                }
                .pointerInput(route, origin, viewport) {
                    detectTapGestures(onDoubleTap = { focus ->
                        if (zoom == 1f && pan == Offset.Zero) {
                            pan = fitted.center - focus
                            zoom = 2f
                        } else {
                            pan = Offset.Zero
                            zoom = 1f
                        }
                    })
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (demo) streetMap(projection, hospital, walkingDetail = true)
            }
            Canvas(Modifier.fillMaxSize()) {
                val point = userPoint() ?: return@Canvas
                val degrees = heading.value.degrees ?: return@Canvas
                val center = projection.at(point)
                withTransform({
                    translate(center.x, center.y)
                    rotate(degrees - 90f - DirectionFanSweep / 2f, pivot = Offset.Zero)
                }) {
                    drawArc(
                        fanBrush, startAngle = 0f, sweepAngle = DirectionFanSweep, useCenter = true,
                        topLeft = Offset(-fanRadius, -fanRadius), size = Size(fanRadius * 2, fanRadius * 2)
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) { drawRoute(route, projection, Gold, true) }
            markers(projection)
            Canvas(Modifier.fillMaxSize().zIndex(.5f)) {
                val point = userPoint() ?: return@Canvas
                val center = projection.at(point)
                val besideMarker = (center - projection.at(route.points.first())).getDistance() <= 12.dp.toPx() ||
                    (center - projection.at(route.points.last())).getDistance() <= 12.dp.toPx()
                val radius = if (besideMarker) 3.5.dp.toPx() else 5.dp.toPx()
                drawCircle(White, radius + 1.5.dp.toPx(), center)
                drawCircle(LivePositionBlue, radius, center)
            }
            if (heading.value.needsCalibration) {
                MapTag("휴대폰을 8자로 움직여 방향을 보정하세요", Modifier.align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 36.dp)
                    .zIndex(2f), Blue)
            }
        }
    }
}


