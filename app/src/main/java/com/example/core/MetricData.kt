package com.example.core

/**
 * Canonical wrapper for metrics in the analytics and reporting layer.
 *
 * Enforces the core invariant:
 * - 0 is a valid VALUE.
 * - NO_DATA means absence of authoritative data.
 * - 0 != NO_DATA.
 */
sealed interface MetricData<out T> {
    val state: DataState

    data class Value<T>(val value: T) : MetricData<T> {
        override val state: DataState get() = DataState.VALUE
    }

    data object NoData : MetricData<Nothing> {
        override val state: DataState get() = DataState.NO_DATA
    }

    val isPresent: Boolean get() = this is Value
    val isNoData: Boolean get() = this is NoData

    fun valueOrNull(): T? = when (this) {
        is Value -> value
        is NoData -> null
    }

    fun <R> map(transform: (T) -> R): MetricData<R> = when (this) {
        is Value -> Value(transform(value))
        is NoData -> NoData
    }

    fun formatForDisplay(formatter: (T) -> String = { it.toString() }): String = when (this) {
        is Value -> formatter(value)
        is NoData -> "NO DATA"
    }

    companion object {
        fun <T : Any> ofNullable(value: T?): MetricData<T> =
            if (value != null) Value(value) else NoData

        fun ofQuantity(value: Int?): MetricData<Int> =
            if (value != null) Value(value) else NoData
    }
}
