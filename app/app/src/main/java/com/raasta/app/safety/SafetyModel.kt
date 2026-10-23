package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.planner.MINUTES_PER_DAY
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.floor

/**
 * How safe each street feels at a given hour, from three things: crime level (from [crime]),
 * how many people are likely around (road type plus nearby places that are open) and whether help
 * is close (late-night help points, cameras, transit). It also spots dark streets: side streets
 * with nothing open, no camera and no transit stop. These are estimates for ranking routes,
 * not measurements.
 */
class SafetyModel(val graph: StreetGraph, val crime: CrimeLayer?) {
    private val crimeLevels = DoubleArray(graph.streets.size) { s -> crime?.levelAt(graph.midpoint(s)) ?: 0.0 }
    private val oversights = DoubleArray(graph.streets.size) { s ->
        val street = graph.streets[s]
        val raw = HELP_WEIGHT * street.lateHelp + ALL_DAY_WEIGHT * street.allDay +
            CAMERA_WEIGHT * street.cameras + TRANSIT_WEIGHT * street.transit
        1 - exp(-raw / OVERSIGHT_SCALE)
    }
    private val activityByHour = ConcurrentHashMap<Int, List<ActivityCell>>()

    fun crimeLevel(street: Int): Double = crimeLevels[street]

    /** 0 = no help point, camera or transit stop nearby, 1 = plenty. */
    fun oversight(street: Int): Double = oversights[street]

    /** A side street with nothing open, no camera and no transit stop, so nobody is likely to be around. */
    fun isDark(street: Int, hour: Int): Boolean {
        val s = graph.streets[street]
        return s.roadClass !in MAIN_ROADS && s.openByHour[hour] == 0 && s.cameras == 0 && s.transit == 0
    }

    /** Share of [oversight] that counts at [hour]; help matters less in daylight. */
    private fun reliefAt(street: Int, hour: Int): Double =
        OVERSIGHT_RELIEF * oversights[street] * if (isNight(hour)) 1.0 else DAYTIME_OVERSIGHT

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
        val base = if (crime == null) empty else CRIME_SHARE * crimeLevel(street) + (1 - CRIME_SHARE) * empty
        return base * (1 - reliefAt(street, hour))
    }

    /**
     * Edge weight for the safety route: length, made longer for crime, empty streets and dark streets.
     * [strength] 1 is the full penalty; lower values trade safety for a shorter detour.
     */
    fun safetyCost(street: Int, hour: Int, strength: Double): Double {
        val s = graph.streets[street]
        val empty = emptyPenalty(hour) * (1 - crowd(street, hour)) * (1 - reliefAt(street, hour))
        val dark = if (isDark(street, hour)) darkPenalty(hour) else 0.0
        val penalty = CRIME_PENALTY * crimeLevel(street) + empty + dark
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

        private const val HELP_WEIGHT = 1.0
        private const val ALL_DAY_WEIGHT = 0.5
        private const val CAMERA_WEIGHT = 0.6
        private const val TRANSIT_WEIGHT = 0.2
        /** About 1.5 weighted help points make a street well covered. */
        private const val OVERSIGHT_SCALE = 1.5
        /** Most that help points, cameras and transit can lower a street's risk. */
        private const val OVERSIGHT_RELIEF = 0.35
        private const val DAYTIME_OVERSIGHT = 0.4

        /** Traffic keeps these roads from being dark even when no shop is open. */
        private val MAIN_ROADS = setOf(RoadClass.TRUNK, RoadClass.PRIMARY, RoadClass.SECONDARY)

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
        fun emptyPenalty(hour: Int): Double = if (isNight(hour)) 2.5 else 1.5

        /** Extra cost for a dark street; the reel's "dark gaps" idea. */
        fun darkPenalty(hour: Int): Double = if (isNight(hour)) 1.5 else 0.5

        fun isNight(hour: Int): Boolean = hour >= 20 || hour < 6
    }
}

data class ActivityCell(val center: LatLon, val openPlaces: Int)

fun hourOf(minute: Int): Int = Math.floorMod(minute, MINUTES_PER_DAY) / 60
