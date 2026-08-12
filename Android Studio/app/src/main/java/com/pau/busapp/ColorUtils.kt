package com.pau.busapp

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

fun contrastTextColor(background: Int, context: Context? = null): Int {
    val r = Color.red(background) / 255.0
    val g = Color.green(background) / 255.0
    val b = Color.blue(background) / 255.0
    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return if (luminance > 0.35) Color.BLACK else Color.WHITE
}

fun statusColor(statut: PassageStatut, ctx: Context): Int = when (statut) {
    PassageStatut.A_LHEURE  -> 0xFF2E7D32.toInt()
    PassageStatut.RETARD    -> 0xFFE65100.toInt()
    PassageStatut.AVANCE    -> 0xFF1565C0.toInt()
    PassageStatut.ANNULE    -> 0xFFC62828.toInt()
    PassageStatut.THEORIQUE -> 0xFF888888.toInt()
}

fun statusHexColor(statut: PassageStatut, ctx: Context): String =
    "#%06X".format(statusColor(statut, ctx) and 0xFFFFFF)
