package com.raasta.app.map

import com.raasta.app.planner.BusAccess
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Looks up coordinates for every place name the planner can put on a leg. */
class PlaceIndex(private val corridor: Corridor) {
    private val byName: Map<String, LatLon> = buildMap {
        corridor.metro.stations.forEach { put(it.name, LatLon(it.lat, it.lon)) }
        val transfer = corridor.station(corridor.metro.transferStation)
        put("${transfer.name} Metro", LatLon(transfer.lat, transfer.lon))
        put("${transfer.name} Metro platform", LatLon(transfer.lat, transfer.lon))
        corridor.origins.forEach { origin ->
            put(origin.name, LatLon(origin.lat, origin.lon))
            origin.accessToMetro.filterIsInstance<BusAccess>().forEach { bus ->
                put(bus.boardStop.name, LatLon(bus.boardStop.lat, bus.boardStop.lon))
                put(bus.alightStop.name, LatLon(bus.alightStop.lat, bus.alightStop.lon))
            }
        }
        corridor.recoveryPoints.forEach { put(it.name, LatLon(it.lat, it.lon)) }
        corridor.destinations.forEach { put(it.name, LatLon(it.lat, it.lon)) }
    }

    fun locate(name: String): LatLon? = byName[name]

    /** Straight-line shape of a leg; metro legs follow the stations in between. */
    fun fallbackPath(leg: Leg): List<LatLon> {
        val from = locate(leg.from) ?: return emptyList()
        val to = locate(leg.to) ?: return emptyList()
        if (leg.mode != Mode.METRO) return listOf(from, to)
        val stations = corridor.metro.stations
        val a = stations.indexOfFirst { it.name == leg.from }
        val b = stations.indexOfFirst { it.name == leg.to }
        if (a < 0 || b < 0) return listOf(from, to)
        val range = if (a <= b) a..b else b..a
        val path = stations.slice(range).map { LatLon(it.lat, it.lon) }
        return if (a <= b) path else path.reversed()
    }

    fun journeyEnds(journey: Journey): Pair<LatLon?, LatLon?> =
        locate(journey.legs.first().from) to locate(journey.legs.last().to)
}

/** Great-circle distance in metres. */
fun distanceM(a: LatLon, b: LatLon): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

fun distanceLabel(metres: Double): String =
    if (metres < 1000) "${metres.roundToInt()} m" else "%.1f km".format(metres / 1000)

/** The point [fraction] (0..1) of the way along [path], measured by distance. */
fun pointAlong(path: List<LatLon>, fraction: Double): LatLon? {
    if (path.isEmpty()) return null
    if (path.size == 1 || fraction <= 0) return path.first()
    if (fraction >= 1) return path.last()
    val segments = path.zipWithNext { a, b -> distanceM(a, b) }
    var remaining = segments.sum() * fraction
    for ((i, length) in segments.withIndex()) {
        if (remaining <= length && length > 0) {
            val t = remaining / length
            val a = path[i]
            val b = path[i + 1]
            return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        }
        remaining -= length
    }
    return path.last()
}
