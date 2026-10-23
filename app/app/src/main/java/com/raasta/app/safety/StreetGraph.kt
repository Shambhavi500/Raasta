package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.map.distanceM
import com.raasta.app.planner.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.floor

/** OpenStreetMap highway classes, in the order tools/safety/build_safety.py writes them. */
enum class RoadClass {
    TRUNK, PRIMARY, SECONDARY, TERTIARY, UNCLASSIFIED, RESIDENTIAL, LIVING_STREET, SERVICE, PEDESTRIAN, FOOTWAY,
}

enum class TravelMode { WALK, DRIVE }

/** One piece of street between two junctions. */
class Street(
    val a: Int,
    val b: Int,
    val lengthM: Double,
    val roadClass: RoadClass,
    val foot: Boolean,
    val car: Boolean,
    /** Cars may only go from [a] to [b]. */
    val oneway: Boolean,
    /** Nearby places open in each hour of the day (24 values). */
    val openByHour: IntArray,
    /** Shape points between [a] and [b]. */
    val via: List<LatLon> = emptyList(),
) {
    fun allows(mode: TravelMode): Boolean = if (mode == TravelMode.WALK) foot else car
}

/** A shop, food place, service or transit stop; bit h of [hourMask] is set when it is open in hour h. */
data class ActivityPoint(val at: LatLon, val hourMask: Int) {
    fun openAt(hour: Int): Boolean = hourMask shr hour and 1 == 1
}

@Serializable
data class GraphMeta(val generatedOn: String, val classes: List<String>, val sources: List<Source>)

/** The corridor's street network for routing, built offline from OpenStreetMap. */
class StreetGraph(
    val nodes: List<LatLon>,
    val streets: List<Street>,
    val places: List<ActivityPoint> = emptyList(),
    val meta: GraphMeta = GraphMeta("", RoadClass.entries.map { it.name.lowercase() }, emptyList()),
) {
    /** Street indices touching each node, flattened: node i uses [adjacency] from [offsets][i] until [offsets][i + 1]. */
    private val offsets = IntArray(nodes.size + 1)
    private val adjacency: IntArray
    private val grid = HashMap<Long, MutableList<Int>>()

    init {
        streets.forEach { offsets[it.a + 1]++; offsets[it.b + 1]++ }
        for (i in 1..nodes.size) offsets[i] += offsets[i - 1]
        adjacency = IntArray(offsets[nodes.size])
        val fill = offsets.copyOf()
        streets.forEachIndexed { s, street ->
            adjacency[fill[street.a]++] = s
            adjacency[fill[street.b]++] = s
        }
        nodes.forEachIndexed { i, p -> grid.getOrPut(cell(p.lat, p.lon)) { mutableListOf() } += i }
    }

    inline fun forEachStreet(node: Int, action: (Int) -> Unit) {
        for (k in streetRange(node)) action(streetAt(k))
    }

    fun streetRange(node: Int): IntRange = offsets[node] until offsets[node + 1]
    fun streetAt(k: Int): Int = adjacency[k]

    /** The closest node that [mode] can use, within [maxM] metres. */
    fun nearestNode(at: LatLon, mode: TravelMode, maxM: Double = SNAP_MAX_M): Int? {
        val cx = floor(at.lon / CELL_DEG).toLong()
        val cy = floor(at.lat / CELL_DEG).toLong()
        var best: Int? = null
        var bestM = maxM
        for (dx in -1L..1L) for (dy in -1L..1L) {
            grid[key(cx + dx, cy + dy)]?.forEach { node ->
                val m = distanceM(at, nodes[node])
                if (m < bestM && streetRange(node).any { streets[streetAt(it)].allows(mode) }) {
                    best = node
                    bestM = m
                }
            }
        }
        return best
    }

    /** Shape of [street] walked from [fromNode] to its other end. */
    fun shape(street: Int, fromNode: Int): List<LatLon> {
        val s = streets[street]
        val forward = listOf(nodes[s.a]) + s.via + nodes[s.b]
        return if (fromNode == s.a) forward else forward.reversed()
    }

    fun midpoint(street: Int): LatLon {
        val s = streets[street]
        if (s.via.isNotEmpty()) return s.via[s.via.size / 2]
        val a = nodes[s.a]
        val b = nodes[s.b]
        return LatLon((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
    }

    private fun cell(lat: Double, lon: Double): Long = key(floor(lon / CELL_DEG).toLong(), floor(lat / CELL_DEG).toLong())
    private fun key(x: Long, y: Long): Long = (x shl 32) xor (y and 0xffffffffL)

    companion object {
        /** About 330 m, so a 3 x 3 block always covers [SNAP_MAX_M]. */
        private const val CELL_DEG = 0.003
        const val SNAP_MAX_M = 300.0
        private const val MAGIC = "RSG1"
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the binary file written by tools/safety/build_safety.py. */
        fun read(input: InputStream): StreetGraph {
            val data = DataInputStream(BufferedInputStream(input, 1 shl 16))
            val magic = ByteArray(4).also { data.readFully(it) }.decodeToString()
            require(magic == MAGIC) { "Not a Raasta street graph (header $magic)" }
            val meta = json.decodeFromString(GraphMeta.serializer(), data.readUTF())
            require(meta.classes.size == RoadClass.entries.size) { "Unknown road classes ${meta.classes}" }
            val nodes = List(data.readInt()) { LatLon(data.readInt() / E6, data.readInt() / E6) }
            val streets = List(data.readInt()) {
                val a = data.readInt()
                val b = data.readInt()
                val length = data.readFloat().toDouble()
                val roadClass = RoadClass.entries[data.readByte().toInt()]
                val flags = data.readByte().toInt()
                val open = IntArray(24) { data.readUnsignedByte() }
                val via = List(data.readUnsignedShort()) { LatLon(data.readInt() / E6, data.readInt() / E6) }
                Street(a, b, length, roadClass, flags and 1 != 0, flags and 2 != 0, flags and 4 != 0, open, via)
            }
            val places = List(data.readInt()) {
                ActivityPoint(LatLon(data.readInt() / E6, data.readInt() / E6), data.readInt())
            }
            return StreetGraph(nodes, streets, places, meta)
        }

        private const val E6 = 1_000_000.0
    }
}

/** Metres per degree of longitude at [lat]. */
internal fun metresPerDegLon(lat: Double): Double = 111_320.0 * cos(Math.toRadians(lat))
