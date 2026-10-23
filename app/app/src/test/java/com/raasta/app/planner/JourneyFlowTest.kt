package com.raasta.app.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Preferences, evidence from reports, trade-offs, recovery at the metro and data checks. */
class JourneyFlowTest {
    private val corridor = Corridor.parse(File("src/main/assets/corridor.json").readText())
    private val planner = JourneyPlanner(corridor)

    @Test
    fun noGoalsMeansBalancedAndSeveralGoalsAreAveraged() {
        assertEquals(Weights.BALANCED, TravelPreferences(goals = emptySet()).weights)
        val both = TravelPreferences(goals = setOf(Goal.TIME, Goal.COST)).weights
        assertEquals((Weights.of(Goal.TIME).cost + Weights.of(Goal.COST).cost) / 2, both.cost, 1e-9)
        val walks = TravelPreferences(goals = setOf(Goal.TIME), avoidLongWalks = true).weights
        assertEquals(Weights.of(Goal.TIME).walk + 1.0, walks.walk, 1e-9)
    }

    @Test
    fun verifiedReportPushesAffectedRouteDown() {
        val preferences = TravelPreferences(goals = setOf(Goal.COST))
        val evidence = listOf(Evidence(Condition.BUS_LATE_OR_MISSING, "Kharadi Gaon", "235"))
        val without = planner.plan("wtc", "vanaj", 9 * 60, preferences)
        val with = planner.plan("wtc", "vanaj", 9 * 60, preferences, evidence)
        val before = without.ranked.first { it.journey.busRoute == "235" }
        val after = with.ranked.first { it.journey.busRoute == "235" }
        assertEquals(before.score + 0.3, after.score, 1e-9)
        val cons = Compare.tradeoffs(after.journey, null, evidence).cons
        assertTrue(cons.any { it is Point.Reported && it.condition == Condition.BUS_LATE_OR_MISSING })
    }

    @Test
    fun tradeoffsNameWhatIsBetterAndWorse() {
        val journeys = planner.journeys("wtc", "vanaj", 9 * 60).first
        val auto = journeys.first { it.kind == JourneyKind.AUTO_DIRECT }
        val bus = journeys.first { it.kind == JourneyKind.BUS_METRO }
        val tradeoffs = Compare.tradeoffs(bus, auto)
        assertTrue(tradeoffs.pros.any { it is Point.Cheaper })
        // An auto all the way has no estimated walking, so the bus option walks more.
        assertTrue(tradeoffs.cons.any { it is Point.MoreWalking || it is Point.LongWalk })
        assertFalse(tradeoffs.pros.any { it is Point.Pricier })
    }

    @Test
    fun missedMetroOffersNextTrainAndAuto() {
        val (journeys, _) = planner.fromTransferJourneys("vanaj", nowMin = 21 * 60, metroUnavailable = false)
        assertEquals(setOf(JourneyKind.METRO, JourneyKind.AUTO_DIRECT), journeys.map { it.kind }.toSet())
        val metro = journeys.first { it.kind == JourneyKind.METRO }.legs.first()
        // A full gap between trains after missing one, not half of one.
        assertEquals(corridor.metro.service.headwayOffPeakMin.toInt(), metro.waitMin)
        val auto = journeys.first { it.kind == JourneyKind.AUTO_DIRECT }
        assertTrue(Caution.AutoDistanceEstimated in auto.cautions)
    }

    @Test
    fun metroNotRunningLeavesOnlyAuto() {
        val (journeys, _) = planner.fromTransferJourneys("vanaj", nowMin = 21 * 60, metroUnavailable = true)
        assertEquals(listOf(JourneyKind.AUTO_DIRECT), journeys.map { it.kind })
    }

    @Test
    fun recoveryIsComparedWithTheOriginalPlan() {
        val journeys = planner.journeys("wtc", "vanaj", 9 * 60).first
        val original = journeys.first { it.kind == JourneyKind.BUS_METRO }
        val auto = journeys.first { it.kind == JourneyKind.AUTO_DIRECT }
        val versus = versusOriginal(original, alreadyPaid = 0, option = auto)
        assertEquals(auto.arriveMin - original.arriveMin, versus.lateMin)
        assertEquals(auto.fare - original.fare, versus.extraFare)
    }

    @Test
    fun dataChecksCallOutUnknownAndOutdatedData() {
        val preferences = TravelPreferences(goals = setOf(Goal.ACCESSIBILITY), safetyAware = true)
        val plan = planner.plan("wtc", "vanaj", 21 * 60, preferences)
        val checks = DataChecks.build(corridor, plan, preferences, today = "2026-10-24", 0, 0, StreetSafety.PARTIAL)
        val byTopic = checks.associateBy { it.topic }
        assertEquals(CheckStatus.UNKNOWN, byTopic.getValue(Topic.LIVE_UPDATES).status)
        assertEquals(CheckStatus.UNKNOWN, byTopic.getValue(Topic.STEP_FREE_ACCESS).status)
        assertEquals(CheckStatus.ESTIMATE, byTopic.getValue(Topic.STREET_SAFETY).status)
        // The timetable lists few evening trips, so evening bus waits are estimates.
        assertEquals(CheckStatus.ESTIMATE, byTopic.getValue(Topic.BUS_TIMES).status)

        val later = DataChecks.build(corridor, plan, preferences, today = "2027-06-01", 0, 0, StreetSafety.PARTIAL)
        assertEquals(CheckStatus.OUTDATED, later.first { it.topic == Topic.METRO_FARES }.status)
    }
}
