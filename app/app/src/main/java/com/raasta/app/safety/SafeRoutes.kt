package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.map.PlaceIndex
import com.raasta.app.map.distanceM
import com.raasta.app.planner.AutoFare
import com.raasta.app.planner.Basis
import com.raasta.app.planner.FareRules
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class RouteVariant { SHORTEST, SAFEST }

/** How much of a route runs through crime zones, busy streets and dark stretches. */
data class Exposure(
    val lengthM: Double = 0.0,
    /** Sum of length x risk (dark stretches add extra), so [score] is a length-weighted average. */
    val riskM: Double = 0.0,
    val crimeZoneM: Double = 0.0,
    val busyM: Double = 0.0,
    /** Length in dark stretches long enough to matter. */
    val darkM: Double = 0.0,
    val longestGapM: Double = 0.0,
    val openPlaces: Int = 0,
    val helpPoints: Int = 0,
    val cameras: Int = 0,
) {
    operator fun plus(other: Exposure) = Exposure(
        lengthM + other.lengthM, riskM + other.riskM, crimeZoneM + other.crimeZoneM, busyM + other.busyM,
        darkM + other.darkM, maxOf(longestGapM, other.longestGapM),
        openPlaces + other.openPlaces, helpPoints + other.helpPoints, cameras + other.cameras,
    )

    /** 100 = every metre is busy, has help nearby and is outside crime zones and dark stretches. */
    val score: Int get() = if (lengthM <= 0) 100 else (100 * (1 - riskM / lengthM)).roundToInt().coerceIn(0, 100)
    val busyShare: Double get() = if (lengthM <= 0) 0.0 else busyM / lengthM
    val crimeZoneShare: Double get() = if (lengthM <= 0) 0.0 else crimeZoneM / lengthM

    /**
     * The same shares over [length] metres. Journey totals weight each leg by its shortest length,
     * so a longer but safer leg can never lower the journey's score.
     */
    fun scaledTo(length: Double): Exposure {
        if (lengthM <= 0) return this
        val k = length / lengthM
        // Shares scale with length; counts and the longest gap describe the actual route.
        return copy(lengthM = length, riskM = riskM * k, crimeZoneM = crimeZoneM * k, busyM = busyM * k, darkM = darkM * k)
    }
}

data class StreetRoute(val points: List<LatLon>, val lengthM: Double, val streets: List<Int>, val exposure: Exposure)

data class RoutePair(val shortest: StreetRoute, val safest: StreetRoute) {
    val same: Boolean get() = shortest.streets == safest.streets
}

/** Finds the shortest and the safety route between two points. */
class SafeRouter(val model: SafetyModel) {
    private val graph = model.graph
    private val router = Router(graph)
    private val cache = ConcurrentHashMap<String, RoutePair>()

    fun routes(from: LatLon, to: LatLon, mode: TravelMode, hour: Int): RoutePair? {
        val key = "$mode:$hour:${from.lat},${from.lon};${to.lat},${to.lon}"
        cache[key]?.let { return it }
        val a = graph.nearestNode(from, mode) ?: return null
        val b = graph.nearestNode(to, mode) ?: return null
        if (a == b) return null
        val shortestPath = router.route(a, b, mode) { graph.streets[it].lengthM } ?: return null
        val shortest = streetRoute(shortestPath, from, to, mode, hour)
        var safest = shortest
        // Full penalty first; soften it until the detour is acceptable.
        for (strength in STRENGTHS) {
            val path = router.route(a, b, mode) { model.safetyCost(it, hour, strength) } ?: break
            val candidate = streetRoute(path, from, to, mode, hour)
            if (candidate.lengthM <= shortest.lengthM * MAX_DETOUR + DETOUR_SLACK_M) {
                safest = candidate
                break
            }
        }
        // Only offer a different route when it is clearly safer by the same measure we show.
        if (safest.exposure.score < shortest.exposure.score + MIN_GAIN) safest = shortest
        return RoutePair(shortest, safest).also { cache[key] = it }
    }

    private fun streetRoute(path: StreetPath, from: LatLon, to: LatLon, mode: TravelMode, hour: Int): StreetRoute {
        val points = ArrayList<LatLon>()
        points += from
        var exposure = Exposure()
        path.streets.forEachIndexed { i, s ->
            val shape = graph.shape(s, path.nodes[i])
            points.addAll(if (i == 0) shape else shape.drop(1))
            val length = graph.streets[s].lengthM
            exposure += Exposure(
                lengthM = length,
                riskM = length * model.risk(s, hour),
                crimeZoneM = if (model.crimeLevel(s) >= SafetyModel.IN_CRIME_ZONE) length else 0.0,
                busyM = if (model.crowd(s, hour) >= SafetyModel.BUSY) length else 0.0,
            )
        }
        points += to
        val night = NightActivity.summarise(model, path.streets, hour)
        exposure += Exposure(
            riskM = night.darkM * NightActivity.darkWeight(mode),
            darkM = night.darkM,
            longestGapM = night.longestGapM,
            openPlaces = night.openPlaces,
            helpPoints = night.helpPoints,
            cameras = night.cameras,
        )
        // The bits from the exact place to the nearest street count towards length but not exposure.
        val connectors = distanceM(from, graph.nodes[path.nodes.first()]) + distanceM(graph.nodes[path.nodes.last()], to)
        return StreetRoute(points, exposure.lengthM + connectors, path.streets, exposure)
    }

    companion object {
        /** The safety route may be at most 40% longer than the shortest, plus a little for short walks. */
        const val MAX_DETOUR = 1.4
        const val DETOUR_SLACK_M = 150.0
        const val MIN_GAIN = 2
        private val STRENGTHS = listOf(1.0, 0.5, 0.25)
    }
}

/** A journey's two versions: the same vehicles, with streets picked for distance or for safety. */
data class JourneyRoutes(
    val shortest: Journey,
    val safest: Journey,
    val shortestExposure: Exposure,
    val safestExposure: Exposure,
    /** Street shapes for the legs of both journeys. */
    val shapes: Map<Leg, List<LatLon>>,
    /** Street legs where the safety route takes other streets. */
    val changedLegs: Int,
) {
    val same: Boolean get() = changedLegs == 0

    fun journey(variant: RouteVariant): Journey = if (variant == RouteVariant.SAFEST) safest else shortest
    fun exposure(variant: RouteVariant): Exposure = if (variant == RouteVariant.SAFEST) safestExposure else shortestExposure
}

/** Applies [SafeRouter] to the walking and auto legs of a planned journey. */
class JourneySafety(private val router: SafeRouter, private val places: PlaceIndex, private val autoFare: AutoFare) {

    /** Null when no leg of [journey] runs on streets the graph covers. */
    fun compare(journey: Journey): JourneyRoutes? {
        val pairs = journey.legs.map { routesFor(it) }
        if (pairs.all { it == null }) return null
        val changed = pairs.map { it != null && !it.same }
        val bus = journey.legs.indexOfFirst { it.mode == Mode.BUS }
        // A longer walk can mean missing a timetabled bus; then keep the shortest streets before it.
        val attempts = listOf(changed, changed.mapIndexed { i, c -> c && i > bus }, changed.map { false })
        val (applied, safeLegs) = attempts.firstNotNullOf { use -> retime(journey.legs, pairs, use)?.let { use to it } }
        val changedLegs = applied.count { it }
        val safest = if (changedLegs == 0) journey else journey.copy(legs = safeLegs)
        val shapes = buildMap {
            journey.legs.forEachIndexed { i, leg -> pairs[i]?.let { put(leg, it.shortest.points) } }
            safest.legs.forEachIndexed { i, leg ->
                pairs[i]?.let { put(leg, if (applied[i]) it.safest.points else it.shortest.points) }
            }
        }
        val shortestExposure = pairs.fold(Exposure()) { sum, p -> if (p == null) sum else sum + p.shortest.exposure }
        val safestExposure = pairs.foldIndexed(Exposure()) { i, sum, p ->
            when {
                p == null -> sum
                applied[i] -> sum + p.safest.exposure.scaledTo(p.shortest.exposure.lengthM)
                else -> sum + p.shortest.exposure
            }
        }
        return JourneyRoutes(journey, safest, shortestExposure, safestExposure, shapes, changedLegs)
    }

    /** Street routes for a walking or auto leg, or null for other legs and places off the graph. */
    fun routesFor(leg: Leg): RoutePair? {
        val mode = when (leg.mode) {
            Mode.WALK -> TravelMode.WALK
            Mode.AUTO -> TravelMode.DRIVE
            else -> return null
        }
        val from = places.locate(leg.from) ?: return null
        val to = places.locate(leg.to) ?: return null
        if (distanceM(from, to) < MIN_LEG_M) return null
        return router.routes(from, to, mode, hourOf(leg.startMin + leg.waitMin))
    }

    /**
     * Legs with the safety streets where [useSafest] is set, lengthened by the extra distance and
     * moved later; null when that would miss a timetabled bus.
     */
    internal fun retime(legs: List<Leg>, pairs: List<RoutePair?>, useSafest: List<Boolean>): List<Leg>? {
        var shift = 0
        return legs.mapIndexed { i, leg ->
            var out = leg
            val pair = pairs[i]
            if (useSafest[i] && pair != null) {
                val ratio = pair.safest.lengthM / pair.shortest.lengthM
                val distance = ((leg.distanceM ?: pair.shortest.lengthM.roundToInt()) * ratio).roundToInt()
                val fare = if (leg.mode == Mode.AUTO) {
                    FareRules.autoFare(autoFare, distance / 1000.0, leg.startMin + shift + leg.waitMin)
                } else leg.fare
                out = leg.copy(
                    distanceM = distance,
                    moveMin = (leg.moveMin * ratio).roundToInt().coerceAtLeast(leg.moveMin),
                    fare = fare,
                )
            }
            out = if (shift != 0 && leg.mode == Mode.BUS && leg.timeBasis == Basis.TIMETABLE) {
                // The bus leaves at its time; arriving later only shortens the wait.
                val wait = leg.waitMin - shift
                if (wait < 0) return null
                out.copy(startMin = leg.startMin + shift, waitMin = wait)
            } else {
                out.copy(startMin = leg.startMin + shift)
            }
            shift = out.endMin - leg.endMin
            out
        }
    }

    private companion object {
        const val MIN_LEG_M = 60.0
    }
}
