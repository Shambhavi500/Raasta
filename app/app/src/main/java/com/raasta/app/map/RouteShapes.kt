package com.raasta.app.map

import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Road and footpath shapes for legs, fetched from the OSRM servers on routing.openstreetmap.de
 * (the same servers the corridor data was built with). Falls back to straight lines offline.
 */
class RouteShapes(private val places: PlaceIndex) {
    private val cache = mutableMapOf<String, List<LatLon>>()
    private val lock = Mutex()

    suspend fun shape(leg: Leg): List<LatLon> {
        val fallback = places.fallbackPath(leg)
        // Metro runs on its own track; the station-to-station line is the honest shape.
        if (leg.mode == Mode.METRO || fallback.size < 2) return fallback
        val profile = if (leg.mode == Mode.WALK) "foot" else "car"
        val from = fallback.first()
        val to = fallback.last()
        if (distanceM(from, to) < 30) return fallback
        val key = "$profile:${from.lat},${from.lon};${to.lat},${to.lon}"
        lock.withLock { cache[key] }?.let { return it }
        val fetched = runCatching { fetch(profile, from, to) }.getOrNull()
        if (fetched.isNullOrEmpty()) return fallback
        lock.withLock { cache[key] = fetched }
        return fetched
    }

    private suspend fun fetch(profile: String, from: LatLon, to: LatLon): List<LatLon> = withContext(Dispatchers.IO) {
        val url = URL(
            "https://routing.openstreetmap.de/routed-$profile/route/v1/driving/" +
                "${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val coordinates = Json.parseToJsonElement(body).jsonObject["routes"]!!.jsonArray[0]
                .jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
            // GeoJSON order is [lon, lat]. Pin the ends to the exact places.
            listOf(from) + coordinates.map {
                val pair = it.jsonArray
                LatLon(pair[1].jsonPrimitive.double, pair[0].jsonPrimitive.double)
            } + to
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "Raasta-demo/1.0 (Android)"
    }
}
