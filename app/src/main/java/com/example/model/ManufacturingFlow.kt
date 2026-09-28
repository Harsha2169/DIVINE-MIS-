package com.example.model

/**
 * Authoritative Canonical Manufacturing Flow definition.
 *
 * Sequence:
 * CASTING -> POST CASTING -> M/C -> BUFFING -> FINAL -> KAMAL OK -> GIL DELIVERY
 *
 * All future dashboard flow visualizations MUST consume this authoritative definition.
 */
object ManufacturingFlow {

    val STEPS: List<Department> = listOf(
        Department.CASTING,
        Department.POST_CASTING,
        Department.MACHINING,
        Department.BUFFING,
        Department.FINAL,
        Department.KAMAL_OK,
        Department.GIL_DELIVERY
    )

    fun getSequence(): List<Department> = STEPS

    fun totalSteps(): Int = STEPS.size

    fun getStepIndex(department: Department): Int = STEPS.indexOf(department)

    fun getNextStep(current: Department): Department? {
        val index = STEPS.indexOf(current)
        return if (index in 0 until STEPS.size - 1) STEPS[index + 1] else null
    }

    fun getPreviousStep(current: Department): Department? {
        val index = STEPS.indexOf(current)
        return if (index > 0) STEPS[index - 1] else null
    }

    fun isSequentialTransition(from: Department, to: Department): Boolean {
        val fromIdx = STEPS.indexOf(from)
        val toIdx = STEPS.indexOf(to)
        return fromIdx != -1 && toIdx == fromIdx + 1
    }
}
