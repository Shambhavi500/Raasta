package com.raasta.app.planner

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** How far a piece of route data can be trusted. */
enum class CheckStatus { OK, ESTIMATE, UNKNOWN, OUTDATED }

/** What Raasta looked at before ranking routes. */
enum class Topic {
    METRO_FARES,
    BUS_FARES,
    AUTO_FARES,
    BUS_TIMES,
    METRO_TIMES,
    WALKING_ROUTES,
    LIVE_UPDATES,
    RIDER_REPORTS,
    STREET_SAFETY,
    STEP_FREE_ACCESS,
    AUTO_AVAILABILITY,
}

/** State of the street safety data. PARTIAL: crime data is missing or a synthetic demo layer. */
enum class StreetSafety { READY, PARTIAL, LOADING, NOT_AVAILABLE }

data class DataCheck(
    val topic: Topic,
    val status: CheckStatus,
    /** The source behind this check, from [Corridor.sources]. */
    val source: Source? = null,
    /** Days since [source] was retrieved. */
    val ageDays: Int? = null,
    /** A count the UI shows, e.g. verified reports used. */
    val count: Int = 0,
    val extra: Int = 0,
)

object DataChecks {
    /** Sources older than this are flagged as possibly outdated. */
    const val STALE_AFTER_DAYS = 90

    /** Everything the ranking for [plan] rests on, with unknown and outdated parts called out. */
    fun build(
        corridor: Corridor,
        plan: PlanResult,
        preferences: TravelPreferences,
        today: String,
        verifiedReports: Int,
        pendingReports: Int,
        streetSafety: StreetSafety,
    ): List<DataCheck> {
        val journeys = plan.ranked.map { it.journey }
        val cautions = journeys.flatMap { it.cautions }
        val usesBus = journeys.any { j -> j.legs.any { it.mode == Mode.BUS } }
        val usesMetro = journeys.any { j -> j.legs.any { it.mode == Mode.METRO } } || plan.unavailable.isNotEmpty()
        val usesAuto = journeys.any { j -> j.legs.any { it.mode == Mode.AUTO } }

        fun sourced(topic: Topic, id: String, status: CheckStatus): DataCheck {
            val source = corridor.sources.firstOrNull { it.id == id }
            val age = source?.let { daysBetween(it.retrieved, today) }
            val fresh = if (age != null && age > STALE_AFTER_DAYS) CheckStatus.OUTDATED else status
            return DataCheck(topic, fresh, source, age)
        }

        return buildList {
            if (usesMetro) add(sourced(Topic.METRO_FARES, "metro_fare", CheckStatus.OK))
            if (usesBus) add(sourced(Topic.BUS_FARES, "pmpml_fare", CheckStatus.ESTIMATE))
            if (usesAuto) add(sourced(Topic.AUTO_FARES, "auto_fare", CheckStatus.OK))
            if (usesBus) {
                val estimated = cautions.any { it is Caution.TimetableEnds }
                add(sourced(Topic.BUS_TIMES, "pmpml_gtfs", if (estimated) CheckStatus.ESTIMATE else CheckStatus.OK))
            }
            // Metro times come from a secondary source and average gaps between trains.
            if (usesMetro) add(sourced(Topic.METRO_TIMES, "metro_service", CheckStatus.ESTIMATE))
            add(sourced(Topic.WALKING_ROUTES, "osrm", CheckStatus.OK))
            add(DataCheck(Topic.LIVE_UPDATES, CheckStatus.UNKNOWN))
            add(DataCheck(Topic.RIDER_REPORTS, CheckStatus.OK, count = verifiedReports, extra = pendingReports))
            if (preferences.safetyAware) {
                val status = when (streetSafety) {
                    StreetSafety.READY -> CheckStatus.OK
                    StreetSafety.PARTIAL -> CheckStatus.ESTIMATE
                    StreetSafety.LOADING, StreetSafety.NOT_AVAILABLE -> CheckStatus.UNKNOWN
                }
                add(DataCheck(Topic.STREET_SAFETY, status))
            }
            if (Goal.ACCESSIBILITY in preferences.goals) add(DataCheck(Topic.STEP_FREE_ACCESS, CheckStatus.UNKNOWN))
            if (usesAuto) add(DataCheck(Topic.AUTO_AVAILABILITY, CheckStatus.UNKNOWN))
        }
    }

    /** Whole days from [from] to [to], both "yyyy-MM-dd"; null when either cannot be read. */
    fun daysBetween(from: String, to: String): Int? {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val a = runCatching { format.parse(from) }.getOrNull() ?: return null
        val b = runCatching { format.parse(to) }.getOrNull() ?: return null
        return TimeUnit.MILLISECONDS.toDays(b.time - a.time).toInt()
    }
}
