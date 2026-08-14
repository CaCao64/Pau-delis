package com.pau.busapp

import java.text.Normalizer
import java.util.Locale

object SearchTextUtils {
    fun normalize(value: String): String {
        if (value.isBlank()) return ""

        val expanded = value
            .replace("\u0153", "oe", ignoreCase = true)
            .replace("\u00E6", "ae", ignoreCase = true)
            .replace("\u00DF", "ss", ignoreCase = true)

        val normalized = Normalizer.normalize(expanded, Normalizer.Form.NFKD)
        val withoutMarks = normalized.replace(Regex("\\p{M}+"), "")
        val cleaned = withoutMarks
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
        return cleaned.replace(Regex("\\s+"), " ")
    }
}
