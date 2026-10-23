package com.raasta.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAuthTest {
    @Test
    fun acceptsTenDigitMobileStartingWithSixToNine() {
        assertTrue(DemoAuth.isValidMobile("9876543210"))
        assertTrue(DemoAuth.isValidMobile("6000000000"))
    }

    @Test
    fun rejectsInvalidMobiles() {
        assertFalse(DemoAuth.isValidMobile("5876543210"))
        assertFalse(DemoAuth.isValidMobile("987654321"))
        assertFalse(DemoAuth.isValidMobile("98765432a0"))
    }

    @Test
    fun onlyTheDemoCodeVerifies() {
        assertTrue(DemoAuth.verifyOtp(DemoAuth.DEMO_OTP))
        assertFalse(DemoAuth.verifyOtp("000000"))
    }

    @Test
    fun masksAllButLastFourDigits() {
        assertEquals("+91 ••••••3210", DemoAuth.maskPhone("9876543210"))
    }
}
