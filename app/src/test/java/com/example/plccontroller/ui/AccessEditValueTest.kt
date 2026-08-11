package com.example.plccontroller.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessEditValueTest {
    @Test
    fun urlTargetAcceptsRawNetworkPathAndNormalizesItOnSave() {
        val isUrl = AccessEditValue.isUrlTarget("业务接口")

        assertTrue(isUrl)
        assertNull(AccessEditValue.validate("业务接口", "192.168.1.50:8080/order", isUrl))
        assertTrue(AccessEditValue.shouldShowSchemeHint("192.168.1.50:8080/order", isUrl, null))
        assertEquals(
            "http://192.168.1.50:8080/order",
            AccessEditValue.normalizeForSave(" 192.168.1.50:8080/order ", isUrl),
        )
    }

    @Test
    fun urlTargetRejectsPathWithWhitespaceAndKeepsExistingScheme() {
        val isUrl = AccessEditValue.isUrlTarget("管理后台")

        assertEquals(
            "地址格式不正确，应为合法的网络路径",
            AccessEditValue.validate("管理后台", "bad host", isUrl),
        )
        assertFalse(AccessEditValue.shouldShowSchemeHint("https://example.com", isUrl, null))
        assertEquals(
            "https://example.com",
            AccessEditValue.normalizeForSave(" https://example.com ", isUrl),
        )
    }

    @Test
    fun nonUrlTargetOnlyRequiresAValue() {
        val isUrl = AccessEditValue.isUrlTarget("设备编码")

        assertFalse(isUrl)
        assertEquals("设备编码不能为空", AccessEditValue.validate("设备编码", " ", isUrl))
        assertNull(AccessEditValue.validate("设备编码", "A-001@branch", isUrl))
        assertEquals("A-001@branch", AccessEditValue.normalizeForSave(" A-001@branch ", isUrl))
    }
}
