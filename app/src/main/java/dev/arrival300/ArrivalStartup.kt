package dev.arrival300

import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.first
import kotlin.math.abs

@Composable
internal fun ArrivalStartup(window: Window, show: Boolean, content: @Composable () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    var prepareMain by remember { mutableStateOf(false) }
    var mainMeasured by remember { mutableStateOf(false) }
    val introVisible = show && !finished

    Box(Modifier.fillMaxSize().background(Page)) {
        if (prepareMain || !introVisible) {
            Layout(
                content = content,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { mainMeasured = true }
            ) { children, constraints ->
                val placeables = children.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Prepare the main layout without drawing it or accepting touches during the intro.
                    if (!introVisible) placeables.forEach { it.placeRelative(0, 0) }
                }
            }
        }
        if (introVisible) {
            StartupIntro(window, prepareMain = { prepareMain = true }, mainMeasured = { mainMeasured }) {
                finished = true
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun StartupIntro(window: Window, prepareMain: () -> Unit, mainMeasured: () -> Boolean, onFinished: () -> Unit) {
    val navy = colorResource(R.color.arrival_launch_navy)
    val opacity = remember { Animatable(1f) }
    DisposableEffect(window, navy) {
        val status = window.statusBarColor
        val navigation = window.navigationBarColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        window.statusBarColor = navy.toArgb()
        window.navigationBarColor = navy.toArgb()
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            window.statusBarColor = status
            window.navigationBarColor = navigation
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        prepareMain()
        Animatable(0f).animateTo(1f, tween(660, easing = LinearEasing))
        snapshotFlow(mainMeasured).first { it }
        opacity.animateTo(0f, tween(190, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(navy)) {
        StartupScene(navy, Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value })
    }
}

@Composable
private fun StartupScene(navy: Color, modifier: Modifier) {
    BoxWithConstraints(
        modifier.background(Brush.linearGradient(listOf(navy, Color(0xFF040D33), Color(0xFF081441))))
    ) {
        Canvas(Modifier.fillMaxSize()) { drawStartupWaves() }
        val compact = maxHeight < 420.dp
        val artworkWidth = minOf(240.dp, maxWidth * .76f, maxHeight * .68f)
        val titleSize = minOf(42f, maxWidth.value / 7f, maxHeight.value / 7.2f).sp
        Column(
            Modifier.align(Alignment.Center).offset(y = if (compact) 0.dp else minOf(30.dp, maxHeight * .045f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(artworkWidth, artworkWidth * .66f)) {
                val artScale = minOf(size.width / 240f, size.height / 158f)
                withTransform({
                    translate((size.width - 240f * artScale) / 2, (size.height - 158f * artScale) / 2)
                    scale(artScale, artScale, pivot = Offset.Zero)
                }) { drawWalkingIllustration() }
            }
            Spacer(Modifier.height(if (compact) 9.dp else 15.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                fontSize = titleSize,
                lineHeight = titleSize * 1.13f,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-.8).sp
            )
            Spacer(Modifier.height(if (compact) 18.dp else 29.dp))
            StartupBars()
        }
    }
}

private fun DrawScope.drawStartupWaves() {
    val w = size.width
    val h = size.height
    val top = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * .51f, 0f)
        cubicTo(w * .39f, h * .10f, w * .22f, h * .17f, 0f, h * .20f)
        close()
    }
    drawPath(top, Brush.linearGradient(
        listOf(Color(0xFF06153E), Color(0xFF09245F), Color(0xFF125BC8)),
        start = Offset(w * .32f, 0f), end = Offset(0f, h * .20f)
    ))
    val distantWave = Path().apply {
        moveTo(0f, h)
        cubicTo(w * .29f, h * .89f, w * .67f, h * .86f, w, h * .79f)
        lineTo(w, h)
        close()
    }
    drawPath(distantWave, Brush.linearGradient(
        listOf(Color(0xFF071235), Color(0xFF0A215A), Color(0xFF123D91)),
        start = Offset(0f, h), end = Offset(w, h * .79f)
    ))
    val nearWave = Path().apply {
        moveTo(w * .43f, h)
        cubicTo(w * .60f, h * .91f, w * .78f, h * .81f, w, h * .77f)
        lineTo(w, h)
        close()
    }
    drawPath(nearWave, Brush.linearGradient(
        listOf(Color(0xFF1769E4), Color(0xFF0A378D), Color(0xFF051842)),
        start = Offset(w * .80f, h * .80f), end = Offset(w * .89f, h)
    ))
}

private fun DrawScope.drawWalkingIllustration() {
    val destination = Offset(177f, 73f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xA02269FF), Color(0x002269FF)), destination, 38f),
        radius = 38f, center = destination
    )
    val ribbon = Path().apply {
        moveTo(21f, 145f)
        cubicTo(57f, 122f, 98f, 124f, 143f, 112f)
        cubicTo(196f, 98f, 194f, 90f, 176f, 82f)
        cubicTo(168f, 78f, 169f, 74f, 177f, 72f)
        cubicTo(172f, 77f, 189f, 77f, 197f, 85f)
        cubicTo(223f, 111f, 169f, 122f, 133f, 129f)
        cubicTo(91f, 136f, 51f, 135f, 21f, 145f)
        close()
    }
    drawPath(ribbon, Brush.linearGradient(
        0f to Color(0xFF123AC5),
        .55f to Color(0xFF1B74FC),
        .80f to Color(0xFF49D7FC),
        1f to Color(0xFF2478FF),
        start = Offset(24f, 139f), end = Offset(204f, 77f)
    ))
    drawOval(
        Color(0xFF75DFFF), topLeft = Offset(157f, 68f), size = Size(40f, 10f), style = Stroke(2.5f)
    )

    val pin = Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(177f, 71f)
        cubicTo(172f, 64f, 161f, 52f, 161f, 41f)
        cubicTo(161f, 32f, 168f, 25f, 177f, 25f)
        cubicTo(186f, 25f, 193f, 32f, 193f, 41f)
        cubicTo(193f, 52f, 182f, 64f, 177f, 71f)
        close()
        addOval(Rect(171f, 35f, 183f, 47f))
    }
    drawPath(pin, Brush.verticalGradient(listOf(Color.White, Color(0xFFCAE8FF)), 25f, 71f))
    drawLine(Color.White, Offset(185f, 19f), Offset(185f, 12f), 3.5f, StrokeCap.Round)
    drawLine(Color.White, Offset(194f, 22f), Offset(198f, 16f), 3.5f, StrokeCap.Round)
    drawLine(Color.White, Offset(199f, 29f), Offset(205f, 26f), 3.5f, StrokeCap.Round)

    val walker = Path().apply {
        moveTo(108f, 55f)
        lineTo(101f, 76f)
        lineTo(115f, 89f)
        lineTo(121f, 105f)
        moveTo(101f, 76f)
        lineTo(97f, 89f)
        lineTo(85f, 105f)
        moveTo(106f, 55f)
        lineTo(92f, 64f)
        lineTo(88f, 76f)
        moveTo(109f, 55f)
        lineTo(119f, 68f)
        lineTo(129f, 72f)
    }
    drawCircle(Color.White, 8f, Offset(111f, 42f))
    drawPath(walker, Color.White, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun StartupBars() {
    val transition = rememberInfiniteTransition(label = "Startup bars")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "Startup highlight"
    )
    Canvas(
        Modifier.size(width = 130.dp, height = 7.dp)
            .semantics { contentDescription = "도착 준비 중" }
    ) {
        val gap = 6.dp.toPx()
        val width = (size.width - gap * 4) / 5
        for (index in 0 until 5) {
            val offset = abs(phase.value - index)
            val distance = minOf(offset, 5f - offset)
            val highlight = (1f - distance / 1.35f).coerceIn(0f, 1f)
            val eased = highlight * highlight * (3f - 2f * highlight)
            val active = lerp(Color(0xFF4CC9FF), Color(0xFFAD83FF), index / 4f)
            drawRoundRect(
                lerp(Color(0xFF202D57), active, eased),
                topLeft = Offset(index * (width + gap), 0f), size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }
    }
}
