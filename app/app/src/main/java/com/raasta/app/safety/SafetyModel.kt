package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.planner.MINUTES_PER_DAY
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.floor

/**
 * How safe each street feels at a given hour, from two things: crime level (from [crime])
 * and how many people are likely around (road type plus nearby places that are open).
 * These are estimates for ranking routes, not measurements.
 */
class SafetyModel(val graph: StreetGraph, val crime: CrimeLayer?) {
    private val crimeLevels = DoubleArray(graph.streets.size) { s -> crime?.levelAt(graph.midpoint(s)) ?: 0.0 }
    private val activityByHour = ConcurrentHashMap<Int, List<ActivityCell>>()

    fun crimeLevel(street: Int): Double = crimeLevels[street]

    /** 0 = likely empty, 1 = busy. */
    fun crowd(street: Int, hour: Int): Double {
        val s = graph.streets[street]
        val road = ROAD_ACTIVITY.getValue(s.roadClass) * timeOfDayFactor(hour)
        val places = 1 - exp(-s.openByHour[hour] / PLACES_SCALE)
        return (road + PLACE_WEIGHT * places).coerceIn(0.0, 1.0)
    }

    /** 0 = safest, 1 = riskiest. Without crime data only the crowd counts. */
    fun risk(street: Int, hour: Int): Double {
        val empty = 1 - crowd(street, hour)
        if (crime == null) return empty
        return CRIME_SHARE * crimeLevel(street) + (1 - CRIME_SHARE) * empty
    }

    /**
     * Edge weight for the safety route: length, made longer for crime and for empty streets.
     * [strength] 1 is the full penalty; lower values trade safety for a shorter detour.
     */
    fun safetyCost(street: Int, hour: Int, strength: Double): Double {
        val s = graph.streets[street]
        val penalty = CRIME_PENALTY * crimeLevel(street) + emptyPenalty(hour) * (1 - crowd(street, hour))
        return s.lengthM * (1 + strength * penalty)
    }

    /** Places open at [hour], grouped into cells about [CELL_M] across, for the people heatmap. */
    fun activity(hour: Int): List<ActivityCell> = activityByHour.getOrPut(hour) {
        val latStep = CELL_M / 111_320.0
        val lonStep = CELL_M / metresPerDegLon(graph.nodes.firstOrNull()?.lat ?: 18.5)
        graph.places.filter { it.openAt(hour) }
            .groupBy { floor(it.at.lat / latStep).toLong() to floor(it.at.lon / lonStep).toLong() }
            .values.map { cell ->
                ActivityCell(LatLon(cell.sumOf { it.at.lat } / cell.size, cell.sumOf { it.at.lon } / cell.size), cell.size)
            }
    }

    companion object {
        /** Share of risk from crime; the rest is from how empty the street is. */
        const val CRIME_SHARE = 0.6
        const val CRIME_PENALTY = 4.0
        const val BUSY = 0.5
        const val IN_CRIME_ZONE = 0.3
        private const val PLACE_WEIGHT = 0.7
        /** About 4 open places make a street feel busy. */
        private const val PLACES_SCALE = 4.0
        const val CELL_M = 250.0

        /** Share of the busiest street's activity a road type has on its own, in the daytime. */
        private val ROAD_ACTIVITY = mapOf(
            RoadClass.TRUNK to 0.55,
            RoadClass.PRIMARY to 0.5,
            RoadClass.SECONDARY to 0.4,
            RoadClass.TERTIARY to 0.3,
            RoadClass.UNCLASSIFIED to 0.2,
            RoadClass.RESIDENTIAL to 0.15,
            RoadClass.LIVING_STREET to 0.15,
            RoadClass.SERVICE to 0.08,
            RoadClass.PEDESTRIAN to 0.3,
            RoadClass.FOOTWAY to 0.1,
        )

        fun timeOfDayFactor(hour: Int): Double = when (hour) {
            in 0..4 -> 0.25
            in 5..6 -> 0.5
            in 7..9 -> 0.9
            in 10..19 -> 1.0
            in 20..21 -> 0.8
            else -> 0.5
        }

        /** Empty streets matter more after dark. */
        fun emptyPenalty(hour: Int): Double = if (hour >= 20 || hour < 6) 2.5 else 1.5
    }
}

data class ActivityCell(val center: LatLon, val openPlaces: Int)

fun hourOf(minute: Int): Int = Math.floorMod(minute, MINUTES_PER_DAY) / 60
