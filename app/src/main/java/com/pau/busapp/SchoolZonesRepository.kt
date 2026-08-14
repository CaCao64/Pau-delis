package com.pau.busapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class SchoolHolidayPeriod(
    val description: String,
    val startDate: String,
    val endDate: String
)

data class SchoolZoneInfo(
    val zoneCode: String,
    val zoneLabel: String,
    val periods: List<SchoolHolidayPeriod>
)

data class SchoolZonesSnapshot(
    val schoolYear: String,
    val fetchedAt: Long,
    val zones: List<SchoolZoneInfo>,
    val fromCache: Boolean
)

object SchoolZonesRepository {
    private const val PREFS = "school_zones_cache"
    private const val KEY_SNAPSHOT = "snapshot_json"
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val zoneLabels = mapOf(
        "Zone A" to "Zone orange",
        "Zone B" to "Zone bleue",
        "Zone C" to "Zone grise"
    )

    private val zoneUrls = mapOf(
        "Zone A" to "https://fr.ftp.opendatasoft.com/openscol/fr-en-calendrier-scolaire/Zone-A.ics",
        "Zone B" to "https://fr.ftp.opendatasoft.com/openscol/fr-en-calendrier-scolaire/Zone-B.ics",
        "Zone C" to "https://fr.ftp.opendatasoft.com/openscol/fr-en-calendrier-scolaire/Zone-C.ics"
    )

    suspend fun load(context: Context): SchoolZonesSnapshot? =
        withContext(Dispatchers.IO) {
            val schoolYear = currentSchoolYear()
            val schoolStart = currentSchoolYearStart()
            val schoolEndExclusive = schoolStart.plusYears(1)
            val cached = readCache(context)

            val zones = coroutineScope {
                zoneUrls.entries.map { entry ->
                    async {
                        runCatching {
                            fetchZone(entry.key, entry.value, schoolStart, schoolEndExclusive)
                        }.getOrElse {
                            SchoolZoneInfo(
                                zoneCode = entry.key,
                                zoneLabel = zoneLabels.getValue(entry.key),
                                periods = emptyList()
                            )
                        }
                    }
                }.awaitAll().sortedBy { it.zoneCode }
            }

            val fetched = SchoolZonesSnapshot(
                schoolYear = schoolYear,
                fetchedAt = System.currentTimeMillis(),
                zones = zones,
                fromCache = false
            )

            if (zones.any { it.periods.isNotEmpty() }) {
                saveCache(context, fetched)
                return@withContext fetched
            }

            cached?.let { return@withContext it.copy(fromCache = true) }
            fetched
        }

    private fun fetchZone(
        zoneCode: String,
        url: String,
        schoolStart: LocalDate,
        schoolEndExclusive: LocalDate
    ): SchoolZoneInfo {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Pau-delis/1.0")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }

        val periods = parseIcsPeriods(body)
            .mapNotNull { period ->
                val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: return@mapNotNull null
                val endExclusive = runCatching { LocalDate.parse(period.endDate) }.getOrNull() ?: return@mapNotNull null
                val endInclusive = endExclusive.minusDays(1)
                if (start.isBefore(schoolEndExclusive) && endInclusive >= schoolStart) {
                    SchoolHolidayPeriod(
                        description = period.description,
                        startDate = start.format(DATE_FORMATTER),
                        endDate = endInclusive.format(DATE_FORMATTER)
                    )
                } else {
                    null
                }
            }
            .distinctBy { "${it.description}|${it.startDate}|${it.endDate}" }
            .sortedBy { it.startDate }

        return SchoolZoneInfo(
            zoneCode = zoneCode,
            zoneLabel = zoneLabels.getValue(zoneCode),
            periods = periods
        )
    }

    private fun parseIcsPeriods(raw: String): List<SchoolHolidayPeriod> {
        val lines = unfoldIcsLines(raw)
        val periods = mutableListOf<SchoolHolidayPeriod>()
        var summary = ""
        var description = ""
        var startDate = ""
        var endDate = ""
        var inEvent = false

        fun commitEvent() {
            if (summary.isBlank() || startDate.isBlank() || endDate.isBlank()) return
            periods.add(
                SchoolHolidayPeriod(
                    description = summary.ifBlank { description },
                    startDate = startDate,
                    endDate = endDate
                )
            )
        }

        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    summary = ""
                    description = ""
                    startDate = ""
                    endDate = ""
                }
                line == "END:VEVENT" -> {
                    if (inEvent) commitEvent()
                    inEvent = false
                }
                !inEvent -> Unit
                line.startsWith("SUMMARY:") -> summary = line.removePrefix("SUMMARY:").trim()
                line.startsWith("DESCRIPTION:") -> description = line.removePrefix("DESCRIPTION:").trim()
                line.startsWith("DTSTART") -> startDate = line.substringAfter(':').trim()
                line.startsWith("DTEND") -> endDate = line.substringAfter(':').trim()
            }
        }

        return periods
    }

    private fun unfoldIcsLines(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val unfolded = mutableListOf<String>()
        normalized.lineSequence().forEach { line ->
            if (line.startsWith(' ') || line.startsWith('\t')) {
                if (unfolded.isNotEmpty()) {
                    unfolded[unfolded.lastIndex] = unfolded.last() + line.trimStart()
                }
            } else {
                unfolded.add(line)
            }
        }
        return unfolded
    }

    private fun saveCache(context: Context, snapshot: SchoolZonesSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SNAPSHOT, snapshot.toJson().toString())
            .apply()
    }

    private fun readCache(context: Context): SchoolZonesSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { snapshotFromJson(JSONObject(raw)) }.getOrNull()
    }

    private fun currentSchoolYear(today: LocalDate = LocalDate.now()): String {
        val startYear = if (today.monthValue >= 7) today.year else today.year - 1
        return "$startYear-${startYear + 1}"
    }

    private fun currentSchoolYearStart(today: LocalDate = LocalDate.now()): LocalDate {
        val startYear = if (today.monthValue >= 7) today.year else today.year - 1
        return LocalDate.of(startYear, 7, 1)
    }

    private fun snapshotFromJson(obj: JSONObject): SchoolZonesSnapshot {
        val zones = mutableListOf<SchoolZoneInfo>()
        val zoneArray = obj.optJSONArray("zones") ?: JSONArray()
        for (i in 0 until zoneArray.length()) {
            val zoneObj = zoneArray.optJSONObject(i) ?: continue
            val periods = mutableListOf<SchoolHolidayPeriod>()
            val periodArray = zoneObj.optJSONArray("periods") ?: JSONArray()
            for (j in 0 until periodArray.length()) {
                val periodObj = periodArray.optJSONObject(j) ?: continue
                periods.add(
                    SchoolHolidayPeriod(
                        description = periodObj.optString("description"),
                        startDate = periodObj.optString("startDate"),
                        endDate = periodObj.optString("endDate")
                    )
                )
            }
            zones.add(
                SchoolZoneInfo(
                    zoneCode = zoneObj.optString("zoneCode"),
                    zoneLabel = zoneObj.optString("zoneLabel"),
                    periods = periods
                )
            )
        }

        return SchoolZonesSnapshot(
            schoolYear = obj.optString("schoolYear"),
            fetchedAt = obj.optLong("fetchedAt"),
            zones = zones,
            fromCache = true
        )
    }

    private fun SchoolZonesSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("schoolYear", schoolYear)
        put("fetchedAt", fetchedAt)
        put("zones", JSONArray(zones.map { zone ->
            JSONObject().apply {
                put("zoneCode", zone.zoneCode)
                put("zoneLabel", zone.zoneLabel)
                put("periods", JSONArray(zone.periods.map { period ->
                    JSONObject().apply {
                        put("description", period.description)
                        put("startDate", period.startDate)
                        put("endDate", period.endDate)
                    }
                }))
            }
        }))
    }

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
