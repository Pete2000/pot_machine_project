package com.example.plccontroller.domain.phase

enum class PotLayoutKind {
    Small,
    Single,
    Split,
    ThreeGrid,
    FourGrid,
}

object PotLayout {
    private val clockwiseRing = listOf(1, 2, 4, 3)

    fun slotCount(kind: PotLayoutKind): Int =
        when (kind) {
            PotLayoutKind.Small,
            PotLayoutKind.Single,
            -> 1
            PotLayoutKind.Split -> 2
            PotLayoutKind.ThreeGrid -> 3
            PotLayoutKind.FourGrid -> 4
        }

    fun rotatedLogicalSlots(
        kind: PotLayoutKind,
        targetTopLeftLogicalSlot: Int?,
    ): List<Int> {
        val target = targetTopLeftLogicalSlot?.coerceIn(1, slotCount(kind)) ?: 1
        return when (kind) {
            PotLayoutKind.Small,
            PotLayoutKind.Single,
            -> listOf(1)
            PotLayoutKind.Split -> if (target == 2) listOf(2, 1) else listOf(1, 2)
            PotLayoutKind.ThreeGrid ->
                when (target) {
                    2 -> listOf(2, 3, 1)
                    3 -> listOf(3, 1, 2)
                    else -> listOf(1, 2, 3)
                }
            PotLayoutKind.FourGrid ->
                when (target) {
                    2 -> listOf(2, 4, 1, 3)
                    3 -> listOf(3, 1, 4, 2)
                    4 -> listOf(4, 3, 2, 1)
                    else -> listOf(1, 2, 3, 4)
                }
        }
    }

    fun physicalLabel(
        kind: PotLayoutKind,
        physicalIndex: Int,
        includeOutlet: Boolean,
    ): String {
        val labels = if (includeOutlet) physicalLabelsWithOutlet(kind) else physicalLabels(kind)
        return labels.getOrElse(physicalIndex) { "${physicalIndex + 1}号锅底" }
    }

    fun physicalLabelForLogicalSlot(
        kind: PotLayoutKind,
        logicalSlot: Int,
        activeTopLeftLogicalSlot: Int,
        includeOutlet: Boolean,
    ): String {
        val physicalIndex =
            rotatedLogicalSlots(kind, activeTopLeftLogicalSlot)
                .indexOf(logicalSlot)
                .takeIf { it >= 0 }
                ?: (logicalSlot - 1).coerceIn(0, slotCount(kind) - 1)
        return physicalLabel(kind, physicalIndex, includeOutlet)
    }

    fun rotationDistance(
        currentTopLeftLogicalSlot: Int,
        targetLogicalSlot: Int,
    ): Int {
        val currentIndex = clockwiseRing.indexOf(currentTopLeftLogicalSlot).takeIf { it >= 0 } ?: 0
        val targetIndex = clockwiseRing.indexOf(targetLogicalSlot).takeIf { it >= 0 } ?: return Int.MAX_VALUE
        return (targetIndex - currentIndex + clockwiseRing.size) % clockwiseRing.size
    }

    private fun physicalLabels(kind: PotLayoutKind): List<String> =
        when (kind) {
            PotLayoutKind.Small -> listOf("左上工位")
            PotLayoutKind.Single -> listOf("整锅工位")
            PotLayoutKind.Split -> listOf("左侧工位", "右侧工位")
            PotLayoutKind.ThreeGrid -> listOf("左上工位", "右上工位", "下方工位")
            PotLayoutKind.FourGrid -> listOf("左上工位", "右上工位", "左下工位", "右下工位")
        }

    private fun physicalLabelsWithOutlet(kind: PotLayoutKind): List<String> =
        when (kind) {
            PotLayoutKind.Small -> listOf("左上工位（出水口1）")
            PotLayoutKind.Single -> listOf("整锅工位（出水口1/2/3/4）")
            PotLayoutKind.Split ->
                listOf(
                    "左侧工位（出水口1/3）",
                    "右侧工位（出水口2/4）",
                )
            PotLayoutKind.ThreeGrid ->
                listOf(
                    "左上工位（出水口1）",
                    "右上工位（出水口2）",
                    "下方工位（出水口3）",
                )
            PotLayoutKind.FourGrid ->
                listOf(
                    "左上工位（出水口1）",
                    "右上工位（出水口2）",
                    "左下工位（出水口3）",
                    "右下工位（出水口4）",
                )
        }
}
