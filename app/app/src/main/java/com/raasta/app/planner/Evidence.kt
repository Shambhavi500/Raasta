package com.raasta.app.planner

/** A route condition a rider can report after (or during) a journey. */
enum class Condition {
    BUS_LATE_OR_MISSING,
    MISSED_CONNECTION,
    CROWDED,
    POOR_LIGHTING,
    FOOTPATH_PROBLEM,
    FELT_UNSAFE,
}

/**
 * A validated report used when ranking: [place] is a stop, station or place name as it appears on
 * legs, [route] the bus route it is about, if any. Unvalidated reports never become evidence.
 */
data class Evidence(val condition: Condition, val place: String, val route: String? = null) {

    /** True when [journey] uses the reported route, or passes the reported place. */
    fun affects(journey: Journey): Boolean = journey.legs.any { leg ->
        when (condition) {
            Condition.BUS_LATE_OR_MISSING -> leg.mode == Mode.BUS && route != null && leg.route == route
            Condition.MISSED_CONNECTION, Condition.CROWDED ->
                (route != null && leg.route == route) || leg.from == place || leg.to == place
            // Street conditions matter where the rider is on foot or waiting outside.
            Condition.POOR_LIGHTING, Condition.FOOTPATH_PROBLEM, Condition.FELT_UNSAFE ->
                leg.mode == Mode.WALK && (leg.from == place || leg.to == place)
        }
    }

    /** Added to an affected journey's score; larger for conditions the rider said they care about. */
    fun penalty(preferences: TravelPreferences): Double = when (condition) {
        Condition.BUS_LATE_OR_MISSING, Condition.MISSED_CONNECTION -> 0.3
        Condition.CROWDED -> if (Goal.CONVENIENCE in preferences.goals) 0.3 else 0.1
        Condition.FOOTPATH_PROBLEM -> if (Goal.ACCESSIBILITY in preferences.goals) 0.4 else 0.1
        Condition.POOR_LIGHTING, Condition.FELT_UNSAFE -> if (preferences.safetyAware) 0.4 else 0.1
    }
}
