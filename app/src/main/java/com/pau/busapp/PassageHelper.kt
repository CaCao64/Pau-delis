package com.pau.busapp

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object PassageHelper {

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

        // Trouver le passage théorique le plus proche (dans une fenêtre de ±30 min)
        val closest = theoreticalMinutes.minByOrNull { Math.abs(it - realMin) } ?: return null
        if (Math.abs(closest - realMin) > 30) return null
        return realMin - closest  // positif = retard, négatif = avance
    }

    fun toStatut(ecart: Int?): PassageStatut = when {
        ecart == null       -> PassageStatut.A_LHEURE
        ecart > 2           -> PassageStatut.RETARD
        ecart < -1          -> PassageStatut.AVANCE
        else                -> PassageStatut.A_LHEURE
    }
}
