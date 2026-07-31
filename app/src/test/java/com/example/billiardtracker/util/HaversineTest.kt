package com.example.billiardtracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineTest {
    @Test
    fun `same point returns 0`() {
        assertEquals(0.0, haversineMeters(59.9311, 30.3609, 59.9311, 30.3609), 0.01)
    }

    @Test
    fun `Nevsky prospect to Dvortsovaya ~ 2_7km`() {
        // 59.9311, 30.3609 (Nevsky Ave center) to 59.9398, 30.3149 (Dvortsovaya Sq)
        val d = haversineMeters(59.9311, 30.3609, 59.9398, 30.3149)
        assertEquals(2760.0, d, 100.0) // roughly 2.7km
    }

    @Test
    fun `SPb to Moscow ~ 635km`() {
        val d = haversineMeters(59.9311, 30.3609, 55.7558, 37.6173)
        assertEquals(635_000.0, d, 5_000.0)
    }
}
