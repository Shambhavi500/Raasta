package com.raasta.app.safety

import com.raasta.app.map.LatLon
import com.raasta.app.planner.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * A small made-up street grid:
 *
 *   0 --- 1 --- 2     quiet lane through a crime zone (2 x 100 m)
 *   |           |
 *   3 --- 4 --- 5     busy main road with shops (2 x 100 m), linked by 50 m side streets
 */
class RouterTest {
    private val nodes = listOf(
        LatLon(18.5010, 73.8000), LatLon(18.5010, 73.8010), LatLon(18.5010, 73.8020),
        LatLon(18.5005, 73.8000), LatLon(18.5005, 73.8010), LatLon(18.5005, 73.8020),
    )
    private val shops = IntArray(24) { if (it in 8..22) 6 else 0 }
    private val none = IntArray(24)

    private fun graph(mainRoadM: Double = 100.0, laneOneWayBackwards: Boolean = false) = StreetGraph(
        nodes,
        listOf(
            if (laneOneWayBackwards) street(1, 0, 100.0, RoadClass.RESIDENTIAL, none, oneway = true)
            else street(0, 1, 100.0, RoadClass.RESIDENTIAL, none),
            street(1, 2, 100.0, RoadClass.RESIDENTIAL, none),
            street(0, 3, 50.0, RoadClass.RESIDENTIAL, none),
            street(3, 4, mainRoadM, RoadClass.PRIMARY, shops),
            street(4, 5, mainRoadM, RoadClass.PRIMARY, shops),
            street(5, 2, 50.0, RoadClass.RESIDENTIAL, none),
        ),
    )

    private fun street(a: Int, b: Int, m: Double, roadClass: RoadClass, open: IntArray, oneway: Boolean = false) =
        Street(a, b, m, roadClass, foot = true, car = true, oneway = oneway, openByHour = open)

    private val crime = CrimeLayer(
        CrimeData(
            demo = true, title = "Test", notice = "Test zones",
            source = Source("t", "Test", "https://example.org", "2026-10-24", "test"),
            zones = listOf(CrimeZone("z", "Zone", intensity = 0.9, lat = 18.5010, lon = 73.8010, radiusM = 70.0)),
        ),
    )

    @Test
    fun shortestRouteTakesTheLaneSafetyRouteTakesTheMainRoad() {
        val router = SafeRouter(SafetyModel(graph(), crime))
        val pair = router.routes(nodes[0], nodes[2], TravelMode.WALK, hour = 21)!!
        assertEquals(listOf(0, 1), pair.shortest.streets)
        assertEquals(listOf(2, 3, 4, 5), pair.safest.streets)
        assertEquals(200.0, pair.shortest.lengthM, 0.01)
        assertEquals(300.0, pair.safest.lengthM, 0.01)
        assertTrue(pair.safest.exposure.score > pair.shortest.exposure.score)
        assertEquals(0.0, pair.safest.exposure.crimeZoneM, 0.01)
        assertEquals(200.0, pair.shortest.exposure.crimeZoneM, 0.01)
    }

    @Test
    fun tooLongADetourFallsBackToShortest() {
        val router = SafeRouter(SafetyModel(graph(mainRoadM = 1000.0), crime))
        val pair = router.routes(nodes[0], nodes[2], TravelMode.WALK, hour = 21)!!
        assertTrue(pair.same)
    }

    @Test
    fun carsRespectOneWayStreetsWalkersDoNot() {
        val graph = graph(laneOneWayBackwards = true)
        val router = Router(graph)
        val length = { s: Int -> graph.streets[s].lengthM }
        assertEquals(listOf(0, 1), router.route(0, 2, TravelMode.WALK, length)!!.streets)
        assertEquals(listOf(2, 3, 4, 5), router.route(0, 2, TravelMode.DRIVE, length)!!.streets)
    }

    @Test
    fun mainRoadWithOpenShopsIsBusierInTheDayThanAtNight() {
        val model = SafetyModel(graph(), crime = null)
        assertTrue(model.crowd(3, hour = 14) > SafetyModel.BUSY)
        assertTrue(model.crowd(3, hour = 2) < model.crowd(3, hour = 14))
        assertTrue(model.crowd(0, hour = 14) < model.crowd(3, hour = 14))
        // Without crime data, risk is only how empty the street is.
        assertEquals(1 - model.crowd(0, 14), model.risk(0, 14), 1e-9)
    }

    @Test
    fun snapsToNearestNodeTheModeCanUse() {
        val graph = graph()
        assertEquals(4, graph.nearestNode(LatLon(18.50052, 73.80098), TravelMode.WALK))
        // Nothing within the snap distance.
        assertEquals(null, graph.nearestNode(LatLon(18.60, 73.90), TravelMode.WALK))
    }

    @Test
    fun readsTheBinaryFormatTheBuildScriptWrites() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeBytes("RSG2")
            out.writeUTF("""{"generatedOn":"2026-10-24","classes":${RoadClass.entries.map { "\"${it.name.lowercase()}\"" }},"sources":[]}""")
            out.writeInt(2)
            out.writeInt(18_501_000); out.writeInt(73_800_000)
            out.writeInt(18_501_000); out.writeInt(73_801_000)
            out.writeInt(1)
            out.writeInt(0); out.writeInt(1); out.writeFloat(105.5f)
            out.writeByte(RoadClass.PRIMARY.ordinal); out.writeByte(1 or 2 or 4)
            repeat(24) { out.writeByte(if (it == 10) 3 else 0) }
            out.writeByte(2); out.writeByte(1); out.writeByte(4); out.writeByte(3) // all-day, late help, transit, cameras
            out.writeShort(1); out.writeInt(18_501_100); out.writeInt(73_800_500)
            out.writeInt(1)
            out.writeInt(18_501_000); out.writeInt(73_800_000); out.writeInt(1 shl 10); out.writeByte(PlaceKind.PHARMACY.ordinal)
        }
        val graph = StreetGraph.read(ByteArrayInputStream(bytes.toByteArray()))
        val street = graph.streets.single()
        assertEquals(RoadClass.PRIMARY, street.roadClass)
        assertTrue(street.foot && street.car && street.oneway)
        assertEquals(3, street.openByHour[10])
        assertEquals(listOf(2, 1, 4, 3), listOf(street.allDay, street.lateHelp, street.transit, street.cameras))
        assertEquals(LatLon(18.5011, 73.8005), street.via.single())
        assertTrue(graph.places.single().openAt(10))
        assertEquals(PlaceKind.PHARMACY, graph.places.single().kind)
        assertNotNull(graph.meta.generatedOn)
    }
}
