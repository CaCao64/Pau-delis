package com.pau.busapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SchedulesCacheManager {
    private const val PREFS = "schedules_realtime_cache"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    fun saveCache(ctx: Context, stopCode: String, stopInfos: List<StopInfo>) {
        purgeExpiredCaches(ctx)
        val o = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            val arr = JSONArray()
            stopInfos.forEach { info ->
                val infoObj = JSONObject().apply {
                    put("ligne", info.ligne)
                    put("destination", info.destination)
                    put("pmr", info.pmr)
                    put("quaiCode", info.quaiCode)
                    val passArr = JSONArray()
                    info.passages.forEach { p ->
                        passArr.put(JSONObject().apply {
                            put("arrivee", p.arrivee)
                            put("type", p.type)
                            put("premier", p.premier)
                            put("dernier", p.dernier)
                            put("statut", p.statut.name)
                            put("ecartMin", p.ecartMin)
                        })
                    }
                    put("passages", passArr)
                }
                arr.put(infoObj)
            }
            put("infos", arr)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(stopCode, o.toString())
            .apply()
    }

    fun getCache(ctx: Context, stopCode: String): Pair<Long, List<StopInfo>>? {
        purgeExpiredCaches(ctx)
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(stopCode, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val ts = o.getLong("timestamp")
            if (System.currentTimeMillis() - ts > CACHE_TTL_MS) {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(stopCode)
                    .apply()
                return null
            }
            val arr = o.getJSONArray("infos")
            val list = mutableListOf<StopInfo>()
            for (i in 0 until arr.length()) {
                val infoObj = arr.getJSONObject(i)
                val passages = mutableListOf<Passage>()
                val passArr = infoObj.getJSONArray("passages")
                for (j in 0 until passArr.length()) {
                    val pObj = passArr.getJSONObject(j)
                    passages.add(Passage(
                        arrivee = pObj.getString("arrivee"),
                        type = pObj.getString("type"),
                        premier = pObj.optBoolean("premier", false),
                        dernier = pObj.optBoolean("dernier", false),
                        statut = PassageStatut.valueOf(pObj.optString("statut", "THEORIQUE")),
                        ecartMin = pObj.optInt("ecartMin", 0)
                    ))
                }
                list.add(StopInfo(
                    ligne = infoObj.getString("ligne"),
                    destination = infoObj.getString("destination"),
                    pmr = infoObj.optBoolean("pmr", false),
                    passages = passages,
                    quaiCode = infoObj.optString("quaiCode", "")
                ))
            }
            Pair(ts, list)
        } catch (e: Exception) {
            null
        }
    }

    fun purgeExpiredCaches(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val staleKeys = prefs.all.mapNotNull { (key, value) ->
            val raw = value as? String ?: return@mapNotNull null
            val ts = runCatching { JSONObject(raw).getLong("timestamp") }.getOrNull() ?: return@mapNotNull key
            if (now - ts > CACHE_TTL_MS) key else null
        }
        if (staleKeys.isNotEmpty()) {
            prefs.edit().apply {
                staleKeys.forEach { remove(it) }
            }.apply()
        }
    }
}
