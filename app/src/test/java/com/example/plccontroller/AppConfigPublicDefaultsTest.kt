package com.example.plccontroller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigPublicDefaultsTest {
    @Test
    fun freshInstallDefaultsStayInsideTheDeviceAndUseMockModbus() {
        assertEquals("mock", AppConfig.plcSerialPortPath)
        assertTrue(AppConfig.businessBaseUrl.startsWith("http://127.0.0.1:"))
        assertTrue(AppConfig.managementBaseUrl.startsWith("http://127.0.0.1:"))
    }
}
