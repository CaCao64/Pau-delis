package com.pau.busapp

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class TrafficNotice(
    val title: String,
    val summary: String,
    val lineCodes: Set<String>,
    val sourceUrl: String
)

object TrafficNoticesRepository {

    private const val URL = "https://www.idelis.fr/decouvrir-idelis/actualites/informations-travaux"
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedAt = 0L
    @Volatile private var cachedNotices: List<TrafficNotice> = emptyList()

    suspend fun loadNotices(forceRefresh: Boolean = false): List<TrafficNotice> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedNotices.isNotEmpty() && now - cachedAt < 10 * 60 * 1000L) {
            return@withContext cachedNotices
        }
        val fetched = runCatching { fetchNotices() }.getOrElse { emptyList() }
        cachedAt = now
        cachedNotices = fetched
        fetched
    }

    suspend fun loadNoticesForLine(lineNumber: String): List<TrafficNotice> {
        val key = lineSlug(lineNumber)
        return loadNotices().filter { notice ->
            key in notice.lineCodes || notice.lineCodes.isEmpty()
        }
    }

    suspend fun loadPrimaryNoticeForLine(lineNumber: String): TrafficNotice? =
        loadNoticesForLine(lineNumber).firstOrNull()

    suspend fun loadPrimaryNotice(): TrafficNotice? =
        loadNotices().firstOrNull()

    private fun fetchNotices(): List<TrafficNotice> {
        val req = Request.Builder()
            .url(URL)
            .header("User-Agent", "Pau-delis/1.0")
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) return emptyList()
            return parseNotices(html)
        }
    }

    private fun parseNotices(html: String): List<TrafficNotice> {
        val linkRegex = Regex("""href="([^"]*/informations-travaux/([^"#?]+))"""", RegexOption.IGNORE_CASE)
        val notices = mutableListOf<TrafficNotice>()

        for (match in linkRegex.findAll(html)) {
            val url = match.groupValues[1]
            val slug = match.groupValues[2]
            val windowStart = (match.range.first - 1600).coerceAtLeast(0)
            val windowEnd = (match.range.last + 1800).coerceAtMost(html.length)
            val window = html.substring(windowStart, windowEnd)
            val title = extractHeading(window)
            val summary = extractSummary(window)
            val lineCodes = extractLineCodes(window).ifEmpty { slugToLineCodes(slug) }
            val cleanSummary = summary.ifBlank { title }
            if (cleanSummary.isNotBlank()) {
                notices.add(
                    TrafficNotice(
                        title = title.ifBlank { "Info trafic" },
                        summary = cleanSummary,
                        lineCodes = lineCodes,
                        sourceUrl = if (url.startsWith("http")) url else "https://www.idelis.fr$url"
                    )
                )
            }
        }

        return notices
            .distinctBy { "${it.title}|${it.summary}|${it.lineCodes.sorted()}" }
            .sortedByDescending { it.lineCodes.size }
    }

    private fun extractHeading(window: String): String {
        val headingRegex = Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.DOT_MATCHES_ALL)
        return headingRegex.findAll(window).lastOrNull()
            ?.groupValues?.getOrNull(1)
            .orEmpty()
            .toPlainText()
            .take(120)
    }

    private fun extractSummary(window: String): String {
        val paragraphRegex = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val paragraphs = paragraphRegex.findAll(window)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.toPlainText() }
            .filter { it.isNotBlank() }
            .toList()

        if (paragraphs.isEmpty()) {
            return window.toPlainText().take(240)
        }
        return paragraphs.take(3).joinToString(" ").replace(Regex("\\s+"), " ").trim().take(260)
    }

    private fun extractLineCodes(window: String): Set<String> {
        val codeRegex = Regex("""/informations-travaux/([^"#?]+)""", RegexOption.IGNORE_CASE)
        return codeRegex.findAll(window)
            .flatMap { slugToLineCodes(it.groupValues[1]).asSequence() }
            .toSet()
    }

    private fun slugToLineCodes(slug: String): Set<String> {
        val normalized = slug.lowercase()
            .removePrefix("ligne-")
            .removePrefix("lignes-")
            .replace(Regex("[^a-z0-9-]"), "")
        if (normalized.isBlank()) return emptySet()
        return normalized.split("-")
            .mapNotNull { part ->
                val token = part.trim()
                if (token.isBlank()) null else token.uppercase()
            }
            .toSet()
    }

    private fun lineSlug(lineNumber: String): String =
        lineNumber.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun String.toPlainText(): String =
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ")
            .trim()
}
