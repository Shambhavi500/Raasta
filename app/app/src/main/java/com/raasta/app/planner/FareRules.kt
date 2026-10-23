package com.raasta.app.planner

import kotlin.math.roundToInt

/** Fare rules applied to the sourced numbers in [Corridor.fares] and the metro chart. */
object FareRules {

    /** PMPML distance slab fare, or null when the trip is longer than the known slabs. */
    fun busFare(slabs: List<FareSlab>, rideKm: Double): Int? =
        slabs.sortedBy { it.upToKm }.firstOrNull { rideKm <= it.upToKm }?.fare

    /** Pune RTA meter rate: base fare for the first [AutoFare.baseKm], then per km; night surcharge. */
    fun autoFare(rule: AutoFare, km: Double, startMinuteOfDay: Int): Int {
        val day = rule.baseFare + (km - rule.baseKm).coerceAtLeast(0.0) * rule.perKm
        val night = isWithin(startMinuteOfDay, parseClock(rule.nightStart), parseClock(rule.nightEnd))
        val total = if (night) day * (100 + rule.nightSurchargePercent) / 100.0 else day
        return total.roundToInt()
    }

    fun isAutoNight(rule: AutoFare, minuteOfDay: Int): Boolean =
        isWithin(minuteOfDay, parseClock(rule.nightStart), parseClock(rule.nightEnd))
}

/** "HH:MM" to minutes after midnight. */
fun parseClock(hhmm: String): Int {
    val (h, m) = hhmm.split(":").map(String::toInt)
    return h * 60 + m
}

/** True when [minute] (any day) falls in [start, end), handling windows that cross midnight. */
fun isWithin(minute: Int, start: Int, end: Int): Boolean {
    val t = Math.floorMod(minute, MINUTES_PER_DAY)
    return if (start <= end) t in start until end else t >= start || t < end
}

const val MINUTES_PER_DAY = 24 * 60
