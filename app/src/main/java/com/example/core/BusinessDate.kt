package com.example.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * Authoritative Canonical Business Date.
 * Format: strictly YYYY-MM-DD.
 * Timezone-safe.
 */
class BusinessDate private constructor(val rawValue: String) : Comparable<BusinessDate> {

    val localDate: LocalDate = try {
        LocalDate.parse(rawValue, STRICT_FORMATTER)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid calendar date format or value: '$rawValue'. Expected YYYY-MM-DD", e)
    }

    val year: Int get() = localDate.year
    val month: Int get() = localDate.monthValue
    val day: Int get() = localDate.dayOfMonth

    fun nextDay(): BusinessDate = of(localDate.plusDays(1))

    fun previousDay(): BusinessDate = of(localDate.minusDays(1))

    override fun compareTo(other: BusinessDate): Int = this.localDate.compareTo(other.localDate)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BusinessDate) return false
        return rawValue == other.rawValue
    }

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = rawValue

    companion object {
        private val PATTERN_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
        val STRICT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT)
        val DEFAULT_ZONE_ID: ZoneId = ZoneId.of("Asia/Kolkata")

        fun of(year: Int, month: Int, day: Int): BusinessDate {
            val ld = LocalDate.of(year, month, day)
            return BusinessDate(ld.format(STRICT_FORMATTER))
        }

        fun of(localDate: LocalDate): BusinessDate {
            return BusinessDate(localDate.format(STRICT_FORMATTER))
        }

        fun parse(dateString: String): BusinessDate {
            val trimmed = dateString.trim()
            require(PATTERN_REGEX.matches(trimmed)) {
                "Invalid date pattern: '$dateString'. Canonical format must strictly be YYYY-MM-DD."
            }
            return BusinessDate(trimmed)
        }

        fun parseOrNull(dateString: String?): BusinessDate? {
            if (dateString.isNullOrBlank()) return null
            return try {
                parse(dateString)
            } catch (e: Exception) {
                null
            }
        }

        fun today(zoneId: ZoneId = DEFAULT_ZONE_ID): BusinessDate {
            return of(LocalDate.now(zoneId))
        }

        /**
         * Generates an uninterrupted, chronological sequence of every date
         * between [from] and [to] inclusive. Never skips any day.
         */
        fun generateSequence(from: BusinessDate, to: BusinessDate): List<BusinessDate> {
            require(from <= to) { "from date ($from) cannot be after to date ($to)" }
            val list = mutableListOf<BusinessDate>()
            var curr = from.localDate
            val end = to.localDate
            while (!curr.isAfter(end)) {
                list.add(of(curr))
                curr = curr.plusDays(1)
            }
            return list
        }
    }
}
