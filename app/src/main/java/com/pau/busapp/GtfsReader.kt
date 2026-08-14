package com.pau.busapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

object GtfsReader {

    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val WEEKDAYS = listOf(
        "monday", "tuesday", "wednesday", "thursday",
        "friday", "saturday", "sunday"
    )

    private data class StopTimeEntry(val tripId: String, val stopId: String, val departure: String)
    private data class TripEntry(
        val routeId: String,
        val serviceId: String,
        val shapeId: String,
        val directionId: String
    )
    private data class ShapePoint(val lat: Double, val lon: Double, val sequence: Int)

    private val mutex = Mutex()
    private var zipLoaded = false
    private var cacheRoutes: Map<String, String> = emptyMap()
    private var cacheRouteIdsByShortName: Map<String, List<String>> = emptyMap()
    private var cacheTrips: Map<String, TripEntry> = emptyMap()
    private var cacheHeadsigns: Map<String, String> = emptyMap()
    private var cacheStopTimes: List<StopTimeEntry> = emptyList()
    private var cacheStopIndex: Map<String, List<StopTimeEntry>> = emptyMap()
    private var cacheShapes: Map<String, List<ShapePoint>> = emptyMap()
    private var cacheCalendar: ByteArray = byteArrayOf()
    private var cacheCalendarDates: ByteArray = byteArrayOf()
    private var cacheServicesDate: LocalDate? = null
    private var cacheServices: Set<String> = emptySet()

    private suspend fun ensureCache(ctx: Context, forDate: LocalDate? = null) = mutex.withLock {
        if (!zipLoaded) {
            val files = mutableMapOf<String, ByteArray>()
            val needed = setOf(
                "routes.txt", "trips.txt", "calendar.txt",
                "calendar_dates.txt", "stop_times.txt", "shapes.txt"
            )
            ZipInputStream(ctx.assets.open("gtfs.zip")).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name in needed) {
                        val buf = ByteArrayOutputStream()
                        zip.copyTo(buf)
                        files[entry.name] = buf.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            cacheRoutes = parseRoutes(files["routes.txt"] ?: byteArrayOf())
            cacheRouteIdsByShortName = cacheRoutes.entries.groupBy({ it.value }, { it.key })
            val (trips, headsigns) = parseTrips(files["trips.txt"] ?: byteArrayOf())
            cacheTrips = trips
            cacheHeadsigns = headsigns
            cacheStopTimes = parseStopTimesRaw(files["stop_times.txt"] ?: byteArrayOf())
            cacheStopIndex = cacheStopTimes.groupBy { it.stopId }
            cacheShapes = parseShapes(files["shapes.txt"] ?: byteArrayOf())
            cacheCalendar = files["calendar.txt"] ?: byteArrayOf()
            cacheCalendarDates = files["calendar_dates.txt"] ?: byteArrayOf()
            zipLoaded = true
        }

        val targetDate = forDate ?: LocalDate.now()
        if (cacheServicesDate != targetDate) {
            val services = parseCalendar(cacheCalendar, targetDate)
            val (added, removed) = parseCalendarDates(cacheCalendarDates, targetDate)
            cacheServices = services + added - removed
            cacheServicesDate = targetDate
        }
    }

    suspend fun getTheoreticalPassages(
        ctx: Context,
        stopIds: List<String>,
        atTime: LocalTime? = null,
        atDate: LocalDate? = null
    ): List<StopInfo> =
        withContext(Dispatchers.IO) {
            ensureCache(ctx, atDate)
            val now = atTime ?: LocalTime.now()

            if (cacheServices.isEmpty() || cacheStopTimes.isEmpty()) return@withContext emptyList()

            val passages = lookupStopTimes(stopIds, now)

            passages
                .groupBy { Pair(it.first, it.second) }
                .map { (key, list) ->
                    val sorted = list.sortedBy { it.third }
                    StopInfo(
                        ligne = key.first,
                        destination = key.second,
                        pmr = false,
                        passages = sorted.take(5).map {
                            Passage(
                                arrivee = it.third.format(DateTimeFormatter.ofPattern("HH:mm")),
                                type = "theorique"
                            )
                        }
                    )
                }
                .sortedWith(compareBy({ lineSortKey(it.ligne) }, { it.destination }))
        }

    suspend fun getLineShapes(ctx: Context, lineNumber: String): List<List<GeoPoint>> =
        withContext(Dispatchers.IO) {
            ensureCache(ctx)
            val routeIds = cacheRouteIdsByShortName[lineNumber].orEmpty()
            if (routeIds.isEmpty() || cacheShapes.isEmpty()) return@withContext emptyList()

            val shapes = mutableListOf<List<GeoPoint>>()
            routeIds.forEach { routeId ->
                val trips = cacheTrips.values.filter { it.routeId == routeId && it.shapeId.isNotBlank() }
                if (trips.isEmpty()) return@forEach

                trips.groupBy { it.directionId.ifBlank { "?" } }.values.forEach { directionTrips ->
                    val chosenShape = directionTrips
                        .groupingBy { it.shapeId }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                        ?: return@forEach

                    val points = cacheShapes[chosenShape]
                        ?.map { GeoPoint(it.lat, it.lon) }
                        ?.takeIf { it.size >= 2 }
                        ?: return@forEach
                    shapes.add(points)
                }
            }

            shapes.distinctBy { points ->
                points.joinToString("|") { "${it.latitude},${it.longitude}" }
            }
        }

    // ── Parsers ────────────────────────────────────────────────────────────────

    private fun parseRoutes(data: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        reader(data).use { r ->
            val headers = r.readLine()?.split(",") ?: return map
            val idIdx = headers.indexOf("route_id")
            val nameIdx = headers.indexOf("route_short_name")
            var line = r.readLine()
            while (line != null) {
                val cols = splitCsvLine(line)
                if (idIdx < cols.size && nameIdx < cols.size) {
                    map[cols[idIdx]] = cols[nameIdx]
                }
                line = r.readLine()
            }
        }
        return map
    }

    private fun parseTrips(data: ByteArray): Pair<Map<String, TripEntry>, Map<String, String>> {
        val trips = mutableMapOf<String, TripEntry>()
        val headsigns = mutableMapOf<String, String>()
        reader(data).use { r ->
            val headers = r.readLine()?.split(",") ?: return Pair(trips, headsigns)
            val routeIdx = headers.indexOf("route_id")
            val svcIdx = headers.indexOf("service_id")
            val tripIdx = headers.indexOf("trip_id")
            val headIdx = headers.indexOf("trip_headsign")
            val shapeIdx = headers.indexOf("shape_id")
            val dirIdx = headers.indexOf("direction_id")
            var line = r.readLine()
            while (line != null) {
                val cols = splitCsvLine(line)
                if (tripIdx < cols.size) {
                    val tripId = cols[tripIdx]
                    if (routeIdx < cols.size && svcIdx < cols.size) {
                        trips[tripId] = TripEntry(
                            routeId = cols[routeIdx],
                            serviceId = cols[svcIdx],
                            shapeId = if (shapeIdx < cols.size) cols[shapeIdx] else "",
                            directionId = if (dirIdx < cols.size) cols[dirIdx] else ""
                        )
                    }
                    if (headIdx < cols.size) {
                        headsigns[tripId] = cols[headIdx]
                    }
                }
                line = r.readLine()
            }
        }
        return Pair(trips, headsigns)
    }

    private fun parseShapes(data: ByteArray): Map<String, List<ShapePoint>> {
        val shapes = mutableMapOf<String, MutableList<ShapePoint>>()
        reader(data).use { r ->
            val headers = r.readLine()?.split(",") ?: return emptyMap()
            val shapeIdx = headers.indexOf("shape_id")
            val latIdx = headers.indexOf("shape_pt_lat")
            val lonIdx = headers.indexOf("shape_pt_lon")
            val seqIdx = headers.indexOf("shape_pt_sequence")
            var line = r.readLine()
            while (line != null) {
                val cols = splitCsvLine(line)
                if (shapeIdx < cols.size && latIdx < cols.size && lonIdx < cols.size && seqIdx < cols.size) {
                    val shapeId = cols[shapeIdx]
                    val lat = cols[latIdx].toDoubleOrNull()
                    val lon = cols[lonIdx].toDoubleOrNull()
                    val seq = cols[seqIdx].toIntOrNull()
                    if (lat != null && lon != null && seq != null) {
                        shapes.getOrPut(shapeId) { mutableListOf() }.add(ShapePoint(lat, lon, seq))
                    }
                }
                line = r.readLine()
            }
        }
        return shapes.mapValues { (_, points) -> points.sortedBy { it.sequence } }
    }

    private fun parseCalendar(data: ByteArray, today: LocalDate): Set<String> {
        val active = mutableSetOf<String>()
        val weekday = WEEKDAYS[today.dayOfWeek.value - 1]
        val ymd = today.format(dateFmt)
        reader(data).use { r ->
            val headers = r.readLine()?.split(",") ?: return active
            val svcIdx = headers.indexOf("service_id")
            val dayIdx = headers.indexOf(weekday)
            val startIdx = headers.indexOf("start_date")
            val endIdx = headers.indexOf("end_date")
            var line = r.readLine()
            while (line != null) {
                val cols = splitCsvLine(line)
                if (svcIdx < cols.size && dayIdx < cols.size &&
                    startIdx < cols.size && endIdx < cols.size) {
                    if (cols[dayIdx] == "1" && cols[startIdx] <= ymd && ymd <= cols[endIdx]) {
                        active.add(cols[svcIdx])
                    }
                }
                line = r.readLine()
            }
        }
        return active
    }

    private fun parseCalendarDates(data: ByteArray, today: LocalDate): Pair<Set<String>, Set<String>> {
        val added = mutableSetOf<String>()
        val removed = mutableSetOf<String>()
        val ymd = today.format(dateFmt)
        reader(data).use { r ->
            val headers = r.readLine()?.split(",") ?: return Pair(added, removed)
            val svcIdx = headers.indexOf("service_id")
            val dateIdx = headers.indexOf("date")
            val typeIdx = headers.indexOf("exception_type")
            var line = r.readLine()
            while (line != null) {
                val cols = splitCsvLine(line)
                if (dateIdx < cols.size && cols[dateIdx] == ymd &&
                    svcIdx < cols.size && typeIdx < cols.size) {
                    when (cols[typeIdx]) {
                        "1" -> added.add(cols[svcIdx])
                        "2" -> removed.add(cols[svcIdx])
                    }
                }
                line = r.readLine()
            }
        }
        return Pair(added, removed)
    }

    private fun parseStopTimesRaw(data: ByteArray): List<StopTimeEntry> {
        val result = mutableListOf<StopTimeEntry>()
        reader(data).use { r ->
            val headers = r.readLine()?.split(",") ?: return result
            val tripIdx = headers.indexOf("trip_id")
            val stopIdx = headers.indexOf("stop_id")
            val depIdx = headers.indexOf("departure_time")
            var line = r.readLine()
            while (line != null) {
                val cols = splitCsvLine(line)
                if (tripIdx < cols.size && stopIdx < cols.size && depIdx < cols.size) {
                    result.add(StopTimeEntry(cols[tripIdx], cols[stopIdx], cols[depIdx]))
                }
                line = r.readLine()
            }
        }
        return result
    }

    private fun lookupStopTimes(
        stopIds: List<String>,
        now: LocalTime
    ): List<Triple<String, String, LocalTime>> {
        val result = mutableListOf<Triple<String, String, LocalTime>>()
        for (stopId in stopIds) {
            val entries = cacheStopIndex[stopId] ?: continue
            for (e in entries) {
                val tripInfo = cacheTrips[e.tripId] ?: continue
                if (tripInfo.serviceId !in cacheServices) continue
                val time = parseGtfsTime(e.departure) ?: continue
                if (time.isBefore(now)) continue
                val ligne = cacheRoutes[tripInfo.routeId] ?: tripInfo.routeId
                val dest = cacheHeadsigns[e.tripId] ?: ""
                result.add(Triple(ligne, dest, time))
            }
        }
        return result
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun parseGtfsTime(s: String): LocalTime? {
        val parts = s.trim().split(":")
        if (parts.size != 3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val sec = parts[2].toIntOrNull() ?: return null
        if (h >= 24) return null
        return LocalTime.of(h, m, sec)
    }

    private fun reader(data: ByteArray) =
        BufferedReader(InputStreamReader(ByteArrayInputStream(data), Charsets.UTF_8))

    private fun lineSortKey(ligne: String): Int {
        val order = listOf(
            "F", "T1", "T2", "T3", "T4",
            "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "16", "17",
            "A", "B", "C", "D", "COXI", "EMMA"
        )
        val idx = order.indexOf(ligne)
        return if (idx == -1) 999 else idx
    }
}
