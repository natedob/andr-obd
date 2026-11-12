package com.natan.obd.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natan.obd.ui.theme.scoreColor
import kotlin.math.roundToInt

/** A full-circle score indicator that counts up from zero when it first appears. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, ringSize: Dp = 160.dp, animate: Boolean = true) {
    var target by remember { mutableFloatStateOf(if (animate) 0f else score.toFloat()) }
    LaunchedEffect(score) { target = score.toFloat() }
    val shown by animateFloatAsState(target, tween(if (animate) 1300 else 0), label = "score")
    val color = scoreColor(shown.roundToInt())
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val stroke = this.size.minDimension * 0.09f
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * (shown / 100f), false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            shown.roundToInt().toString(),
            style = MaterialTheme.typography.displayMedium,
            fontSize = (ringSize.value * 0.34f).sp,
            color = color,
        )
    }
}
