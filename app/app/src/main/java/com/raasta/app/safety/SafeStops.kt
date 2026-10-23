package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.map.distanceM
import kotlin.math.roundToInt

/** A place that is open right now where someone can walk in and ask for help. */
data class SafeStop(val kind: PlaceKind, val at: LatLon, val distanceM: Int)

/** A help point this close beats a nearer shop; further away, the closest open place wins. */
private const val HELP_PREFERRED_M = 800.0

/**
 * The closest place open at [hour]: a police station, hospital, fuel station or pharmacy when one is near,
 * otherwise any open shop or service. Bus stops and cash machines are left out, as nobody is there to help.
 */
fun StreetGraph.nearestOpenStop(at: LatLon, hour: Int): SafeStop? {
    val open = places.filter { it.openAt(hour) && it.kind != PlaceKind.TRANSIT && it.kind != PlaceKind.ATM }
    val help = open.filter { it.kind.isHelp }.minByOrNull { distanceM(at, it.at) }
    val nearest = open.minByOrNull { distanceM(at, it.at) }
    val helpIsNear = help != null && distanceM(at, help.at) <= HELP_PREFERRED_M
    val pick = (if (helpIsNear) help else nearest ?: help) ?: return null
    return SafeStop(pick.kind, pick.at, distanceM(at, pick.at).roundToInt())
}
