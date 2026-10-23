package com.raasta.app.planner

/** One advantage or disadvantage of a route, worded by the UI. */
sealed interface Point {
    data class Faster(val min: Int) : Point
    data class Slower(val min: Int) : Point
    data class Cheaper(val rupees: Int) : Point
    data class Pricier(val rupees: Int) : Point
    data class LessWaiting(val min: Int) : Point
    data class MoreWaiting(val min: Int) : Point
    data class LessWalking(val metres: Int) : Point
    data class MoreWalking(val metres: Int) : Point
    data class FewerChanges(val changes: Int) : Point
    data class MoreChanges(val changes: Int) : Point
    data class SaferStreets(val score: Int, val otherScore: Int) : Point
    data class LessSafeStreets(val score: Int, val otherScore: Int) : Point
    data class ShorterDarkStretch(val metres: Int, val otherMetres: Int) : Point
    data class LongerDarkStretch(val metres: Int, val otherMetres: Int) : Point
    data class MoreOpenPlaces(val count: Int, val otherCount: Int) : Point
    data class FewerOpenPlaces(val count: Int, val otherCount: Int) : Point
    data object ReliableData : Point
    data object MostlyEstimates : Point
    data class LongWait(val min: Int) : Point
    data class LongWalk(val metres: Int) : Point
    data class Reported(val condition: Condition, val place: String) : Point
}

data class Tradeoffs(val pros: List<Point>, val cons: List<Point>)

/** What the chosen streets pass: the longest stretch with nothing open or watching, and open places beside them. */
data class StreetFacts(val longestGapM: Int, val openPlaces: Int)

object Compare {
    // Differences smaller than these are not worth mentioning.
    private const val MIN_TIME = 3
    private const val MIN_FARE = 5
    private const val MIN_WAIT = 3
    private const val MIN_WALK_M = 150
    private const val MIN_SAFETY = 3
    private const val MIN_GAP_M = 100
    private const val MIN_PLACES = 3
    private const val LONG_WAIT_MIN = 15
    private const val LONG_WALK_M = 1000

    /**
     * Advantages and disadvantages of [option] against [reference]: the recommended route for an
     * alternative, or the next best for the recommended route. Safety scores are 0..100 when known.
     */
    fun tradeoffs(
        option: Journey,
        reference: Journey?,
        evidence: List<Evidence> = emptyList(),
        safety: Int? = null,
        referenceSafety: Int? = null,
        facts: StreetFacts? = null,
        referenceFacts: StreetFacts? = null,
    ): Tradeoffs {
        val pros = mutableListOf<Point>()
        val cons = mutableListOf<Point>()
        if (reference != null) {
            diff(reference.arriveMin - option.arriveMin, MIN_TIME, pros, cons, Point::Faster, Point::Slower)
            diff(reference.fare - option.fare, MIN_FARE, pros, cons, Point::Cheaper, Point::Pricier)
            diff(reference.waitMin - option.waitMin, MIN_WAIT, pros, cons, Point::LessWaiting, Point::MoreWaiting)
            diff(reference.walkM - option.walkM, MIN_WALK_M, pros, cons, Point::LessWalking, Point::MoreWalking)
            diff(reference.transfers - option.transfers, 1, pros, cons, Point::FewerChanges, Point::MoreChanges)
            if (safety != null && referenceSafety != null) {
                if (safety - referenceSafety >= MIN_SAFETY) pros += Point.SaferStreets(safety, referenceSafety)
                if (referenceSafety - safety >= MIN_SAFETY) cons += Point.LessSafeStreets(safety, referenceSafety)
            }
            if (facts != null && referenceFacts != null) {
                val gap = referenceFacts.longestGapM - facts.longestGapM
                if (gap >= MIN_GAP_M) pros += Point.ShorterDarkStretch(facts.longestGapM, referenceFacts.longestGapM)
                if (-gap >= MIN_GAP_M) cons += Point.LongerDarkStretch(facts.longestGapM, referenceFacts.longestGapM)
                val places = facts.openPlaces - referenceFacts.openPlaces
                if (places >= MIN_PLACES) pros += Point.MoreOpenPlaces(facts.openPlaces, referenceFacts.openPlaces)
                if (-places >= MIN_PLACES) cons += Point.FewerOpenPlaces(facts.openPlaces, referenceFacts.openPlaces)
            }
        }
        when (option.confidence) {
            Confidence.HIGH -> pros += Point.ReliableData
            Confidence.LOW -> cons += Point.MostlyEstimates
            Confidence.MEDIUM -> Unit
        }
        if (option.waitMin >= LONG_WAIT_MIN && cons.none { it is Point.MoreWaiting }) cons += Point.LongWait(option.waitMin)
        if (option.walkM >= LONG_WALK_M && cons.none { it is Point.MoreWalking }) cons += Point.LongWalk(option.walkM)
        evidence.filter { it.affects(option) }.forEach { cons += Point.Reported(it.condition, it.place) }
        return Tradeoffs(pros, cons)
    }

    private fun diff(
        gain: Int,
        threshold: Int,
        pros: MutableList<Point>,
        cons: MutableList<Point>,
        better: (Int) -> Point,
        worse: (Int) -> Point,
    ) {
        if (gain >= threshold) pros += better(gain)
        if (-gain >= threshold) cons += worse(-gain)
    }
}

/** How a recovery option compares with the journey the rider originally chose. */
data class VersusOriginal(
    /** Minutes later than the original plan's arrival; negative when earlier. */
    val lateMin: Int,
    /** Extra money on top of the original plan's total fare, counting fares already paid. */
    val extraFare: Int,
)

fun versusOriginal(original: Journey, alreadyPaid: Int, option: Journey): VersusOriginal =
    VersusOriginal(option.arriveMin - original.arriveMin, alreadyPaid + option.fare - original.fare)
