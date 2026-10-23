package com.raasta.app.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the planner against the generated corridor data bundled with the app. */
class JourneyPlannerTest {
    private val corridor = Corridor.parse(File("src/main/assets/corridor.json").readText())
    private val planner = JourneyPlanner(corridor)

    private fun busJourney(route: String, departMin: Int): Journey =
        planner.journeys("wtc", "vanaj", departMin).first.first { it.busRoute == route }

    @Test
    fun morningBusWaitComesFromTimetable() {
        val journey = busJourney("235", departMin = 9 * 60)
        val bus = journey.legs.first { it.mode == Mode.BUS }
        assertEquals(Basis.TIMETABLE, bus.timeBasis)
        // Walk ends 09:08; the next listed 235 departure from Kharadi Gaon is 09:20.
        assertEquals(9 * 60 + 20, bus.startMin + bus.waitMin)
        assertTrue(journey.cautions.none { it is Caution.TimetableEnds })
    }

    @Test
    fun busPlusMetroFareUsesSlabAndOfficialChart() {
        val journey = busJourney("235", departMin = 9 * 60)
        // 4.9 km bus = Rs 10 slab; Ramwadi to Vanaz = Rs 30 on the official chart.
        assertEquals(40, journey.fare)
        val metro = journey.legs.first { it.mode == Mode.METRO }
        assertEquals(Basis.OFFICIAL_FARE, metro.fareBasis)
        assertEquals("Vanaz", metro.to)
    }

    @Test
    fun eveningWaitIsLabelledAsEstimateWhenTimetableEnds() {
        val journey = busJourney("235", departMin = 21 * 60 + 30)
        assertEquals(Basis.ESTIMATE, journey.legs.first { it.mode == Mode.BUS }.timeBasis)
        assertTrue(journey.cautions.contains(Caution.TimetableEnds("235", "16:00")))
    }

    @Test
    fun lateEveningMetroCarriesLastTrainCaution() {
        val journey = busJourney("235", departMin = 21 * 60 + 30)
        assertTrue(journey.cautions.contains(Caution.MetroLastTrainUncertain))
        assertEquals(Confidence.LOW, journey.confidence)
    }

    @Test
    fun autoIsNeverHighConfidence() {
        val (journeys, _) = planner.journeys("wtc", "vanaj", departMin = 9 * 60)
        journeys.filter { it.kind != JourneyKind.BUS_METRO }.forEach {
            assertFalse(it.confidence == Confidence.HIGH)
        }
    }

    @Test
    fun metroOptionsAreUnavailableAfterLastTrain() {
        val (journeys, unavailable) = planner.journeys("wtc", "vanaj", departMin = 23 * 60 + 30)
        assertEquals(listOf(JourneyKind.AUTO_DIRECT), journeys.map { it.kind })
        assertTrue(unavailable.isNotEmpty())
    }

    @Test
    fun balancedRankingPrefersBusAndMetroOverLongAutoRide() {
        val result = planner.plan("wtc", "vanaj", departMin = 9 * 60, priority = Priority.BALANCED)
        assertEquals(JourneyKind.BUS_METRO, result.ranked.first().journey.kind)
        assertFalse(result.ranked.first().journey.kind == JourneyKind.AUTO_DIRECT)
    }

    @Test
    fun cheapestHighlightGoesToLowestFare() {
        val result = planner.plan("wtc", "vanaj", departMin = 9 * 60, priority = Priority.CHEAPEST)
        val cheapest = result.ranked.filter { Highlight.CHEAPEST in it.highlights }
        assertTrue(cheapest.isNotEmpty())
        assertEquals(result.ranked.minOf { it.journey.fare }, cheapest.first().journey.fare)
        assertEquals(cheapest.first(), result.ranked.first())
    }
}
