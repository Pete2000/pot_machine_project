package com.example.plccontroller.runtime

import com.example.plccontroller.data.plc.PlcCommunicationUnavailableException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlcPollingServiceTest {
    @Test
    fun communicationUnavailableEscalatesImmediatelyAfterPreviousSuccess() {
        assertTrue(
            shouldEscalatePollFailure(
                error = PlcCommunicationUnavailableException("PLC 通讯不可用"),
                consecutiveFailures = 1,
                hadSuccessfulPoll = true,
            ),
        )
    }

    @Test
    fun transientFailureKeepsRetryWindowAfterPreviousSuccess() {
        assertFalse(
            shouldEscalatePollFailure(
                error = IllegalStateException("single frame failed"),
                consecutiveFailures = 1,
                hadSuccessfulPoll = true,
            ),
        )
    }

    @Test
    fun startupFailureEscalatesWithoutPreviousSuccess() {
        assertTrue(
            shouldEscalatePollFailure(
                error = IllegalStateException("startup frame failed"),
                consecutiveFailures = 1,
                hadSuccessfulPoll = false,
            ),
        )
    }
}
