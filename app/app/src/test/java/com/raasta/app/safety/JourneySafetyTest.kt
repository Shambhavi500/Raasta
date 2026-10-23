package com.raasta.app.safety

import com.raasta.app.map.PlaceIndex
import com.raasta.app.planner.Basis
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.JourneyKind
import com.raasta.app.planner.JourneyPlanner
import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the safety routing on the bundled corridor, street graph and demo crime layer. */
class JourneySafetyTest {
    private val corridor = Corridor.parse(File("src/main/assets/corridor.json").readText())
    private val graph = File("src/main/assets/street_graph.bin").inputStream().use { StreetGraph.read(it) }
    private val crime = CrimeLayer.parse(File("src/main/assets/crime_zones.json").readText())
    private val safety = JourneySafety(SafeRouter(SafetyModel(graph, crime)), PlaceIndex(corridor), corridor.fares.autoFare)
    private val planner = JourneyPlanner(corridor)

    @Test
    fun everyPlannedJourneyGetsAComparison() {
        for (destination in corridor.destinations) {
            for (minute in listOf(9 * 60, 21 * 60 + 30)) {
                planner.journeys("wtc", destination.id, minute).first.forEach { journey ->
                    val routes = safety.compare(journey)
                    if (routes == null) {
                        // Only when no leg is a walk or auto ride long enough to route on streets.
                        assertTrue(journey.legs.all { safety.routesFor(it) == null })
                        assertTrue(journey.kind != JourneyKind.AUTO_DIRECT)
                        return@forEach
                    }
                    assertTrue(routes.safestExposure.score >= routes.shortestExposure.score)
                    // Same vehicles, never faster than the shortest streets.
                    assertEquals(journey.legs.map { it.mode }, routes.safest.legs.map { it.mode })
                    assertTrue(routes.safest.arriveMin >= journey.arriveMin)
                    journey.legs.zip(routes.safest.legs).forEach { (short, safe) ->
                        if (short.distanceM != null && safe.distanceM != null) {
                            assertTrue(safe.distanceM!! <= short.distanceM!! * SafeRouter.MAX_DETOUR + SafeRouter.DETOUR_SLACK_M + 1)
                        }
                    }
                    routes.safest.legs.forEach { leg ->
                        if (leg.mode == Mode.WALK || leg.mode == Mode.AUTO) {
                            if (safety.routesFor(leg) != null) assertNotNull(routes.shapes[leg])
                        }
                    }
                }
            }
        }
    }

    @Test
    fun autoAllTheWayHasStreetRoutes() {
        val direct = planner.journeys("wtc", "deccan", 22 * 60).first.first { it.kind == JourneyKind.AUTO_DIRECT }
        val pair = safety.routesFor(direct.legs.single())
        assertNotNull(pair)
        // OSRM says about 12-13 km by car; our graph should agree roughly.
        assertTrue(pair!!.shortest.lengthM in 9_000.0..16_000.0)
    }

    private fun leg(mode: Mode, start: Int, wait: Int, move: Int, distance: Int? = null, basis: Basis = Basis.ESTIMATE) =
        Leg(mode, "a", "b", startMin = start, waitMin = wait, moveMin = move, distanceM = distance, timeBasis = basis)

    private fun pair(shortM: Double, safeM: Double): RoutePair {
        val short = StreetRoute(emptyList(), shortM, listOf(1), Exposure(shortM))
        return RoutePair(short, StreetRoute(emptyList(), safeM, listOf(2), Exposure(safeM)))
    }

    @Test
    fun longerWalkShortensTheWaitForATimetabledBus() {
        val legs = listOf(
            leg(Mode.WALK, start = 600, wait = 0, move = 10, distance = 800),
            leg(Mode.BUS, start = 610, wait = 10, move = 20, basis = Basis.TIMETABLE),
        )
        val out = safety.retime(legs, listOf(pair(800.0, 1000.0), null), listOf(true, false))!!
        assertEquals(1000, out[0].distanceM)
        assertEquals(13, out[0].moveMin)
        // Same bus, same departure: 613 + 7 = 620.
        assertEquals(613, out[1].startMin)
        assertEquals(7, out[1].waitMin)
        assertEquals(legs[1].endMin, out[1].endMin)
    }

    @Test
    fun detourThatMissesTheBusIsRefused() {
        val legs = listOf(
            leg(Mode.WALK, start = 600, wait = 0, move = 10, distance = 800),
            leg(Mode.BUS, start = 610, wait = 2, move = 20, basis = Basis.TIMETABLE),
        )
        assertNull(safety.retime(legs, listOf(pair(800.0, 1100.0), null), listOf(true, false)))
    }

    @Test
    fun laterLegsMoveByTheExtraTime() {
        val legs = listOf(
            leg(Mode.AUTO, start = 600, wait = 4, move = 20, distance = 5000),
            leg(Mode.METRO, start = 624, wait = 5, move = 15),
        )
        val out = safety.retime(legs, listOf(pair(5000.0, 6000.0), null), listOf(true, false))!!
        assertEquals(24, out[0].moveMin)
        assertEquals(628, out[1].startMin)
        assertEquals(5, out[1].waitMin)
        // Auto fare follows the longer distance: Rs 30 + 4.5 km x Rs 20.
        assertEquals(120, out[0].fare)
    }
}
