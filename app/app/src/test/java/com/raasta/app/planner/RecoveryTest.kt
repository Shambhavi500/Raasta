package com.raasta.app.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Recovery from a missed bus, against the generated corridor data. */
class RecoveryTest {
    private val corridor = Corridor.parse(File("src/main/assets/corridor.json").readText())
    private val planner = JourneyPlanner(corridor)

    @Test
    fun keepWaitingSkipsTheBusThatJustFailed() {
        // Bus 235 was due at 09:20 from Kharadi Gaon and did not come; the next listed one is 09:30.
        val (journeys, _) = planner.recoveryJourneys("kharadi_gaon", "vanaj", nowMin = 9 * 60 + 21, failedRoute = "235")
        val retry = journeys.single { it.retriesFailedRoute }
        val bus = retry.legs.first { it.mode == Mode.BUS }
        assertEquals(Basis.TIMETABLE, bus.timeBasis)
        assertEquals(9 * 60 + 30, bus.startMin + bus.waitMin)
        assertTrue(retry.cautions.contains(Caution.FailedRouteMayRepeat("235")))
    }

    @Test
    fun failedRouteIsOfferedOnlyOnceAndOnlyWhereTheRiderIs() {
        val (journeys, _) = planner.recoveryJourneys("kharadi_gaon", "vanaj", nowMin = 9 * 60 + 21, failedRoute = "235")
        val route235 = journeys.filter { it.busRoute == "235" }
        assertEquals(1, route235.size)
        assertTrue(route235.single().legs.none { it.mode == Mode.WALK && it.to == "Kharadi Gaon" })
    }

    @Test
    fun otherRoutesBoardAtTheNearestStopServingThem() {
        // Route 234 boards at World Trade Center (626 m) and Eon Phase 2 (1.4 km); the nearer one wins.
        val (journeys, _) = planner.recoveryJourneys("kharadi_gaon", "vanaj", nowMin = 9 * 60 + 21, failedRoute = "235")
        val walk = journeys.single { it.busRoute == "234" }.legs.first()
        assertEquals(Mode.WALK, walk.mode)
        assertEquals("World Trade Center", walk.to)
        assertEquals(626, walk.distanceM)
    }

    @Test
    fun lateEveningRecommendsSwitchingAndSaysWhatItCosts() {
        val result = planner.recover("kharadi_gaon", "vanaj", nowMin = 21 * 60 + 50, failedRoute = "235", preferences = TravelPreferences(goals = emptySet()))
        val best = result.ranked.first().journey
        assertEquals(JourneyKind.AUTO_METRO, best.kind)
        val versus = assertNotNullAndGet(result.versusWaiting())
        assertEquals("235", versus.failedRoute)
        assertTrue(versus.minutesSooner > 0)
        assertTrue(versus.extraFare > 0)
    }

    @Test
    fun noComparisonWhenWaitingIsTheBestOption() {
        val result = planner.recover("kharadi_gaon", "vanaj", nowMin = 9 * 60 + 21, failedRoute = "235", preferences = TravelPreferences(goals = setOf(Goal.COST)))
        assertTrue(result.ranked.first().journey.retriesFailedRoute)
        assertEquals(null, result.versusWaiting())
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
