package com.raasta.app.safety

import com.raasta.app.map.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A straight road of six 100 m side streets, so a route is just a list of street indices.
 * Each test sets which streets have an open place.
 */
class NightActivityTest {
    private val nodes = List(7) { LatLon(18.5, 73.8 + it * 0.001) }
    private val shops = IntArray(24) { if (it in 8..22) 2 else 0 }
    private val none = IntArray(24)

    private fun model(open: List<IntArray>, roadClass: RoadClass = RoadClass.RESIDENTIAL): SafetyModel {
        val streets = open.mapIndexed { i, hours ->
            Street(i, i + 1, 100.0, roadClass, foot = true, car = true, oneway = false, openByHour = hours)
        }
        return SafetyModel(StreetGraph(nodes, streets), crime = null)
    }

    private fun withCameras(count: Int, vararg indices: Int): SafetyModel {
        val streets = List(6) { i ->
            Street(i, i + 1, 100.0, RoadClass.RESIDENTIAL, true, true, false, none, cameras = if (i in indices) count else 0)
        }
        return SafetyModel(StreetGraph(nodes, streets), crime = null)
    }

    private val all = (0 until 6).toList()

    @Test
    fun shopsThatAreClosedDoNotCount() {
        val model = model(List(6) { shops })
        val day = NightActivity.summarise(model, all, hour = 14)
        val night = NightActivity.summarise(model, all, hour = 2)
        assertEquals(12, day.openPlaces)
        assertEquals(0, day.darkM.toInt())
        assertEquals(0, night.openPlaces)
        assertEquals(600.0, night.darkM, 0.01)
        assertEquals(600.0, night.longestGapM, 0.01)
    }

    @Test
    fun spreadOutShopsLeaveNoLongGapWhereClusteredOnesDo() {
        val openAt = IntArray(24) { 1 }
        val clustered = model(listOf(openAt, openAt, openAt, none, none, none))
        val spread = model(listOf(openAt, none, openAt, none, openAt, none))
        val a = NightActivity.summarise(clustered, all, hour = 22)
        val b = NightActivity.summarise(spread, all, hour = 22)
        // Same three open places each, but the clustered route has a 300 m dark gap.
        assertEquals(3, a.openPlaces)
        assertEquals(3, b.openPlaces)
        assertEquals(300.0, a.longestGapM, 0.01)
        assertEquals(100.0, b.longestGapM, 0.01)
        assertEquals(300.0, a.darkM, 0.01)
        assertEquals(0.0, b.darkM, 0.01) // single 100 m gaps are shorter than the 150 m threshold
    }

    @Test
    fun mainRoadsAreNeverDark() {
        val model = model(List(6) { none }, roadClass = RoadClass.PRIMARY)
        assertFalse(model.isDark(0, hour = 2))
        assertEquals(0.0, NightActivity.summarise(model, all, hour = 2).darkM, 0.0)
    }

    @Test
    fun aCameraMeansAStreetIsNotDark() {
        val model = withCameras(1, 1, 2, 3, 4)
        assertTrue(model.isDark(0, hour = 2))
        assertFalse(model.isDark(2, hour = 2))
        val night = NightActivity.summarise(model, all, hour = 2)
        assertEquals(4, night.cameras)
        assertEquals(100.0, night.longestGapM, 0.01) // one 100 m gap at each end
    }

    @Test
    fun darkStretchesCostMoreToWalkAtNightThanInTheDay() {
        val model = model(List(6) { none })
        assertTrue(model.safetyCost(0, hour = 2, strength = 1.0) > model.safetyCost(0, hour = 14, strength = 1.0))
        // Strength 0 is the plain length.
        assertEquals(100.0, model.safetyCost(0, hour = 2, strength = 0.0), 1e-9)
    }

    @Test
    fun camerasLowerRiskButNeverBelowZero() {
        val covered = withCameras(5, 0)
        val bare = withCameras(0)
        assertTrue(covered.risk(0, hour = 2) < bare.risk(0, hour = 2))
        assertTrue(covered.risk(0, hour = 2) >= 0.0)
        // With no help points or cameras, risk is unchanged from the plain emptiness measure.
        assertEquals(1 - bare.crowd(0, 2), bare.risk(0, 2), 1e-9)
    }

    @Test
    fun walkingCountsDarkGapsMoreThanDriving() {
        assertTrue(NightActivity.darkWeight(TravelMode.WALK) > NightActivity.darkWeight(TravelMode.DRIVE))
    }

    @Test
    fun routeScoreFallsWhenGapsAreAdded() {
        val bright = Exposure(lengthM = 600.0, riskM = 120.0)
        val dark = bright + Exposure(riskM = 300.0 * NightActivity.darkWeight(TravelMode.WALK), darkM = 300.0, longestGapM = 300.0)
        assertTrue(dark.score < bright.score)
        assertEquals(300.0, dark.longestGapM, 0.0)
        // Scaling keeps counts and the gap, and scales the shares.
        val scaled = dark.scaledTo(1200.0)
        assertEquals(600.0, scaled.darkM, 0.01)
        assertEquals(300.0, scaled.longestGapM, 0.0)
        assertEquals(dark.score, scaled.score)
    }

    private fun stopsGraph(vararg places: ActivityPoint) = StreetGraph(nodes, emptyList(), places.toList())

    @Test
    fun nearestStopPrefersANearbyHelpPointOverANearerShop() {
        val here = nodes[0]
        val shop = ActivityPoint(LatLon(here.lat, here.lon + 0.0005), -1, PlaceKind.SHOP) // about 50 m
        val police = ActivityPoint(LatLon(here.lat, here.lon + 0.004), -1, PlaceKind.POLICE) // about 420 m
        val stop = stopsGraph(shop, police).nearestOpenStop(here, hour = 2)!!
        assertEquals(PlaceKind.POLICE, stop.kind)
    }

    @Test
    fun nearestStopFallsBackToAShopWhenHelpIsFar() {
        val here = nodes[0]
        val shop = ActivityPoint(LatLon(here.lat, here.lon + 0.0005), -1, PlaceKind.SHOP)
        val police = ActivityPoint(LatLon(here.lat, here.lon + 0.03), -1, PlaceKind.POLICE) // about 3 km
        assertEquals(PlaceKind.SHOP, stopsGraph(shop, police).nearestOpenStop(here, hour = 2)!!.kind)
    }

    @Test
    fun nearestStopIgnoresClosedPlacesBusStopsAndCashMachines() {
        val here = nodes[0]
        val closed = ActivityPoint(LatLon(here.lat, here.lon + 0.0005), 1 shl 10, PlaceKind.POLICE)
        val busStop = ActivityPoint(LatLon(here.lat, here.lon + 0.0006), -1, PlaceKind.TRANSIT)
        val atm = ActivityPoint(LatLon(here.lat, here.lon + 0.0007), -1, PlaceKind.ATM)
        assertEquals(null, stopsGraph(closed, busStop, atm).nearestOpenStop(here, hour = 2))
    }
}
