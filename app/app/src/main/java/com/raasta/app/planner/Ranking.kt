package com.raasta.app.planner

/**
 * What the rider cares about most. Each factor is normalised (per 60 min, per Rs 100,
 * per 15 min of waiting, per km walked, per 2 changes) and weighted. Lower score is better.
 * The weights are a starting point to be tuned with real riders.
 */
enum class Priority(
    val time: Double,
    val cost: Double,
    val wait: Double,
    val walk: Double,
    val transfers: Double,
    val uncertainty: Double,
) {
    BALANCED(time = 1.0, cost = 0.6, wait = 0.8, walk = 0.3, transfers = 0.3, uncertainty = 0.8),
    CHEAPEST(time = 0.4, cost = 2.0, wait = 0.4, walk = 0.2, transfers = 0.2, uncertainty = 0.5),
    LEAST_WAITING(time = 0.7, cost = 0.4, wait = 2.0, walk = 0.3, transfers = 0.3, uncertainty = 1.0),
    FEWEST_CHANGES(time = 0.6, cost = 0.5, wait = 0.6, walk = 0.6, transfers = 2.0, uncertainty = 0.6),
}

enum class Highlight { FASTEST, CHEAPEST, LEAST_WAITING, FEWEST_CHANGES }

data class RankedJourney(val journey: Journey, val score: Double, val highlights: Set<Highlight>)

data class PlanResult(val ranked: List<RankedJourney>, val unavailable: List<Unavailable>)

object Ranking {

    fun score(journey: Journey, priority: Priority): Double =
        priority.time * journey.totalMin / 60.0 +
            priority.cost * journey.fare / 100.0 +
            priority.wait * journey.waitMin / 15.0 +
            priority.walk * journey.walkM / 1000.0 +
            priority.transfers * journey.transfers / 2.0 +
            priority.uncertainty * journey.uncertainty

    fun rank(journeys: List<Journey>, priority: Priority): List<RankedJourney> {
        val highlights = mapOf(
            Highlight.FASTEST to bestBy(journeys) { it.totalMin },
            Highlight.CHEAPEST to bestBy(journeys) { it.fare },
            Highlight.LEAST_WAITING to bestBy(journeys) { it.waitMin },
            Highlight.FEWEST_CHANGES to bestBy(journeys) { it.transfers },
        )
        return journeys
            .map { journey ->
                RankedJourney(
                    journey,
                    score(journey, priority),
                    highlights.filterValues { journey in it }.keys,
                )
            }
            .sortedBy { it.score }
    }

    /** Journeys with the lowest value, or none when every journey ties (nothing stands out). */
    private fun bestBy(journeys: List<Journey>, value: (Journey) -> Int): Set<Journey> {
        if (journeys.isEmpty()) return emptySet()
        val min = journeys.minOf(value)
        val best = journeys.filter { value(it) == min }
        return if (best.size == journeys.size) emptySet() else best.toSet()
    }
}

fun JourneyPlanner.plan(originId: String, destinationId: String, departMin: Int, priority: Priority): PlanResult {
    val (journeys, unavailable) = journeys(originId, destinationId, departMin)
    return PlanResult(Ranking.rank(journeys, priority), unavailable)
}

fun JourneyPlanner.recover(
    pointId: String,
    destinationId: String,
    nowMin: Int,
    failedRoute: String,
    priority: Priority,
): PlanResult {
    val (journeys, unavailable) = recoveryJourneys(pointId, destinationId, nowMin, failedRoute)
    return PlanResult(Ranking.rank(journeys, priority), unavailable)
}

/** How the best option compares with waiting for the bus that failed. */
data class VersusWaiting(val failedRoute: String, val minutesSooner: Int, val extraFare: Int)

/** Null when waiting is itself the best option, or when waiting is not possible at all. */
fun PlanResult.versusWaiting(): VersusWaiting? {
    val best = ranked.firstOrNull()?.journey ?: return null
    val waiting = ranked.firstOrNull { it.journey.retriesFailedRoute }?.journey ?: return null
    if (best == waiting) return null
    return VersusWaiting(waiting.busRoute.orEmpty(), waiting.arriveMin - best.arriveMin, best.fare - waiting.fare)
}
