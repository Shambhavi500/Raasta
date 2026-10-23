package com.raasta.app.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

enum class PinStyle { YOU, PICKUP, DROP, STOP, STATION, VEHICLE }

data class MapPin(
    val id: String,
    val at: LatLon,
    val style: PinStyle,
    val label: String? = null,
    val color: Color? = null,
)

data class MapLine(val points: List<LatLon>, val color: Color, val dashed: Boolean = false)

/** A soft circle on the heatmap layer, [radiusM] across on the ground. Spots with an [id] can be tapped. */
data class HeatSpot(
    val at: LatLon,
    val radiusM: Double,
    val color: Color,
    /** 0..1, how strongly the spot is drawn. */
    val strength: Float,
    val id: String? = null,
)

/** What the camera should do. A new value is applied once; panning by hand is kept until then. */
sealed interface MapCamera {
    /** [token] lets a caller re-apply the same view, e.g. a "re-center" button. */
    data class Fit(val points: List<LatLon>, val token: Int = 0) : MapCamera
    data class Center(val at: LatLon, val zoom: Double, val token: Int = 0) : MapCamera
    data class Follow(val at: LatLon, val zoom: Double) : MapCamera
    /** Leave the camera where the user put it. */
    data object Free : MapCamera
}

/**
 * Standard OpenStreetMap tiles, turned into a night map with a colour filter:
 * invert, rotate hue back 180 degrees so water and parks keep their colours, then dim.
 */
private val NightFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ).apply {
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    -0.574f, 1.430f, 0.144f, 0f, 0f,
                    0.426f, 0.430f, 0.144f, 0f, 0f,
                    0.426f, 1.430f, -0.856f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        postConcat(ColorMatrix().apply { setScale(0.82f, 0.84f, 0.9f, 1f) })
    },
)

private val MapBackground = 0xFF0E0F11.toInt()

fun configureMaps(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = RouteShapes.USER_AGENT
        val base = File(context.cacheDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun RaastaMap(
    pins: List<MapPin>,
    lines: List<MapLine>,
    camera: MapCamera,
    modifier: Modifier = Modifier,
    /** Moves the camera's focus up (negative) so it is not hidden behind a bottom sheet. */
    centerOffsetY: Dp = 0.dp,
    onMapTouched: () -> Unit = {},
    onPinClick: (String) -> Unit = {},
    /** Drawn under the lines and pins. */
    heat: List<HeatSpot> = emptyList(),
    onHeatSpotClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val offsetPx = with(LocalDensity.current) { centerOffsetY.roundToPx() }
    val holder = remember { MapHolder(context) }
    val touched by rememberUpdatedState(onMapTouched)
    val pinClick by rememberUpdatedState(onPinClick)
    val heatClick by rememberUpdatedState(onHeatSpotClick)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.map.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            holder.map.apply {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) touched()
                    false
                }
            }
        },
        update = {
            holder.onPinClick = { id -> pinClick(id) }
            holder.heat.onSpotClick = { id -> heatClick(id) }
            holder.setCenterOffset(offsetPx)
            holder.setHeat(heat)
            holder.setLines(lines)
            holder.setPins(pins)
            holder.setCamera(camera)
        },
    )
}

private class MapHolder(private val context: Context) {
    val map: MapView = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        overlayManager.tilesOverlay.setColorFilter(NightFilter)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        minZoomLevel = 9.0
        maxZoomLevel = 19.0
        setBackgroundColor(MapBackground)
        overlayManager.tilesOverlay.loadingBackgroundColor = MapBackground
        overlayManager.tilesOverlay.loadingLineColor = MapBackground
        overlays += CopyrightOverlay(context).apply {
            setTextColor(0x99FFFFFF.toInt())
            setAlignRight(true)
        }
        controller.setZoom(13.0)
        controller.setCenter(GeoPoint(18.535, 73.88))
    }
    var onPinClick: (String) -> Unit = {}
    val heat = HeatOverlay()

    init {
        // First in the list, so it draws over the tiles and under everything else.
        map.overlays.add(0, heat)
    }

    private val density = context.resources.displayMetrics.density
    private var shownLines: List<MapLine> = emptyList()
    private val lineOverlays = mutableListOf<Polyline>()
    private val markers = mutableMapOf<String, Pair<MapPin, Marker>>()
    private var appliedCamera: MapCamera? = null
    private var centerOffset = 0

    fun setCenterOffset(px: Int) {
        if (px == centerOffset) return
        centerOffset = px
        map.setMapCenterOffset(0, px)
    }

    fun setLines(lines: List<MapLine>) {
        if (lines == shownLines) return
        shownLines = lines
        map.overlays.removeAll(lineOverlays)
        lineOverlays.clear()
        lines.forEach { line ->
            // A dark casing under each line keeps it readable over busy streets.
            if (!line.dashed) lineOverlays += polyline(line.points, Obsidian.toArgb(), 9f, dashed = false)
            lineOverlays += polyline(line.points, line.color.toArgb(), if (line.dashed) 4f else 6f, line.dashed)
        }
        // Lines go under the markers, which sit at the end of the overlay list.
        map.overlays.addAll(1, lineOverlays)
        map.invalidate()
    }

    private fun polyline(points: List<LatLon>, color: Int, widthDp: Float, dashed: Boolean) = Polyline(map).apply {
        setPoints(points.map { GeoPoint(it.lat, it.lon) })
        outlinePaint.color = color
        outlinePaint.strokeWidth = widthDp * density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
        if (dashed) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(2f * density, 7f * density), 0f)
        infoWindow = null
        setOnClickListener { _, _, _ -> false }
    }

    fun setHeat(spots: List<HeatSpot>) {
        if (spots == heat.spots) return
        heat.spots = spots
        map.invalidate()
    }

    fun setPins(pins: List<MapPin>) {
        val wanted = pins.associateBy { it.id }
        markers.keys.filter { it !in wanted }.forEach { id ->
            map.overlays.remove(markers.remove(id)!!.second)
        }
        pins.forEach { pin ->
            val existing = markers[pin.id]
            if (existing != null && existing.first.style == pin.style &&
                existing.first.label == pin.label && existing.first.color == pin.color
            ) {
                if (existing.first.at != pin.at) {
                    existing.second.position = GeoPoint(pin.at.lat, pin.at.lon)
                    markers[pin.id] = pin to existing.second
                }
            } else {
                existing?.let { map.overlays.remove(it.second) }
                val marker = createMarker(pin)
                map.overlays += marker
                markers[pin.id] = pin to marker
            }
        }
        map.invalidate()
    }

    private fun createMarker(pin: MapPin): Marker = Marker(map).apply {
        position = GeoPoint(pin.at.lat, pin.at.lon)
        val (bitmap, anchorY) = PinArt.draw(pin, density)
        icon = BitmapDrawable(context.resources, bitmap)
        setAnchor(Marker.ANCHOR_CENTER, anchorY)
        infoWindow = null
        setOnMarkerClickListener { _, _ -> onPinClick(pin.id); true }
    }

    fun setCamera(camera: MapCamera) {
        if (camera == appliedCamera) return
        val previous = appliedCamera
        appliedCamera = camera
        when (camera) {
            is MapCamera.Fit -> whenLaidOut { fit(camera.points) }
            is MapCamera.Center -> whenLaidOut {
                map.controller.setZoom(camera.zoom)
                map.controller.setCenter(GeoPoint(camera.at.lat, camera.at.lon))
            }
            is MapCamera.Follow -> whenLaidOut {
                val point = GeoPoint(camera.at.lat, camera.at.lon)
                if (previous is MapCamera.Follow && previous.zoom == camera.zoom) {
                    map.controller.setCenter(point)
                } else {
                    map.controller.animateTo(point, camera.zoom, 600L)
                }
            }
            MapCamera.Free -> Unit
        }
    }

    private fun fit(points: List<LatLon>) {
        if (points.isEmpty()) return
        if (points.size == 1 || points.distinct().size == 1) {
            map.controller.setZoom(16.0)
            map.controller.setCenter(GeoPoint(points[0].lat, points[0].lon))
            return
        }
        val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lon) })
        // Generous border so pin labels near the edge stay on screen.
        map.zoomToBoundingBox(box, false, (84 * density).toInt())
    }

    private fun whenLaidOut(action: () -> Unit) {
        if (map.width > 0 && map.height > 0) action()
        else map.addOnFirstLayoutListener { _, _, _, _, _ -> action() }
    }
}

/** Heatmap spots drawn as one blurred circle bitmap, tinted and scaled per spot. */
private class HeatOverlay : Overlay() {
    var spots: List<HeatSpot> = emptyList()
    var onSpotClick: (String) -> Unit = {}

    private val blob: Bitmap = Bitmap.createBitmap(BLOB_PX, BLOB_PX, Bitmap.Config.ARGB_8888).apply {
        val r = BLOB_PX / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r, r, r,
                intArrayOf(0xFFFFFFFF.toInt(), 0x99FFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(this).drawCircle(r, r, r, paint)
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tints = mutableMapOf<Int, PorterDuffColorFilter>()
    private val point = Point()
    private val rect = RectF()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || spots.isEmpty()) return
        val projection = mapView.projection
        val screen = RectF(projection.screenRect)
        spots.forEach { spot ->
            projection.toPixels(GeoPoint(spot.at.lat, spot.at.lon), point)
            val r = projection.metersToPixels(spot.radiusM.toFloat(), spot.at.lat, projection.zoomLevel)
            if (r < 2f) return@forEach
            rect.set(point.x - r, point.y - r, point.x + r, point.y + r)
            if (!RectF.intersects(rect, screen)) return@forEach
            val argb = spot.color.toArgb()
            paint.colorFilter = tints.getOrPut(argb) { PorterDuffColorFilter(argb or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN) }
            paint.alpha = (spot.strength.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawBitmap(blob, null, rect, paint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        // Topmost (last drawn) first.
        val hit = spots.lastOrNull { spot ->
            if (spot.id == null) return@lastOrNull false
            projection.toPixels(GeoPoint(spot.at.lat, spot.at.lon), point)
            val r = projection.metersToPixels(spot.radiusM.toFloat(), spot.at.lat, projection.zoomLevel)
            val dx = e.x - point.x
            val dy = e.y - point.y
            dx * dx + dy * dy <= r * r
        } ?: return false
        onSpotClick(hit.id!!)
        return true
    }

    private companion object {
        const val BLOB_PX = 128
    }
}

/** Marker bitmaps drawn in code so they stay crisp at any density. */
private object PinArt {
    private const val BLUE = 0xFF3B82F6.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Returns the bitmap and the vertical anchor (0..1) of the pin's centre. */
    fun draw(pin: MapPin, density: Float): Pair<Bitmap, Float> {
        val size = (pinSize(pin.style) * density).toInt()
        val label = pin.label
        if (label == null) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawPin(Canvas(bitmap), pin, size / 2f, size / 2f, density)
            return bitmap to 0.5f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val shown = if (label.length > 28) label.take(27) + "…" else label
        val padH = 10f * density
        val pillH = 26f * density
        val gap = 6f * density
        val pillW = text.measureText(shown) + padH * 2
        val width = maxOf(pillW, size.toFloat()).toInt() + 2
        val height = (pillH + gap + size).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = (width - pillW) / 2
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Obsidian.toArgb() }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = 0x40FFFFFF
        }
        val rect = RectF(left, 0f, left + pillW, pillH)
        canvas.drawRoundRect(rect, pillH / 2, pillH / 2, bg)
        canvas.drawRoundRect(rect, pillH / 2, pillH / 2, border)
        canvas.drawText(shown, left + padH, pillH / 2 - (text.descent() + text.ascent()) / 2, text)
        val cy = pillH + gap + size / 2f
        drawPin(canvas, pin, width / 2f, cy, density)
        return bitmap to cy / height
    }

    private fun pinSize(style: PinStyle): Int = when (style) {
        PinStyle.YOU -> 34
        PinStyle.PICKUP, PinStyle.DROP -> 20
        PinStyle.STOP -> 16
        PinStyle.STATION -> 12
        PinStyle.VEHICLE -> 30
    }

    private fun drawPin(canvas: Canvas, pin: MapPin, cx: Float, cy: Float, density: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val d = density
        when (pin.style) {
            PinStyle.YOU -> {
                fill.color = 0x333B82F6
                canvas.drawCircle(cx, cy, 17 * d, fill)
                fill.color = WHITE
                canvas.drawCircle(cx, cy, 8.5f * d, fill)
                fill.color = BLUE
                canvas.drawCircle(cx, cy, 6 * d, fill)
            }
            PinStyle.PICKUP -> {
                fill.color = WHITE
                canvas.drawCircle(cx, cy, 10 * d, fill)
                fill.color = Obsidian.toArgb()
                canvas.drawCircle(cx, cy, 4 * d, fill)
            }
            PinStyle.DROP -> {
                fill.color = ElectricLime.toArgb()
                canvas.drawRect(cx - 9 * d, cy - 9 * d, cx + 9 * d, cy + 9 * d, fill)
                fill.color = Obsidian.toArgb()
                canvas.drawRect(cx - 3.5f * d, cy - 3.5f * d, cx + 3.5f * d, cy + 3.5f * d, fill)
            }
            PinStyle.STOP -> {
                fill.color = Obsidian.toArgb()
                canvas.drawCircle(cx, cy, 8 * d, fill)
                fill.color = pin.color?.toArgb() ?: 0xFFF59E0B.toInt()
                canvas.drawCircle(cx, cy, 5.5f * d, fill)
            }
            PinStyle.STATION -> {
                fill.color = WHITE
                canvas.drawCircle(cx, cy, 6 * d, fill)
                fill.color = pin.color?.toArgb() ?: 0xFF38BDF8.toInt()
                canvas.drawCircle(cx, cy, 4 * d, fill)
            }
            PinStyle.VEHICLE -> {
                fill.color = 0x55000000
                canvas.drawCircle(cx, cy + 1 * d, 15 * d, fill)
                fill.color = WHITE
                canvas.drawCircle(cx, cy, 14 * d, fill)
                fill.color = pin.color?.toArgb() ?: ElectricLime.toArgb()
                canvas.drawCircle(cx, cy, 10.5f * d, fill)
                fill.color = Obsidian.toArgb()
                canvas.drawCircle(cx, cy, 3.5f * d, fill)
            }
        }
    }
}
