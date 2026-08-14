package com.pau.busapp

internal fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuote = false
    val current = StringBuilder()
    for (ch in line) {
        when {
            ch == '"' -> inQuote = !inQuote
            ch == ',' && !inQuote -> {
                result.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    result.add(current.toString())
    return result
}
