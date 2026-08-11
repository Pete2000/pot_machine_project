package com.example.plccontroller.ui

import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PotMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryPotSectionsTest {
    @Test
    fun buildPotSectionsParsesDirectionalFourGridSummary() {
        val order =
            order(
                potMode = PotMode.FourGrid,
                slotSummary = "左上：清水锅|右上：三鲜锅（加浓）|左下：菌汤锅|右下：番茄锅（微辣）",
            )

        val sections = buildPotSections(order)

        assertEquals(listOf("左上", "右上", "左下", "右下"), sections.map { it.slotLabel })
        assertEquals(listOf("清水锅", "三鲜锅", "菌汤锅", "番茄锅"), sections.map { it.baseName })
        assertEquals(listOf(null, "加浓", null, "微辣"), sections.map { it.tasteName })
        assertEquals("分区锅底", cardTitle(order))
        assertEquals("四宫格 · 分区锅底", queueTitle(order))
    }

    @Test
    fun buildPotSectionsUsesSinglePotBottomAndTasteFallback() {
        val order =
            order(
                potMode = PotMode.Single,
                potBottomName = "海底捞6斤番茄锅",
                tasteSummary = "加浓",
            )

        val sections = buildPotSections(order)

        assertEquals(1, sections.size)
        assertEquals("整锅", sections.single().slotLabel)
        assertEquals("海底捞6斤番茄锅", sections.single().baseName)
        assertEquals("加浓", sections.single().tasteName)
        assertEquals("海底捞6斤番茄锅", cardTitle(order))
        assertEquals("海底捞6斤番茄锅", queueTitle(order))
    }

    @Test
    fun buildPotSectionsFallsBackToPendingForMissingMultiPotSlots() {
        val order =
            order(
                potMode = PotMode.ThreeGrid,
                potBottomName = "清汤锅",
            )

        val sections = buildPotSections(order)

        assertEquals(listOf("清汤锅", "待同步", "待同步"), sections.map { it.baseName })
        assertEquals("三拼锅 · 分区锅底", queueTitle(order))
    }

    @Test
    fun buildPotSectionsParsesTasteSummaryWithColons() {
        val order =
            order(
                potMode = PotMode.Split,
                slotSummary = "左锅：番茄锅|右锅：麻辣锅",
                tasteSummary = "左锅:番茄锅:番茄浓汤（多放番茄）|右锅:麻辣锅:微辣",
            )

        val sections = buildPotSections(order)

        assertEquals(2, sections.size)
        assertEquals("左锅", sections[0].slotLabel)
        assertEquals("番茄锅", sections[0].baseName)
        assertEquals("番茄浓汤（多放番茄）", sections[0].tasteName)

        assertEquals("右锅", sections[1].slotLabel)
        assertEquals("麻辣锅", sections[1].baseName)
        assertEquals("微辣", sections[1].tasteName)
    }

    private fun order(
        potMode: PotMode,
        potBottomName: String? = null,
        tasteSummary: String? = null,
        slotSummary: String? = null,
    ): Order =
        Order(
            id = "order-1",
            recipeCode = "POT-TYPE",
            quantity = 1,
            targetTemperature = 0,
            cookSeconds = 0,
            spiceLevel = 0,
            potMode = potMode,
            potBottomName = potBottomName,
            tasteSummary = tasteSummary,
            slotSummary = slotSummary,
        )
}
