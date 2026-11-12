package com.natan.obd.ui.components

import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.natan.obd.data.RoutePoint
import com.natan.obd.ui.theme.scoreColor
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** OpenStreetMap view with the route drawn in segments coloured by the eco score at that moment. */
@Composable
fun RouteMap(points: List<RoutePoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName // OSM's tile policy requires an identifying user agent
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // The map sits inside a scrolling page: while a finger is on it, panning should move the map.
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            if (map.tag === points) return@AndroidView // already drawn; don't reset the user's pan/zoom
            map.tag = points
            map.overlays.clear()
            val geo = points.map { GeoPoint(it.lat, it.lon) }
            for (i in 0 until geo.size - 1) {
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(listOf(geo[i], geo[i + 1]))
                        outlinePaint.color = scoreColor(points[i + 1].eco).toArgb()
                        outlinePaint.strokeWidth = 14f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    },
                )
            }
            map.invalidate()
            val box = BoundingBox.fromGeoPoints(geo)
            map.post {
                if (box.latitudeSpan < 1e-4 && box.longitudeSpanWithDateLine < 1e-4) {
                    map.controller.setZoom(17.0)
                    map.controller.setCenter(GeoPoint(box.centerLatitude, box.centerLongitude))
                } else {
                    map.zoomToBoundingBox(box, false, 80)
                }
            }
        },
    )
}
