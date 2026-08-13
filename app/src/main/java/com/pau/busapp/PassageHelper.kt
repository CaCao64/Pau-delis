package com.pau.busapp

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object PassageHelper {
    private const val MINUTES_PER_DAY = 24 * 60

    // Parse "3 min", "Imminent", "14:32" → LocalTime (heure estimée d'arrivée)
    fun parseArrivee(arrivee: String): LocalTime? {
        val s = arrivee.trim().lowercase()
        return when {
            s == "imminent" || s == "à l'arrêt" -> LocalTime.now()
            s.matches(Regex("\\d+\\s*min")) -> {
                val min = s.filter { it.isDigit() }.toIntOrNull() ?: return null
                LocalTime.now().plusMinutes(min.toLong())
            }
            s.matches(Regex("\\d{1,2}:\\d{2}")) -> {
                val parts = s.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            }
            else -> null
        }
    }

    // Calcule l'écart en minutes entre l'heure temps réel et l'horaire théorique GTFS
    // Retourne null si on ne peut pas calculer
    fun computeEcart(passage: Passage, theoreticalMinutes: List<Int>?): Int? {
        if (passage.type != "reel") return null
        if (theoreticalMinutes.isNullOrEmpty()) return null
        val realTime = parseArrivee(passage.arrivee) ?: return null
        val realMin  = realTime.hour * 60 + realTime.minute
        return computeEcartMinutes(realMin, theoreticalMinutes)
    }

    internal fun computeEcartMinutes(realMinutes: Int, theoreticalMinutes: List<Int>): Int? {
        if (theoreticalMinutes.isEmpty()) return null

        // On compare aussi avec le jour précédent / suivant pour éviter les faux statuts
        // quand le passage est proche de minuit.
        val closest = theoreticalMinutes.minByOrNull { circularDistance(realMinutes, it) } ?: return null
        val delta = circularSignedDelta(realMinutes, closest)
        if (kotlin.math.abs(delta) > 45) return null
        return delta  // positif = retard, négatif = avance
    }

    fun toStatut(ecart: Int?): PassageStatut = when {
        ecart == null       -> PassageStatut.A_LHEURE
        ecart > 2           -> PassageStatut.RETARD
        ecart < -1          -> PassageStatut.AVANCE
        else                -> PassageStatut.A_LHEURE
    }

    private fun circularSignedDelta(realMinutes: Int, theoreticalMinutes: Int): Int {
        val base = realMinutes - theoreticalMinutes
        val candidates = listOf(base, base + MINUTES_PER_DAY, base - MINUTES_PER_DAY)
        return candidates.minByOrNull { kotlin.math.abs(it) } ?: base
    }

    private fun circularDistance(realMinutes: Int, theoreticalMinutes: Int): Int =
        kotlin.math.abs(circularSignedDelta(realMinutes, theoreticalMinutes))
}
