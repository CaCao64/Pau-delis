package com.pau.busapp

import android.graphics.*
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.pau.busapp.databinding.FragmentLineMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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
        _b = FragmentLineMapBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val line = AppData.busLines.find { it.number == arguments?.getString("num") } ?: return

        Configuration.getInstance().userAgentValue = requireContext().packageName

        map = b.lineMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true

        // Header coloré
        b.header.setBackgroundColor(line.color)
        b.tvTitle.text = "Ligne ${line.number}  –  ${line.terminus1} ↔ ${line.terminus2}"
        b.btnRetour.setOnClickListener { parentFragmentManager.popBackStack() }

        // Résoudre les noms d'arrêts → BusStop avec coordonnées
        val stopsByName = AppData.busStops.associateBy { it.name }

        fun resolveStop(name: String): BusStop? =
            stopsByName[name]
                ?: AppData.busStops.find { it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true) }

        val dir1Points = line.stopsDir1.mapNotNull { resolveStop(it) }
        val dir2Points = line.stopsDir2.mapNotNull { resolveStop(it) }

        // Tous les arrêts uniques de la ligne
        val allStops = (dir1Points + dir2Points).distinctBy { it.name }

        // Polyline direction 1
        if (dir1Points.size >= 2) {
            val poly = Polyline(map).apply {
                setPoints(dir1Points.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = line.color
                outlinePaint.strokeWidth = 8f
                outlinePaint.isAntiAlias = true
            }
            map.overlays.add(poly)
        }

        // Polyline direction 2 (légèrement décalée visuellement via alpha)
        if (dir2Points.size >= 2) {
            val poly = Polyline(map).apply {
                setPoints(dir2Points.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = line.color
                outlinePaint.alpha = 160
                outlinePaint.strokeWidth = 4f
                outlinePaint.isAntiAlias = true
            }
            map.overlays.add(poly)
        }

        val highlight = arguments?.getString("highlight") ?: ""

        // Marqueurs pour chaque arrêt de la ligne
        allStops.forEach { stop ->
            val isTerminus = stop.name == line.terminus1 || stop.name == line.terminus2 ||
                dir1Points.firstOrNull()?.name == stop.name || dir1Points.lastOrNull()?.name == stop.name ||
                dir2Points.firstOrNull()?.name == stop.name || dir2Points.lastOrNull()?.name == stop.name
            val isHighlight = highlight.isNotEmpty() && (
                stop.name.equals(highlight, ignoreCase = true) ||
                stop.name.contains(highlight, ignoreCase = true) ||
                highlight.contains(stop.name, ignoreCase = true))
            val marker = Marker(map).apply {
                position = GeoPoint(stop.lat, stop.lon)
                title    = stop.name
                icon     = android.graphics.drawable.BitmapDrawable(
                    resources, makeStopDot(line.color, isTerminus, isHighlight))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    (activity as? MainActivity)?.openDetails(stop); true
                }
            }
            map.overlays.add(marker)
        }

        // Zoom pour englober tous les arrêts
        if (allStops.isNotEmpty()) {
            val lats = allStops.map { it.lat }
            val lons = allStops.map { it.lon }
            val box  = BoundingBox(lats.max(), lons.max(), lats.min(), lons.min())
            map.post {
                map.zoomToBoundingBox(box.increaseByScale(1.15f), false)
            }
        }

        map.invalidate()
    }

    private fun makeStopDot(color: Int, isTerminus: Boolean, isHighlight: Boolean = false): Bitmap {
        val r = when {
            isHighlight -> 22f
            isTerminus  -> 18f
            else        -> 12f
        }
        val bmp = Bitmap.createBitmap((r * 2).toInt(), (r * 2).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            if (isHighlight) {
                // Plein couleur de la ligne
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                // Contour blanc
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
                })
            } else {
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; style = Paint.Style.STROKE
                    strokeWidth = if (isTerminus) 4f else 3f
                })
                if (isTerminus) {
                    drawCircle(r, r, r * 0.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                }
            }
        }
        return bmp
    }

    override fun onResume()      { super.onResume();  if (::map.isInitialized) map.onResume() }
    override fun onPause()       { super.onPause();   if (::map.isInitialized) map.onPause()  }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
