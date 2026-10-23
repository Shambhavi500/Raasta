package com.raasta.app.auth

/**
 * Demo-only phone + OTP login. No SMS is sent and the code is fixed and shown on screen.
 * Replace with a real verification service before any non-demo release.
 */
object DemoAuth {
    const val DEMO_OTP = "123456"
    const val OTP_LENGTH = 6
    const val PHONE_LENGTH = 10

    /** Indian mobile numbers are 10 digits and start with 6, 7, 8 or 9. */
    fun isValidMobile(digits: String): Boolean =
        digits.length == PHONE_LENGTH && digits.all(Char::isDigit) && digits.first() in '6'..'9'

    fun verifyOtp(code: String): Boolean = code == DEMO_OTP

    fun maskPhone(digits: String): String = "+91 ••••••${digits.takeLast(4)}"
}
