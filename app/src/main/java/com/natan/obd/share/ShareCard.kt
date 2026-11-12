package com.natan.obd.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.natan.obd.R
import com.natan.obd.data.Trip
import com.natan.obd.ui.Units
import com.natan.obd.ui.formatDuration
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/** Draws a 1080x1350 score card with plain Android graphics (no screen capture needed) and saves it as a PNG. */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    fun render(context: Context, trip: Trip, driverName: String, units: Units): File {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF0A0E14.toInt(), 0xFF16202E.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        text(c, context.getString(R.string.score_label), W / 2f, 150f, 44f, 0xFF9AA7B8.toInt(), bold = false)

        val cx = W / 2f
        val cy = 570f
        val r = 300f
        val scoreColor = when {
            trip.score >= 80 -> 0xFF3DDC84.toInt()
            trip.score >= 60 -> 0xFFFFB454.toInt()
            else -> 0xFFFF6B6B.toInt()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 56f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFF1E2733.toInt()
        c.drawCircle(cx, cy, r, paint)
        paint.color = scoreColor
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * trip.score / 100f, false, paint)
        text(c, trip.score.toString(), cx, cy + 95f, 270f, scoreColor, bold = true)

        text(c, driverName, W / 2f, 990f, 68f, 0xFFE6EAF0.toInt(), bold = true)
        text(c, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.startedAt)),
            W / 2f, 1055f, 40f, 0xFF9AA7B8.toInt(), bold = false)

        val na = "-"
        val stats = listOf(
            context.getString(R.string.tile_distance) to units.distance(trip.distanceKm),
            context.getString(R.string.tile_time) to formatDuration(trip.durationSec),
            context.getString(R.string.tile_avg_speed) to units.speed(trip.avgSpeed),
            context.getString(R.string.stat_consumption) to (trip.consumptionL100?.let(units::consumption) ?: na),
        )
        stats.forEachIndexed { i, (label, value) ->
            val x = 135f + i * 270f
            text(c, label, x, 1170f, 30f, 0xFF9AA7B8.toInt(), bold = false)
            text(c, value, x, 1220f, 36f, 0xFFE6EAF0.toInt(), bold = true)
        }
        text(c, context.getString(R.string.card_footer), W / 2f, 1305f, 34f, 0xFF2EE6C5.toInt(), bold = true)

        val file = File(Sharing.sharedDir(context), "trip-${trip.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            isFakeBoldText = bold
        }
        c.drawText(s, x, y, p)
    }
}
