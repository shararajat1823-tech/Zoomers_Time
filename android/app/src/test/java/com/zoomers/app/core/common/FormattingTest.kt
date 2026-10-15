package com.zoomers.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FormattingTest {

    @Test
    fun formatCount_small_numbers_are_plain() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun formatCount_thousands_use_k_suffix() {
        assertEquals("1.0K", formatCount(1_000))
        assertEquals("2.4K", formatCount(2_418))
        assertEquals("12K", formatCount(12_400))
    }

    @Test
    fun formatCount_millions_use_m_suffix() {
        assertEquals("1.5M", formatCount(1_500_000))
    }

    @Test
    fun relativeTime_buckets_by_magnitude() {
        val now = Instant.parse("2026-01-10T12:00:00Z")
        assertEquals("now", relativeTime("2026-01-10T11:59:30Z", now))
        assertEquals("5m", relativeTime("2026-01-10T11:55:00Z", now))
        assertEquals("3h", relativeTime("2026-01-10T09:00:00Z", now))
        assertEquals("2d", relativeTime("2026-01-08T12:00:00Z", now))
    }

    @Test
    fun relativeTime_handles_garbage_gracefully() {
        assertEquals("", relativeTime("not-a-date"))
    }
}
