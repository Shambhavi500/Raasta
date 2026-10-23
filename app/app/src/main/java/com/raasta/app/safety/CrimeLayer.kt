package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.map.distanceM
import com.raasta.app.planner.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Crime by area, from assets/crime_zones.json. The bundled file is a synthetic demo layer
 * ([demo] = true); official area-level data can replace it in the same format (see tools/safety/README.md).
 */
@Serializable
data class CrimeData(
    val demo: Boolean,
    val title: String,
    val notice: String,
    val source: Source,
    /** Reporting period of the counts, e.g. "2024", when the data has one. */
    val period: String? = null,
    val zones: List<CrimeZone>,
)

/**
 * An area with a relative crime level. Either a circle ([lat], [lon], [radiusM]) or a [polygon]
 * of [lat, lon] pairs, e.g. a police station jurisdiction.
 */
@Serializable
data class CrimeZone(
    val id: String,
    val name: String,
    /** 0 = no reported crime, 1 = the highest level in the data. */
    val intensity: Double,
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusM: Double? = null,
    val polygon: List<List<Double>>? = null,
    val category: String? = null,
    val reportedCases: Int? = null,
) {
    /** Why the zone cannot be used, or null when it is valid. */
    fun problem(): String? = when {
        id.isBlank() -> "missing id"
        intensity.isNaN() || intensity !in 0.0..1.0 -> "intensity must be between 0 and 1"
        reportedCases != null && reportedCases < 0 -> "reportedCases cannot be negative"
        polygon != null -> when {
            polygon.size < 3 -> "polygon needs at least 3 points"
            polygon.any { it.size != 2 || !validLatLon(it[0], it[1]) } -> "polygon points must be [lat, lon]"
            else -> null
        }
        lat == null || lon == null || radiusM == null -> "needs lat, lon and radiusM, or a polygon"
        !validLatLon(lat, lon) -> "lat/lon out of range"
        radiusM <= 0 -> "radiusM must be positive"
        else -> null
    }

    /** Centre for drawing: the circle centre or the polygon's average point. */
    val center: LatLon
        get() = if (polygon != null) {
            LatLon(polygon.sumOf { it[0] } / polygon.size, polygon.sumOf { it[1] } / polygon.size)
        } else LatLon(lat!!, lon!!)

    /** Radius for drawing; for a polygon, the circle of the same area. */
    val drawRadiusM: Double
        get() = radiusM ?: sqrt(polygonAreaM2() / PI)

    /** Crime level felt at [at]: full at the centre of a circle, half at its edge; flat inside a polygon. */
    fun levelAt(at: LatLon): Double {
        if (polygon != null) return if (contains(polygon, at)) intensity else 0.0
        val d = distanceM(at, LatLon(lat!!, lon!!))
        val r = radiusM!!
        return if (d >= r) 0.0 else intensity * (1 - 0.5 * d / r)
    }

    private fun polygonAreaM2(): Double {
        val ring = polygon ?: return 0.0
        val ref = center
        val kx = metresPerDegLon(ref.lat)
        val points = ring.map { (it[1] - ref.lon) * kx to (it[0] - ref.lat) * 111_320.0 }
        return abs(points.indices.sumOf { i ->
            val (x1, y1) = points[i]
            val (x2, y2) = points[(i + 1) % points.size]
            x1 * y2 - x2 * y1
        }) / 2
    }
}

class CrimeLayer(val data: CrimeData) {
    /** Zones that passed validation. */
    val zones: List<CrimeZone> = data.zones.filter { it.problem() == null }
    /** Zones dropped by validation, with the reason. */
    val rejected: List<Pair<CrimeZone, String>> = data.zones.mapNotNull { zone -> zone.problem()?.let { zone to it } }

    val isDemo: Boolean get() = data.demo

    /** Highest crime level of any zone at [at] (0..1). */
    fun levelAt(at: LatLon): Double = zones.maxOfOrNull { it.levelAt(at) } ?: 0.0

    fun zone(id: String): CrimeZone? = zones.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): CrimeLayer = CrimeLayer(json.decodeFromString(CrimeData.serializer(), text))
    }
}

/** How a crime level is described to the rider. */
enum class CrimeLevel { LOW, MEDIUM, HIGH }

fun crimeLevel(intensity: Double): CrimeLevel = when {
    intensity >= 0.75 -> CrimeLevel.HIGH
    intensity >= 0.5 -> CrimeLevel.MEDIUM
    else -> CrimeLevel.LOW
}

private fun validLatLon(lat: Double, lon: Double) = lat in -90.0..90.0 && lon in -180.0..180.0

/** Ray-casting point-in-polygon test on [lat, lon] pairs. */
private fun contains(ring: List<List<Double>>, at: LatLon): Boolean {
    var inside = false
    var j = ring.lastIndex
    for (i in ring.indices) {
        val (yi, xi) = ring[i]
        val (yj, xj) = ring[j]
        if ((yi > at.lat) != (yj > at.lat) && at.lon < (xj - xi) * (at.lat - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}
