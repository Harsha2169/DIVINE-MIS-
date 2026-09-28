package com.example.core

/**
 * Canonical Data State contract for Divine Stamp Manufacturing MIS.
 *
 * Rules:
 * - VALUE: Data exists and is authoritative. Note: 0 is a valid VALUE.
 * - NO_DATA: No authoritative record exists.
 * - 0 != NO_DATA. Never convert NO_DATA into 0. Never fabricate missing data.
 */
enum class DataState {
    VALUE,
    NO_DATA
}
