package com.pau.busapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.net.ssl.SSLSocketFactory

enum class PassageStatut { THEORIQUE, A_LHEURE, RETARD, AVANCE, ANNULE }

data class Passage(
    val arrivee: String,
    val type: String,
    val premier: Boolean = false,
    val dernier: Boolean = false,
    val statut: PassageStatut = if (type == "reel") PassageStatut.A_LHEURE else PassageStatut.THEORIQUE,
    val ecartMin: Int = 0
)

data class StopInfo(
    val ligne: String,
    val destination: String,
    val pmr: Boolean,
    val passages: List<Passage>,
    val quaiCode: String = ""
)

object IdelisApi {
    private val API_KEY get() = BuildConfig.IDELIS_API_KEY
    private const val HOST = "api.idelis.fr"
    private const val PATH = "/GetStopMonitoring"

    suspend fun getStopMonitoring(stopCode: String, next: Int = 5): List<StopInfo> =
        withContext(Dispatchers.IO) {
            val body = """{"code":"$stopCode","next":$next}""".toByteArray(Charsets.UTF_8)
            android.util.Log.d("IdelisApi", "GET https://$HOST$PATH body=" + String(body))

            val request = buildString {
                append("GET $PATH HTTP/1.1\r\n")
                append("Host: $HOST\r\n")
                append("X-AUTH-TOKEN: $API_KEY\r\n")
                append("Content-Type: application/json\r\n")
                append("Accept: application/json\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(Charsets.UTF_8)

            val socket = SSLSocketFactory.getDefault().createSocket(HOST, 443)
            socket.soTimeout = 10_000
            val raw = socket.use { s ->
                s.outputStream.write(request)
                s.outputStream.write(body)
                s.outputStream.flush()
                s.inputStream.bufferedReader(Charsets.UTF_8).readText()
            }

            // Sépare headers et body HTTP/1.1
            val separator = raw.indexOf("\r\n\r\n")
            val headers = if (separator >= 0) raw.substring(0, separator) else ""
            var responseBody = if (separator >= 0) raw.substring(separator + 4) else raw

            val statusCode = headers.lineSequence().firstOrNull()
                ?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 0

            val chunked = headers.lines().any {
                it.trim().lowercase().startsWith("transfer-encoding") && it.contains("chunked", ignoreCase = true)
            }
            if (chunked) responseBody = dechunk(responseBody)

            android.util.Log.d("IdelisApi", "HTTP $statusCode — ${responseBody.take(200)}")
            if (statusCode !in 200..299) throw RuntimeException("HTTP $statusCode")
            if (!responseBody.trimStart().startsWith("{") && !responseBody.trimStart().startsWith("["))
                return@withContext emptyList()
            parse(responseBody, stopCode)
        }

    private fun dechunk(body: String): String {
        val sb = StringBuilder()
        var pos = 0
        while (pos < body.length) {
            val lineEnd = body.indexOf("\r\n", pos)
            if (lineEnd < 0) break
            val sizeLine = body.substring(pos, lineEnd).trim()
            val chunkSize = sizeLine.substringBefore(";").trim().toIntOrNull(16) ?: break
            if (chunkSize == 0) break
            pos = lineEnd + 2
            if (pos + chunkSize > body.length) {
                sb.append(body.substring(pos))
                break
            }
            sb.append(body.substring(pos, pos + chunkSize))
            pos += chunkSize + 2  // skip trailing \r\n
        }
        return sb.toString()
    }

    private fun parse(raw: String, quaiCode: String = ""): List<StopInfo> {
        val root = JSONObject(raw)
        val result = mutableListOf<StopInfo>()
        for (key in root.keys()) {
            val obj = root.optJSONObject(key) ?: continue
            val ligne = obj.optString("ligne")
            val destination = obj.optString("destination")
            val pmr = obj.optBoolean("pmr", false)
            val passagesArr = obj.optJSONArray("passages")
            val rawPassages = mutableListOf<JSONObject>()
            if (passagesArr != null) {
                for (i in 0 until passagesArr.length()) {
                    passagesArr.optJSONObject(i)?.let(rawPassages::add)
                }
            }
            val theoreticalMinutes = rawPassages
                .filter { it.optString("type") == "theorique" }
                .mapNotNull { PassageHelper.parseArrivee(it.optString("arrivee")) }
                .map { it.hour * 60 + it.minute }

            val passages = rawPassages.map { p ->
                val base = Passage(
                    arrivee = p.optString("arrivee"),
                    type = p.optString("type"),
                    premier = p.optBoolean("premier", false),
                    dernier = p.optBoolean("dernier", false)
                )
                val ecart = PassageHelper.computeEcart(base, theoreticalMinutes.ifEmpty { null })
                val statut = if (base.type == "reel") PassageHelper.toStatut(ecart) else PassageStatut.THEORIQUE
                base.copy(statut = statut, ecartMin = ecart ?: 0)
            }
            result.add(StopInfo(ligne, destination, pmr, passages, quaiCode))
        }
        return result
    }
}
