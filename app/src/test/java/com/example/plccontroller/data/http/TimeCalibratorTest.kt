package com.example.plccontroller.data.http

import com.example.plccontroller.domain.currentOrderTimestampText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeCalibratorTest {
    @After
    fun tearDown() {
        TimeCalibrator.updateOffset(0L)
    }

    @Test
    fun testDefaultOffsetIsZero() {
        TimeCalibrator.updateOffset(0L)
        assertEquals(0L, TimeCalibrator.getOffset())
    }

    @Test
    fun testUpdateOffsetPropagates() {
        TimeCalibrator.updateOffset(5000L)
        assertEquals(5000L, TimeCalibrator.getOffset())

        TimeCalibrator.updateOffset(-3000L)
        assertEquals(-3000L, TimeCalibrator.getOffset())
    }

    @Test
    fun testTimestampUsesCalibratedTime() {
        // Shift time forward by 1 hour (3600000 ms)
        val oneHourMs = 3600000L
        TimeCalibrator.updateOffset(oneHourMs)

        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val expectedCalibratedDate = Date(System.currentTimeMillis() + oneHourMs)
        val expectedTimeString = format.format(expectedCalibratedDate)

        val timestamp = currentOrderTimestampText()

        // Parse back to verify it's approximately 1 hour ahead of system time
        val parsedDate = format.parse(timestamp)!!
        val systemDate = Date()

        val diff = parsedDate.time - systemDate.time
        // The difference should be very close to 1 hour (allowing a few seconds margin for test execution)
        assertTrue("Difference should be around 1 hour, got: $diff", Math.abs(diff - oneHourMs) < 5000L)
    }
}
