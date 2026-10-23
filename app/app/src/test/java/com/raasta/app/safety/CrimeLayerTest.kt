package com.raasta.app.safety

import com.raasta.app.map.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CrimeLayerTest {
    private val json = """
        {
          "demo": false,
          "title": "Test import",
          "notice": "Test",
          "period": "2025",
          "source": {"id": "t", "title": "Test", "url": "https://example.org", "retrieved": "2026-10-24", "note": ""},
          "zones": [
            {"id": "c1", "name": "Circle", "intensity": 0.8, "lat": 18.5, "lon": 73.8, "radiusM": 200},
            {"id": "p1", "name": "Square", "intensity": 0.4, "reportedCases": 120,
             "polygon": [[18.51, 73.81], [18.51, 73.82], [18.52, 73.82], [18.52, 73.81]]},
            {"id": "bad1", "name": "Too strong", "intensity": 1.7, "lat": 18.5, "lon": 73.8, "radiusM": 100},
            {"id": "bad2", "name": "No shape", "intensity": 0.5},
            {"id": "bad3", "name": "Two points", "intensity": 0.5, "polygon": [[18.5, 73.8], [18.6, 73.9]]},
            {"id": "bad4", "name": "Negative", "intensity": 0.5, "reportedCases": -3, "lat": 18.5, "lon": 73.8, "radiusM": 100}
          ]
        }
    """.trimIndent()

    @Test
    fun keepsValidZonesAndExplainsRejectedOnes() {
        val layer = CrimeLayer.parse(json)
        assertEquals(listOf("c1", "p1"), layer.zones.map { it.id })
        assertEquals(listOf("bad1", "bad2", "bad3", "bad4"), layer.rejected.map { it.first.id })
        assertTrue(layer.rejected.all { it.second.isNotBlank() })
    }

    @Test
    fun circleIsStrongestAtTheCentreAndHalvesTowardsTheEdge() {
        val circle = CrimeLayer.parse(json).zone("c1")!!
        assertEquals(0.8, circle.levelAt(LatLon(18.5, 73.8)), 1e-9)
        // About 190 m north: just inside the edge, so about half.
        assertEquals(0.42, circle.levelAt(LatLon(18.5017, 73.8)), 0.02)
        assertEquals(0.0, circle.levelAt(LatLon(18.51, 73.8)), 1e-9)
    }

    @Test
    fun polygonCountsOnlyInside() {
        val layer = CrimeLayer.parse(json)
        assertEquals(0.4, layer.levelAt(LatLon(18.515, 73.815)), 1e-9)
        assertEquals(0.0, layer.levelAt(LatLon(18.525, 73.815)), 1e-9)
        // Roughly 1.1 km x 1.05 km, drawn as a circle of the same area.
        assertEquals(610.0, layer.zone("p1")!!.drawRadiusM, 30.0)
    }

    @Test
    fun bundledCrimeLayerIsMarkedAsDemo() {
        val layer = CrimeLayer.parse(File("src/main/assets/crime_zones.json").readText())
        assertTrue(layer.isDemo)
        assertTrue(layer.rejected.isEmpty())
        assertTrue(layer.zones.isNotEmpty())
        // Synthetic zones must not carry made-up case counts.
        assertTrue(layer.zones.all { it.reportedCases == null })
    }
}
