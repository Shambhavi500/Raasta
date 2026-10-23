package com.raasta.app.safety

/** Streets in order from one node to another. */
data class StreetPath(val nodes: List<Int>, val streets: List<Int>)

/**
 * Dijkstra's shortest path on the street graph. The edge weight decides what "shortest" means:
 * plain length gives the usual shortest route, [SafetyModel.safetyCost] gives the safety route.
 */
class Router(private val graph: StreetGraph) {

    fun route(from: Int, to: Int, mode: TravelMode, weight: (street: Int) -> Double): StreetPath? {
        val n = graph.nodes.size
        val best = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val cameBy = IntArray(n) { -1 }
        val done = BooleanArray(n)
        val heap = MinHeap()
        best[from] = 0.0
        heap.push(0.0, from)
        while (heap.isNotEmpty()) {
            val node = heap.pop()
            if (done[node]) continue
            done[node] = true
            if (node == to) break
            graph.forEachStreet(node) { s ->
                val street = graph.streets[s]
                if (!street.allows(mode)) return@forEachStreet
                // One-way streets only run from a to b for cars; walking is fine both ways.
                if (mode == TravelMode.DRIVE && street.oneway && node != street.a) return@forEachStreet
                val next = if (node == street.a) street.b else street.a
                val cost = best[node] + weight(s)
                if (cost < best[next]) {
                    best[next] = cost
                    cameBy[next] = s
                    heap.push(cost, next)
                }
            }
        }
        if (from != to && cameBy[to] == -1) return null
        val nodes = ArrayList<Int>()
        val streets = ArrayList<Int>()
        var at = to
        nodes += at
        while (at != from) {
            val s = cameBy[at]
            streets += s
            val street = graph.streets[s]
            at = if (street.a == at) street.b else street.a
            nodes += at
        }
        return StreetPath(nodes.reversed(), streets.reversed())
    }
}

/** Binary min-heap of (cost, node); stale entries are skipped by the caller. */
private class MinHeap {
    private var keys = DoubleArray(256)
    private var values = IntArray(256)
    private var size = 0

    fun isNotEmpty() = size > 0

    fun push(key: Double, value: Int) {
        if (size == keys.size) {
            keys = keys.copyOf(size * 2)
            values = values.copyOf(size * 2)
        }
        var i = size++
        while (i > 0) {
            val parent = (i - 1) / 2
            if (keys[parent] <= key) break
            keys[i] = keys[parent]
            values[i] = values[parent]
            i = parent
        }
        keys[i] = key
        values[i] = value
    }

    fun pop(): Int {
        val top = values[0]
        size--
        if (size > 0) {
            val key = keys[size]
            val value = values[size]
            var i = 0
            while (true) {
                var child = 2 * i + 1
                if (child >= size) break
                if (child + 1 < size && keys[child + 1] < keys[child]) child++
                if (keys[child] >= key) break
                keys[i] = keys[child]
                values[i] = values[child]
                i = child
            }
            keys[i] = key
            values[i] = value
        }
        return top
    }
}
