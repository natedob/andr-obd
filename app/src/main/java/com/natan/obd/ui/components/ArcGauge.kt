package com.natan.obd.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val START_ANGLE = 135f
private const val SWEEP = 270f

/** A 270° animated gauge with a gradient arc, a glowing tip and the value in the middle. */
@Composable
fun ArcGauge(
    value: Float,
    max: Float,
    label: String,
    unit: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 160.dp,
    big: Boolean = false,
) {
    val fraction by animateFloatAsState((value / max).coerceIn(0f, 1f), tween(300), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier.size(gaugeSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(gaugeSize)) {
            val stroke = this.size.minDimension * 0.085f
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)

            drawArc(track, START_ANGLE, SWEEP, false, topLeft, arcSize, style = style)

            if (fraction > 0.003f) {
                // The sweep shader's 0° is at 3 o'clock; rotating the canvas lines it up with the arc start.
                rotate(START_ANGLE, pivot = center) {
                    val stops = colors.mapIndexed { i, c -> (i / (colors.size - 1).coerceAtLeast(1).toFloat()) * 0.75f to c }
                    val brush = Brush.sweepGradient(*stops.toTypedArray(), 0.75f to colors.last(), 1f to colors.last(), center = center)
                    // A wide, translucent pass underneath gives the arc a soft glow.
                    drawArc(brush, 0f, SWEEP * fraction, false, topLeft, arcSize, alpha = 0.25f,
                        style = Stroke(width = stroke * 1.9f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, SWEEP * fraction, false, topLeft, arcSize, style = style)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toInt().toString(),
                style = if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall,
                fontSize = if (big) 64.sp else 34.sp,
            )
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
