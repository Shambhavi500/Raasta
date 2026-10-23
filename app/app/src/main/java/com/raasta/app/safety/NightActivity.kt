package com.raasta.app.safety

/**
 * What a route passes at night: how much of it runs through dark stretches, and how many
 * open places, late-night help points and cameras sit beside it.
 *
 * The place and camera counts are per street, so a place beside a junction can count for two
 * streets; read them as "about", not exact.
 */
data class NightSummary(
    /** Length of dark stretches at least [NightActivity.DARK_GAP_M] long. */
    val darkM: Double = 0.0,
    /** The longest run of dark streets, even a short one. */
    val longestGapM: Double = 0.0,
    val openPlaces: Int = 0,
    val helpPoints: Int = 0,
    val cameras: Int = 0,
)

object NightActivity {
    /** A dark stretch shorter than this is not worth avoiding. */
    const val DARK_GAP_M = 150.0

    /** How much a metre of dark stretch adds to risk; a vehicle passes through faster, so it counts less. */
    fun darkWeight(mode: TravelMode): Double = if (mode == TravelMode.WALK) 0.5 else 0.25

    fun summarise(model: SafetyModel, streets: List<Int>, hour: Int): NightSummary {
        var dark = 0.0
        var longest = 0.0
        var run = 0.0
        var places = 0
        var help = 0
        var cameras = 0
        fun endRun() {
            if (run >= DARK_GAP_M) dark += run
            longest = maxOf(longest, run)
            run = 0.0
        }
        for (index in streets) {
            val street = model.graph.streets[index]
            places += street.openByHour[hour]
            help += street.lateHelp
            cameras += street.cameras
            if (model.isDark(index, hour)) run += street.lengthM else endRun()
        }
        endRun()
        return NightSummary(dark, longest, places, help, cameras)
    }
}
