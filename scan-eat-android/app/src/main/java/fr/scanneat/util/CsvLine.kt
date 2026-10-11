package fr.scanneat.util

/**
 * Minimal RFC4180-style single-line CSV parser: handles double-quoted fields
 * (needed because drug names routinely contain commas, e.g. "X 500 mg,
 * comprimé"), with "" as the escaped-quote sequence inside a quoted field.
 * No embedded-newline support - the generating exports never produce one.
 */
internal fun parseCsvLine(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}
