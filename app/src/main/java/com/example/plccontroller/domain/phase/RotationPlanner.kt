package com.example.plccontroller.domain.phase

object RotationPlanner {
    fun preferredTopLeftSlot(
        currentTopLeftLogicalSlot: Int,
        candidateLogicalSlots: List<Int>,
    ): Int? {
        if (candidateLogicalSlots.isEmpty()) return null
        return candidateLogicalSlots.firstOrNull { it == currentTopLeftLogicalSlot }
            ?: candidateLogicalSlots.minByOrNull { logicalSlot ->
                PotLayout.rotationDistance(currentTopLeftLogicalSlot, logicalSlot)
            }
    }

    fun <T> sortByClockwiseDistance(
        currentTopLeftLogicalSlot: Int,
        items: List<T>,
        logicalSlotOf: (T) -> Int,
    ): List<T> =
        items.sortedBy { item ->
            PotLayout.rotationDistance(currentTopLeftLogicalSlot, logicalSlotOf(item))
        }
}
