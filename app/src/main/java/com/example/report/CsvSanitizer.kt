package com.example.report

/**
 * Utility for sanitizing CSV exports against CSV Formula Injection (CWE-1236).
 *
 * Invariant: Any cell starting with formula triggers (=, +, -, @, tab, CR)
 * must be safely escaped by prepending a single quote (') to prevent
 * spreadsheet applications from executing formulas.
 */
object CsvSanitizer {

    private val FORMULA_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Sanitizes a single cell value against formula injection.
     */
    fun sanitizeValue(value: String): String {
        if (value.isEmpty()) return ""
        val startsWithFormula = FORMULA_CHARS.any { value.startsWith(it) }
        return if (startsWithFormula) {
            "'$value"
        } else {
            value
        }
    }

    /**
     * Sanitizes a cell and escapes it according to RFC 4180 standard CSV rules.
     * Encloses in quotes if it contains commas, double quotes, newlines, or formula escape quotes.
     */
    fun escapeCsvField(value: String): String {
        val sanitized = sanitizeValue(value)
        val needsQuotes = sanitized.contains(',') ||
                sanitized.contains('"') ||
                sanitized.contains('\n') ||
                sanitized.contains('\r') ||
                sanitized.startsWith('\'')

        return if (needsQuotes) {
            "\"${sanitized.replace("\"", "\"\"")}\""
        } else {
            sanitized
        }
    }

    /**
     * Formats a row of cells into a valid CSV line.
     */
    fun formatRow(cells: List<String>): String {
        return cells.joinToString(",") { escapeCsvField(it) }
    }
}
