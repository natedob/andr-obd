package com.natan.obd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A small filled line chart with faint gridlines. Enough for a trip's speed/RPM/load traces. */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width
        val h = size.height
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1)
            val y = h - (v / maxValue).coerceIn(0f, 1f) * h
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
