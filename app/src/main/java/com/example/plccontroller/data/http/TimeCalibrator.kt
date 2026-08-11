package com.example.plccontroller.data.http

import java.util.concurrent.atomic.AtomicLong

object TimeCalibrator {
    private val offsetMs = AtomicLong(0L)

    fun updateOffset(offset: Long) {
        offsetMs.set(offset)
    }

    fun getOffset(): Long = offsetMs.get()
}
