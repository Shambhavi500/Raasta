package com.raasta.app.planner

/** What a rider can say matters most. Several can be picked; none means balanced. */
enum class Goal { TIME, COST, CONVENIENCE, ACCESSIBILITY }

/**
 * Weight of each factor in a journey's score. Each factor is normalised (per 60 min, per Rs 100,
 * per 15 min of waiting, per km walked, per 2 changes) and weighted. Lower score is better.
 * The weights are a starting point to be tuned with real riders.
 */
data class Weights(
    val time: Double,
    val cost: Double,
    val wait: Double,
    val walk: Double,
    val transfers: Double,
    val uncertainty: Double,
) {
    operator fun plus(other: Weights) = Weights(
        time + other.time, cost + other.cost, wait + other.wait,
        walk + other.walk, transfers + other.transfers, uncertainty + other.uncertainty,
    )

    operator fun div(n: Int) = Weights(time / n, cost / n, wait / n, walk / n, transfers / n, uncertainty / n)

    companion object {
        val BALANCED = Weights(time = 1.0, cost = 0.6, wait = 0.8, walk = 0.3, transfers = 0.3, uncertainty = 0.8)

        /** Weights for a rider who cares about [goal] above all; several goals are averaged. */
        fun of(goal: Goal): Weights = when (goal) {
            Goal.TIME -> Weights(time = 2.0, cost = 0.4, wait = 1.0, walk = 0.3, transfers = 0.3, uncertainty = 1.0)
            Goal.COST -> Weights(time = 0.4, cost = 2.0, wait = 0.4, walk = 0.2, transfers = 0.2, uncertainty = 0.5)
            // Convenience: little waiting, few changes and numbers that can be relied on.
            Goal.CONVENIENCE -> Weights(time = 0.7, cost = 0.4, wait = 1.6, walk = 0.4, transfers = 1.6, uncertainty = 1.0)
            // Step-free data is not available, so accessibility means less walking and fewer changes.
            Goal.ACCESSIBILITY -> Weights(time = 0.6, cost = 0.5, wait = 0.6, walk = 2.0, transfers = 1.6, uncertainty = 0.6)
        }
    }
}

/** The rider's travel preferences. They stay the same through the whole journey, including recovery. */
data class TravelPreferences(
    val goals: Set<Goal> = setOf(Goal.TIME),
    /** Prefer streets that are busier and outside crime zones for walks and auto rides. */
    val safetyAware: Boolean = false,
    val avoidLongWalks: Boolean = false,
) {
    val weights: Weights
        get() {
            val w = if (goals.isEmpty()) Weights.BALANCED else goals.map(Weights::of).reduce(Weights::plus) / goals.size
            return if (avoidLongWalks) w.copy(walk = w.walk + 1.0) else w
        }
}

enum class Highlight { FASTEST, CHEAPEST, LEAST_WAITING, FEWEST_CHANGES }

data class RankedJourney(val journey: Journey, val score: Double, val highlights: Set<Highlight>)

data class PlanResult(val ranked: List<RankedJourney>, val unavailable: List<Unavailable>)

object Ranking {

    fun score(journey: Journey, weights: Weights): Double =
        weights.time * journey.totalMin / 60.0 +
            weights.cost * journey.fare / 100.0 +
            weights.wait * journey.waitMin / 15.0 +
            weights.walk * journey.walkM / 1000.0 +
            weights.transfers * journey.transfers / 2.0 +
            weights.uncertainty * journey.uncertainty

    fun rank(
        journeys: List<Journey>,
        preferences: TravelPreferences,
        evidence: List<Evidence> = emptyList(),
    ): List<RankedJourney> {
        val weights = preferences.weights
        val highlights = mapOf(
            Highlight.FASTEST to bestBy(journeys) { it.totalMin },
            Highlight.CHEAPEST to bestBy(journeys) { it.fare },
            Highlight.LEAST_WAITING to bestBy(journeys) { it.waitMin },
            Highlight.FEWEST_CHANGES to bestBy(journeys) { it.transfers },
        )
        return journeys
            .map { journey ->
                val reported = evidence.filter { it.affects(journey) }.sumOf { it.penalty(preferences) }
                RankedJourney(
                    journey,
                    score(journey, weights) + reported,
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

fun JourneyPlanner.plan(
    originId: String,
    destinationId: String,
    departMin: Int,
    preferences: TravelPreferences,
    evidence: List<Evidence> = emptyList(),
): PlanResult {
    val (journeys, unavailable) = journeys(originId, destinationId, departMin)
    return PlanResult(Ranking.rank(journeys, preferences, evidence), unavailable)
}

fun JourneyPlanner.recover(
    pointId: String,
    destinationId: String,
    nowMin: Int,
    failedRoute: String,
    preferences: TravelPreferences,
    evidence: List<Evidence> = emptyList(),
): PlanResult {
    val (journeys, unavailable) = recoveryJourneys(pointId, destinationId, nowMin, failedRoute)
    return PlanResult(Ranking.rank(journeys, preferences, evidence), unavailable)
}

fun JourneyPlanner.recoverAtTransfer(
    destinationId: String,
    nowMin: Int,
    metroUnavailable: Boolean,
    preferences: TravelPreferences,
    evidence: List<Evidence> = emptyList(),
): PlanResult {
    val (journeys, unavailable) = fromTransferJourneys(destinationId, nowMin, metroUnavailable)
    return PlanResult(Ranking.rank(journeys, preferences, evidence), unavailable)
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
