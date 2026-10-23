package com.raasta.app.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FareRulesTest {
    private val slabs = listOf(FareSlab(5.0, 10), FareSlab(10.0, 20), FareSlab(15.0, 30), FareSlab(20.0, 40))
    private val auto = AutoFare(
        baseFare = 30, baseKm = 1.5, perKm = 20,
        nightSurchargePercent = 25, nightStart = "00:00", nightEnd = "05:00",
    )

    @Test
    fun busFareUsesDistanceSlabs() {
        assertEquals(10, FareRules.busFare(slabs, 4.9))
        assertEquals(10, FareRules.busFare(slabs, 5.0))
        assertEquals(20, FareRules.busFare(slabs, 5.57))
        assertEquals(40, FareRules.busFare(slabs, 19.9))
        assertNull(FareRules.busFare(slabs, 25.0))
    }

    @Test
    fun autoFareChargesBaseThenPerKm() {
        assertEquals(30, FareRules.autoFare(auto, 1.0, startMinuteOfDay = 21 * 60))
        // 30 + (6.21 - 1.5) * 20 = 124.2
        assertEquals(124, FareRules.autoFare(auto, 6.21, startMinuteOfDay = 21 * 60))
    }

    @Test
    fun autoFareAddsNightSurchargeAfterMidnight() {
        // 124.2 * 1.25 = 155.25
        assertEquals(155, FareRules.autoFare(auto, 6.21, startMinuteOfDay = 30))
        assertEquals(155, FareRules.autoFare(auto, 6.21, startMinuteOfDay = 24 * 60 + 30))
        assertEquals(124, FareRules.autoFare(auto, 6.21, startMinuteOfDay = 5 * 60))
    }

    @Test
    fun windowsCanCrossMidnight() {
        assertTrue(isWithin(23 * 60 + 30, start = 23 * 60, end = 60))
        assertTrue(isWithin(30, start = 23 * 60, end = 60))
        assertFalse(isWithin(12 * 60, start = 23 * 60, end = 60))
    }
}
