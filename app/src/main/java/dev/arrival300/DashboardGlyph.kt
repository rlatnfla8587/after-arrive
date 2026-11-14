package dev.arrival300

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

internal enum class Glyph {
    HOME, CAR, PARKING, PIN, WALK, QR, SEARCH, PLUS, INFO, PLAY, CHECK, CHEVRON
}

@Composable
internal fun DashboardGlyph(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val outline = Stroke(1.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(vararg points: Float) {
                val path = Path().apply {
                    moveTo(points[0], points[1])
                    for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
                }
                drawPath(path, tint, style = outline)
            }
            fun circle(x: Float, y: Float, radius: Float) {
                drawCircle(tint, radius, Offset(x, y), style = outline)
            }
            when (glyph) {
                Glyph.HOME -> line(3f, 11f, 12f, 4f, 21f, 11f, 21f, 20f, 15f, 20f, 15f, 14f, 9f, 14f, 9f, 20f, 3f, 20f, 3f, 11f)
                Glyph.CAR -> {
                    line(4.5f, 11f, 6.5f, 6f, 17.5f, 6f, 19.5f, 11f)
                    drawRoundRect(tint, Offset(3f, 11f), Size(18f, 7f), CornerRadius(2f), style = outline)
                    line(5.5f, 18f, 5.5f, 20f)
                    line(18.5f, 18f, 18.5f, 20f)
                    line(6.5f, 14.5f, 8f, 14.5f)
                    line(16f, 14.5f, 17.5f, 14.5f)
                }
                Glyph.PARKING -> {
                    drawRoundRect(tint, Offset(3.5f, 3.5f), Size(17f, 17f), CornerRadius(4f), style = outline)
                    line(9f, 17f, 9f, 7.5f, 13f, 7.5f, 15.5f, 9f, 15.5f, 11f, 13f, 12.5f, 9f, 12.5f)
                }
                Glyph.PIN -> {
                    val marker = Path().apply {
                        moveTo(12f, 21f)
                        cubicTo(8f, 17f, 5f, 13f, 5f, 9.5f)
                        cubicTo(5f, .5f, 19f, .5f, 19f, 9.5f)
                        cubicTo(19f, 13f, 16f, 17f, 12f, 21f)
                        close()
                    }
                    drawPath(marker, tint, style = outline)
                    circle(12f, 9.5f, 2.3f)
                }
                Glyph.WALK -> {
                    circle(13.5f, 4.5f, 1.7f)
                    line(6f, 11.5f, 9f, 9f, 12.5f, 8.5f, 15f, 12f, 19f, 12f)
                    line(12.5f, 9f, 11f, 14.5f, 15f, 17f, 15.5f, 21f)
                    line(11f, 14.5f, 8.5f, 18f, 5.5f, 20f)
                }
                Glyph.QR -> {
                    listOf(Offset(3.5f, 3.5f), Offset(14.5f, 3.5f), Offset(3.5f, 14.5f)).forEach {
                        drawRoundRect(tint, it, Size(6f, 6f), CornerRadius(.5f), style = outline)
                    }
                    line(14.5f, 14.5f, 14.5f, 20.5f, 20.5f, 20.5f, 20.5f, 16.5f, 17.5f, 16.5f)
                }
                Glyph.SEARCH -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) }
                Glyph.PLUS -> { line(6f, 12f, 18f, 12f); line(12f, 6f, 12f, 18f) }
                Glyph.INFO -> {
                    circle(12f, 12f, 8.5f)
                    drawCircle(tint, 1f, Offset(12f, 8f))
                    line(12f, 11.5f, 12f, 16f)
                }
                Glyph.PLAY -> line(8f, 5f, 19f, 12f, 8f, 19f, 8f, 5f)
                Glyph.CHECK -> line(5f, 12f, 10f, 17f, 19f, 7f)
                Glyph.CHEVRON -> line(9f, 5f, 16f, 12f, 9f, 19f)
            }
        }
    }
}
