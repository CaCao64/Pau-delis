package com.pau.busapp

import android.graphics.*
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentLineMapBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.Normalizer

class LineMapFragment : Fragment() {

    private var _b: FragmentLineMapBinding? = null
    private val b get() = _b!!
    private lateinit var map: MapView

    companion object {
        fun newInstance(line: BusLine, highlightStop: String = "") = LineMapFragment().apply {
            arguments = Bundle().apply {
                putString("num", line.number)
                putString("highlight", highlightStop)
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentLineMapBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val line = AppData.busLines.find { it.number == arguments?.getString("num") } ?: return

        Configuration.getInstance().userAgentValue = requireContext().packageName

        map = b.lineMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true

        b.header.setBackgroundColor(line.color)
        b.tvTitle.text = "Ligne ${line.number}  -  ${line.terminus1} <-> ${line.terminus2}"
        b.btnRetour.setOnClickListener { parentFragmentManager.popBackStack() }

        val stopsByName = AppData.busStops.associateBy { normalizeTransitName(it.name) }

        fun resolveStop(name: String): BusStop? =
            stopsByName[normalizeTransitName(name)]
                ?: AppData.busStops.find {
                    val a = normalizeTransitName(it.name)
                    val b = normalizeTransitName(name)
                    a.contains(b) || b.contains(a)
                }

        val dir1Points = line.stopsDir1.mapNotNull { resolveStop(it) }
        val dir2Points = line.stopsDir2.mapNotNull { resolveStop(it) }
        val allStops = (dir1Points + dir2Points).distinctBy { it.name }

        fun addPolyline(points: List<GeoPoint>, alpha: Int, width: Float, tag: String) {
            if (points.size < 2) return
            val poly = Polyline(map).apply {
                setPoints(points)
                outlinePaint.color = line.color
                outlinePaint.alpha = alpha
                outlinePaint.strokeWidth = width
                outlinePaint.isAntiAlias = true
                infoWindow = null
                title = tag
            }
            map.overlays.add(poly)
        }

        addPolyline(dir1Points.map { GeoPoint(it.lat, it.lon) }, 255, 8f, "fallback")
        addPolyline(dir2Points.map { GeoPoint(it.lat, it.lon) }, 160, 4f, "fallback")

        viewLifecycleOwner.lifecycleScope.launch {
            val shapes = runCatching { GtfsReader.getLineShapes(requireContext(), line.number) }.getOrDefault(emptyList())
            if (!isAdded || _b == null || shapes.isEmpty()) return@launch
            map.overlays.removeAll { it is Polyline && it.title == "gtfs_shape" }
            shapes.forEachIndexed { index, points ->
                val poly = Polyline(map).apply {
                    setPoints(points)
                    outlinePaint.color = line.color
                    outlinePaint.alpha = if (index == 0) 255 else 170
                    outlinePaint.strokeWidth = if (index == 0) 9f else 5f
                    outlinePaint.isAntiAlias = true
                    infoWindow = null
                    title = "gtfs_shape"
                }
                map.overlays.add(poly)
            }
            map.invalidate()
        }

        val highlight = arguments?.getString("highlight") ?: ""

        allStops.forEach { stop ->
            val isTerminus = stop.name == line.terminus1 || stop.name == line.terminus2 ||
                normalizeTransitName(stop.name) == normalizeTransitName(line.terminus1) ||
                normalizeTransitName(stop.name) == normalizeTransitName(line.terminus2) ||
                normalizeTransitName(dir1Points.firstOrNull()?.name ?: "") == normalizeTransitName(stop.name) ||
                normalizeTransitName(dir1Points.lastOrNull()?.name ?: "") == normalizeTransitName(stop.name) ||
                normalizeTransitName(dir2Points.firstOrNull()?.name ?: "") == normalizeTransitName(stop.name) ||
                normalizeTransitName(dir2Points.lastOrNull()?.name ?: "") == normalizeTransitName(stop.name)
            val isHighlight = highlight.isNotEmpty() && (
                normalizeTransitName(stop.name) == normalizeTransitName(highlight) ||
                    normalizeTransitName(stop.name).contains(normalizeTransitName(highlight)) ||
                    normalizeTransitName(highlight).contains(normalizeTransitName(stop.name))
                )
            val marker = Marker(map).apply {
                position = GeoPoint(stop.lat, stop.lon)
                title = stop.name
                icon = android.graphics.drawable.BitmapDrawable(
                    resources, makeStopDot(line.color, isTerminus, isHighlight)
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    (activity as? MainActivity)?.openDetails(stop)
                    true
                }
            }
            map.overlays.add(marker)
        }

        if (allStops.isNotEmpty()) {
            val lats = allStops.map { it.lat }
            val lons = allStops.map { it.lon }
            val box = BoundingBox(lats.max(), lons.max(), lats.min(), lons.min())
            map.post { map.zoomToBoundingBox(box.increaseByScale(1.15f), false) }
        }

        fun addTerminusLabel(stop: BusStop?, title: String, topAnchor: Boolean) {
            if (stop == null) return
            val marker = Marker(map).apply {
                position = GeoPoint(stop.lat, stop.lon)
                this.title = title
                infoWindow = null
                icon = android.graphics.drawable.BitmapDrawable(resources, makeLabelBitmap(title, line.color))
                setAnchor(Marker.ANCHOR_CENTER, if (topAnchor) Marker.ANCHOR_TOP else Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
        }

        addTerminusLabel(dir1Points.firstOrNull(), line.terminus1, false)
        addTerminusLabel(dir1Points.lastOrNull(), line.terminus2, true)
        addTerminusLabel(dir2Points.firstOrNull(), line.terminus2, false)
        addTerminusLabel(dir2Points.lastOrNull(), line.terminus1, true)

        map.invalidate()
    }

    private fun makeLabelBitmap(text: String, color: Int): Bitmap {
        val label = text.take(24)
        val fillColor = color
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.color = Color.WHITE
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(215, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
        }
        val fm = textPaint.fontMetrics
        val width = (textPaint.measureText(label) + 32f).toInt().coerceAtLeast(140)
        val height = ((fm.bottom - fm.top) + 24f).toInt().coerceAtLeast(56)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
        canvas.drawText(label, 16f, height / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
        return bmp
    }

    private fun makeStopDot(color: Int, isTerminus: Boolean, isHighlight: Boolean = false): Bitmap {
        val r = when {
            isHighlight -> 22f
            isTerminus -> 18f
            else -> 12f
        }
        val bmp = Bitmap.createBitmap((r * 2).toInt(), (r * 2).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            if (isHighlight) {
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                })
            } else {
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = if (isTerminus) 4f else 3f
                })
                if (isTerminus) {
                    drawCircle(r, r, r * 0.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                }
            }
        }
        return bmp
    }

    private fun normalizeTransitName(value: String): String {
        val trimmed = value.replace(Regex("\\s+"), " ").trim()
        val strippedQuai = trimmed.replace(Regex("\\s+quai\\s+[a-z0-9]+$", RegexOption.IGNORE_CASE), "")
        val normalized = Normalizer.normalize(strippedQuai, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}+"), "").lowercase()
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::map.isInitialized) map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
