package com.pau.busapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.pau.busapp.databinding.FragmentMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.math.*

class MapFragment : Fragment() {

    private var _b: FragmentMapBinding? = null
    private val b get() = _b!!
    private lateinit var map: MapView

    // Point bleu custom — dessiné directement sur le canvas, sans Marker
    private val locationDotOverlay = LocationDotOverlay()
    private var locationManager: LocationManager? = null
    private var gpsListening = false
    private var emulationActive = false

    // Overlay canvas pour les quais de l'arrêt le plus proche (reste au-dessus de tout)
    private val nearestStopOverlay = NearestStopOverlay()

    // Cache stop_id → (lat, lon) chargé une fois depuis le GTFS
    private var quaiPositions: Map<String, Pair<Double, Double>> = emptyMap()

    // Reconstruits à chaque onViewCreated car ils capturent la MapView
    private var dotMarkers:   List<Marker> = emptyList()
    private var labelMarkers: List<Marker> = emptyList()
    private var dotMarkersLoading = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) startGps()
        else (activity as? MainActivity)?.showPermissionDeniedDialog(
            "Vous avez refusé l'accès à la position. " +
            "Le système de géolocalisation est donc indisponible. " +
            "Si vous souhaitez y accéder, merci de le modifier dans les paramètres."
        )
    }

    private var gpsFirstFix = true
    private val gpsListener = LocationListener { loc ->
        AppData.userLocation = Pair(loc.latitude, loc.longitude)
        locationDotOverlay.updateLocation(loc.latitude, loc.longitude)
        showNearestStopMarkers(loc.latitude, loc.longitude)
        if (gpsFirstFix) {
            gpsFirstFix = false
            map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 17.0, 800L)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMapBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue               = requireContext().packageName
            tileFileSystemCacheMaxBytes  = 100L * 1024 * 1024
            tileFileSystemCacheTrimBytes =  80L * 1024 * 1024
        }

        map = b.osmMap
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(43.296, -0.370))

        // Masquer la carte pendant le chargement des tiles pour éviter le flash blanc/noir
        map.alpha = 0f

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isDark) {
            val stadiaApiKey = "08e5b3c6-6716-4ff9-8698-9327ea3c5965"
            map.setTileSource(object : OnlineTileSourceBase(
                "Stadia_dark", 0, 20, 256, ".png",
                arrayOf("tiles.stadiamaps.com")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val z = MapTileIndex.getZoom(pMapTileIndex)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    return "https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/$z/$x/$y.png?api_key=$stadiaApiKey"
                }
            })
        } else {
            map.setTileSource(TileSourceFactory.MAPNIK)
        }

        // Préchargement des positions des quais en background
        Thread {
            quaiPositions = loadQuaiPositions()
        }.start()

        // Point bleu sous les arrêts, quais nearest par-dessus tout
        map.overlays.add(locationDotOverlay)
        map.overlays.add(nearestStopOverlay)

        // Fondu d'apparition dès le premier rendu de la carte
        map.post {
            map.animate().alpha(1f).setDuration(400).start()
        }

        map.addMapListener(object : MapListener {
            private var lastZoom = -1.0
            override fun onScroll(e: ScrollEvent?): Boolean {
                if (emulationActive) updateCoordsLabel(); return true
            }
            override fun onZoom(e: ZoomEvent?): Boolean {
                val z = map.zoomLevelDouble
                if (abs(z - lastZoom) >= 0.4) { lastZoom = z; refreshStops() }
                if (emulationActive) updateCoordsLabel()
                return true
            }
        })

        // Apply emulation button visibility based on user preference
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("show_emulation", false)) {
            b.btnEmulation.visibility = View.GONE
            b.btnPlaceHere.visibility = View.GONE
            b.tvEmuCoords.visibility = View.GONE
            b.crosshair.visibility = View.GONE
        } else {
            b.btnEmulation.visibility = View.VISIBLE
        }

        val zoomLat = arguments?.getDouble("zoom_lat", Double.NaN) ?: Double.NaN
        val zoomLon = arguments?.getDouble("zoom_lon", Double.NaN) ?: Double.NaN
        if (!zoomLat.isNaN() && !zoomLon.isNaN()) {
            map.controller.animateTo(GeoPoint(zoomLat, zoomLon), 18.0, 700L)
        }

        // Construire les markers hors du rendu initial pour éviter de bloquer l'ouverture
        dotMarkers = emptyList()
        labelMarkers = emptyList()
        prepareDotMarkersAsync()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) (activity as? MainActivity)?.refreshApiStatusViews()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.refreshApiStatusViews()
        if (::map.isInitialized) {
            map.onResume()
            refreshStops()
        }
    }
    override fun onPause() {
        super.onPause()
        if (::map.isInitialized) { map.onPause(); stopGps() }
    }
    override fun onDestroyView() { super.onDestroyView(); stopGps(); _b = null }

    // ── GPS ───────────────────────────────────────────────────────────────────

    private fun onMyLocationClicked() {
        val ctx = requireContext()
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ok) startGps() else locationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun onNearestStopClicked() {
        val ctx = requireContext()

        b.layoutSearchLoader.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            delay(1200)
            if (_b == null) return@launch
            b.layoutSearchLoader.visibility = View.GONE

            // L'émulation est prioritaire dans tous les cas
            if (emulationActive) {
                val c = map.mapCenter
                openNearestStop(c.latitude, c.longitude)
                return@launch
            }

            // Sans émulation : position GPS requise
            val hasFine   = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                locationPermission.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
                return@launch
            }

            // Position GPS connue (dernière position ou point bleu déjà affiché)
            val lat: Double
            val lon: Double
            if (!locationDotOverlay.currentLat.isNaN()) {
                lat = locationDotOverlay.currentLat
                lon = locationDotOverlay.currentLon
            } else {
                val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val loc = try {
                    if (hasFine)
                        lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                            ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    else
                        lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                } catch (_: SecurityException) { null }

                if (loc == null) {
                    Toast.makeText(ctx, getString(R.string.map_position_unknown), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                lat = loc.latitude
                lon = loc.longitude
            }

            openNearestStop(lat, lon)
        }
    }

    private fun openNearestStop(lat: Double, lon: Double) {
        val nearest = AppData.busStops.minByOrNull { stop ->
            val dl = stop.lat - lat; val dn = stop.lon - lon; dl * dl + dn * dn
        } ?: return
        (activity as? MainActivity)?.openDetails(nearest)
    }

    // Charge les positions individuelles des quais depuis le GTFS embarqué
    private fun loadQuaiPositions(): Map<String, Pair<Double, Double>> {
        val result = mutableMapOf<String, Pair<Double, Double>>()
        try {
            ZipInputStream(requireContext().assets.open("gtfs.zip")).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "stops.txt") {
                        val buf = ByteArrayOutputStream(); zip.copyTo(buf)
                        val reader = BufferedReader(InputStreamReader(buf.toByteArray().inputStream(), Charsets.UTF_8))
                        val headers = reader.readLine()?.split(",") ?: break
                        val idIdx  = headers.indexOf("stop_id")
                        val latIdx = headers.indexOf("stop_lat")
                        val lonIdx = headers.indexOf("stop_lon")
                        var line = reader.readLine()
                        while (line != null) {
                            val cols = splitCsvLine(line)
                            if (idIdx < cols.size && latIdx < cols.size && lonIdx < cols.size) {
                                val lat = cols[latIdx].toDoubleOrNull()
                                val lon = cols[lonIdx].toDoubleOrNull()
                                if (lat != null && lon != null) result[cols[idIdx]] = Pair(lat, lon)
                            }
                            line = reader.readLine()
                        }
                        break
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun showNearestStopMarkers(lat: Double, lon: Double) {
        val nearest = AppData.busStops.minByOrNull { stop ->
            val dl = stop.lat - lat; val dn = stop.lon - lon; dl*dl + dn*dn
        } ?: return

        val quais = nearest.codes.mapNotNull { code ->
            quaiPositions[code]?.let { (qLat, qLon) -> GeoPoint(qLat, qLon) }
        }

        nearestStopOverlay.set(nearest.name, quais)
        map.invalidate()
    }

    private fun startGps() {
        val ctx = requireContext()
        val lm  = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        gpsFirstFix = true

        val hasFine   = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        // Dernière position connue immédiatement
        val last = if (hasFine) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                       ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                   else lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (last != null) {
            locationDotOverlay.updateLocation(last.latitude, last.longitude)
            showNearestStopMarkers(last.latitude, last.longitude)
            map.controller.animateTo(GeoPoint(last.latitude, last.longitude), 17.0, 800L)
        } else {
            Toast.makeText(ctx, getString(R.string.map_gps_searching), Toast.LENGTH_SHORT).show()
        }

        // Mises à jour en continu
        if (!gpsListening) {
            if (hasFine)   lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,     20000L, 20f, gpsListener)
            if (hasCoarse) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20000L, 25f, gpsListener)
            gpsListening = true
        }
    }

    private fun stopGps() {
        locationManager?.removeUpdates(gpsListener)
        gpsListening = false
    }

    // ── Émulation ─────────────────────────────────────────────────────────────

    private fun toggleEmulation() {
        emulationActive = !emulationActive
        val ctx = requireContext()
        if (emulationActive) {
            stopGps()
            b.crosshair.visibility    = View.VISIBLE
            b.btnPlaceHere.visibility = View.VISIBLE
            b.tvEmuCoords.visibility  = View.VISIBLE
            b.btnEmulation.backgroundTintList   = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.blue_primary))
            b.btnEmulation.supportImageTintList = ColorStateList.valueOf(Color.WHITE)
            updateCoordsLabel()
        } else {
            b.crosshair.visibility    = View.GONE
            b.btnPlaceHere.visibility = View.GONE
            b.tvEmuCoords.visibility  = View.GONE
            b.btnEmulation.backgroundTintList   = ColorStateList.valueOf(Color.WHITE)
            b.btnEmulation.supportImageTintList = ColorStateList.valueOf(Color.parseColor("#888888"))
            locationDotOverlay.hide()
            nearestStopOverlay.clear()
            map.invalidate()
        }
    }

    private fun placeEmulatedHere() {
        val c = map.mapCenter
        locationDotOverlay.updateLocation(c.latitude, c.longitude)
        showNearestStopMarkers(c.latitude, c.longitude)
        map.controller.animateTo(GeoPoint(c.latitude, c.longitude), 17.0, 800L)
        Toast.makeText(requireContext(), getString(R.string.map_emulated).format(c.latitude, c.longitude), Toast.LENGTH_SHORT).show()
    }

    private fun updateCoordsLabel() {
        if (_b == null) return
        val c = map.mapCenter
        b.tvEmuCoords.text = "%.5f, %.5f".format(c.latitude, c.longitude)
    }

    // ── Stop rendering ────────────────────────────────────────────────────────

    private fun refreshStops() {
        val zoom = map.zoomLevelDouble
        map.overlays.removeAll { it is Marker }
        when {
            zoom >= ZOOM_LABELS && labelMarkers.isNotEmpty() -> labelMarkers.forEach { map.overlays.add(it) }
            zoom >= ZOOM_DOTS && dotMarkers.isNotEmpty() -> dotMarkers.forEach { map.overlays.add(it) }
            else -> cluster(AppData.busStops, zoom).forEach { map.overlays.add(buildClusterMarker(it)) }
        }
        // nearestStopOverlay est un Overlay non-Marker, reste intact au-dessus
        map.invalidate()
    }

    private fun prepareDotMarkersAsync() {
        if (dotMarkersLoading) return
        dotMarkersLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            val density = resources.displayMetrics.density
            val prepared = withContext(Dispatchers.Default) {
                AppData.busStops.map { stop ->
                    val colors = stopColors(stop)
                    PreparedStopMarker(stop, makeDotBitmap(colors))
                }
            }
            if (_b == null) return@launch
            dotMarkers = prepared.map { item ->
                Marker(map).apply {
                    position = GeoPoint(item.stop.lat, item.stop.lon)
                    title = item.stop.name
                    icon = android.graphics.drawable.BitmapDrawable(resources, item.dotBitmap)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ ->
                        (activity as? MainActivity)?.openDetails(item.stop)
                        true
                    }
                }
            }
            dotMarkersLoading = false
            if (::map.isInitialized && map.zoomLevelDouble >= ZOOM_DOTS) refreshStops()
        }
    }

    private fun stopColors(stop: BusStop): List<Int> {
        val stopKey = normalizeTransitName(stop.name)
        val lineByNumber = AppData.busLines.associateBy { it.number }
        val colors = stop.lines
            .filter { num ->
                val line = lineByNumber[num]
                line != null && (
                    line.stopsDir1.any { normalizeTransitName(it) == stopKey || normalizeTransitName(it).contains(stopKey) || stopKey.contains(normalizeTransitName(it)) } ||
                    line.stopsDir2.any { normalizeTransitName(it) == stopKey || normalizeTransitName(it).contains(stopKey) || stopKey.contains(normalizeTransitName(it)) }
                )
            }
            .mapNotNull { num -> lineByNumber[num]?.color }
        return colors.ifEmpty { listOf(0xFF_00843D.toInt()) }
    }

    private data class PreparedStopMarker(
        val stop: BusStop,
        val dotBitmap: Bitmap
    )

    private fun makeDotBitmap(colors: List<Int>): Bitmap {
        val r = 14f
        val bmp = Bitmap.createBitmap((r * 2).toInt(), (r * 2).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            if (colors.size == 1) {
                drawCircle(r, r, r - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors[0]; style = Paint.Style.STROKE; strokeWidth = 3f })
                drawCircle(r, r, r * 0.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[0] })
            } else {
                val sweep = 360f / colors.size
                val oval  = RectF(2f, 2f, r * 2 - 2f, r * 2 - 2f)
                colors.forEachIndexed { i, c ->
                    drawArc(oval, i * sweep - 90f, sweep, true,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c })
                }
                drawCircle(r, r, r * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            }
        }
        return bmp
    }

    private fun makeLabelBitmap(label: String, colors: List<Int>): Bitmap {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * resources.displayMetrics.density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.color = Color.WHITE
        }
        val name = if (label.length > 22) label.take(20) + "…" else label
        val padH = 14f; val padV = 8f; val pinH = 16f
        val boxW = tp.measureText(name) + padH * 2
        val boxH = tp.textSize + padV * 2
        val bmp  = Bitmap.createBitmap(boxW.toInt(), (boxH + pinH).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (colors.size == 1) {
                // Fond uni + pointe
                paint.color = colors[0]
                drawRoundRect(RectF(0f, 0f, boxW, boxH), 12f, 12f, paint)
                drawPath(Path().apply { moveTo(boxW/2-8f, boxH); lineTo(boxW/2+8f, boxH); lineTo(boxW/2f, boxH+pinH); close() }, paint)
            } else {
                // Sections de couleurs séparées, pas de dégradé
                val sectionW = boxW / colors.size
                colors.forEachIndexed { i, c ->
                    paint.color = c
                    val left  = i * sectionW
                    val right = left + sectionW
                    // Coins arrondis seulement aux extrémités
                    val rect = RectF(left, 0f, right, boxH)
                    when (i) {
                        0 -> {
                            // coin gauche arrondi
                            drawRoundRect(RectF(left, 0f, right + 12f, boxH), 12f, 12f, paint)
                            // recouvrir le coin droit avec un carré
                            drawRect(RectF(right, 0f, right + 12f, boxH), paint)
                        }
                        colors.size - 1 -> {
                            // coin droit arrondi
                            drawRoundRect(RectF(left - 12f, 0f, right, boxH), 12f, 12f, paint)
                            drawRect(RectF(left - 12f, 0f, left, boxH), paint)
                        }
                        else -> drawRect(rect, paint)
                    }
                }
                // Pointe de la couleur du milieu
                paint.color = colors[colors.size / 2]
                drawPath(Path().apply { moveTo(boxW/2-8f, boxH); lineTo(boxW/2+8f, boxH); lineTo(boxW/2f, boxH+pinH); close() }, paint)
            }
            drawText(name, padH, boxH/2f + tp.textSize/3f, tp)
        }
        return bmp
    }

    private fun makeClusterBitmap(count: Int, color: Int): Bitmap {
        val size = 72; val r = size / 2f
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or 0x44000000 })
            drawCircle(r, r, r * 0.70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; textSize = if (count < 100) 26f else 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
            }
            drawText(count.toString(), r, r - (tp.ascent() + tp.descent()) / 2f, tp)
        }
        return bmp
    }

    // ── Clustering ────────────────────────────────────────────────────────────

    private data class Cluster(val lat: Double, val lon: Double, val stops: List<BusStop>)

    private fun cluster(stops: List<BusStop>, zoom: Double): List<Cluster> {
        val radiusDeg = CLUSTER_RADIUS_PX / (156543.03392 * cos(Math.toRadians(43.3)) / 2.0.pow(zoom))
        val assigned  = BooleanArray(stops.size)
        val result    = mutableListOf<Cluster>()
        for (i in stops.indices) {
            if (assigned[i]) continue
            val group = mutableListOf(stops[i]); assigned[i] = true
            for (j in i + 1 until stops.size) {
                if (assigned[j]) continue
                val dl = stops[i].lat - stops[j].lat; val dn = stops[i].lon - stops[j].lon
                if (sqrt(dl*dl + dn*dn) < radiusDeg) { group.add(stops[j]); assigned[j] = true }
            }
            result.add(Cluster(group.sumOf { it.lat } / group.size, group.sumOf { it.lon } / group.size, group))
        }
        return result
    }

    private fun buildClusterMarker(c: Cluster): Marker {
        val color = stopColors(c.stops.first()).first()
        return Marker(map).apply {
            position = GeoPoint(c.lat, c.lon)
            title    = "${c.stops.size} arrêts"
            icon     = android.graphics.drawable.BitmapDrawable(resources, makeClusterBitmap(c.stops.size, color))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> map.controller.animateTo(GeoPoint(c.lat, c.lon)); map.controller.zoomIn(); true }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun zoomTo(lat: Double, lon: Double) {
        if (!::map.isInitialized) return
        map.controller.animateTo(GeoPoint(lat, lon), 18.0, 700L)
    }

    companion object {
        private const val ZOOM_DOTS         = 10.5
        private const val ZOOM_LABELS       = 15.0
        private const val CLUSTER_RADIUS_PX = 55.0
    }

    private fun normalizeTransitName(value: String): String {
        val trimmed = value.replace(Regex("\\s+"), " ").trim()
        val strippedQuai = trimmed.replace(Regex("\\s+quai\\s+[a-z0-9]+$", RegexOption.IGNORE_CASE), "")
        val normalized = Normalizer.normalize(strippedQuai, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}+"), "").lowercase()
    }
}

// ── LocationDotOverlay — point bleu dessiné directement sur le canvas ─────────

class LocationDotOverlay : Overlay() {
    var currentLat = Double.NaN; private set
    var currentLon = Double.NaN; private set
    private var lat = Double.NaN
    private var lon = Double.NaN
    private var visible = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2196F3.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x402196F3 }

    fun updateLocation(newLat: Double, newLon: Double) { lat = newLat; lon = newLon; currentLat = newLat; currentLon = newLon; visible = true }
    fun hide() { visible = false }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible || lat.isNaN()) return
        val pt = mapView.projection.toPixels(GeoPoint(lat, lon), null) ?: return
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 24f, haloPaint)
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 12f, fillPaint)
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 12f, borderPaint)
    }
}

// ── Overlay quais de l'arrêt le plus proche — dessin canvas direct ─────────────

class NearestStopOverlay : Overlay() {
    private var quais: List<GeoPoint> = emptyList()

    private val dotFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3.5f
    }

    fun set(name: String, positions: List<GeoPoint>) { quais = positions }
    fun clear() { quais = emptyList() }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || quais.isEmpty()) return
        quais.forEach { geo ->
            val pt = mapView.projection.toPixels(geo, null) ?: return@forEach
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            canvas.drawCircle(x, y, 18f, dotFill)
            canvas.drawCircle(x, y, 18f, dotBorder)
        }
    }
}
