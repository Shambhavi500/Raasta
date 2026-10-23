package com.raasta.app.ui.plan

import com.raasta.app.map.LatLon
import com.raasta.app.map.MapLine
import com.raasta.app.map.MapPin
import com.raasta.app.map.PinStyle
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Mode
import com.raasta.app.ui.TripViewModel

/** Map lines for a journey: one per leg, coloured by mode, walking dotted. */
fun journeyLines(vm: TripViewModel, journey: Journey, doneLegs: Int = 0): List<MapLine> =
    journey.legs.mapIndexed { i, leg ->
        val color = modeColor(leg.mode)
        MapLine(
            points = vm.shapeOf(leg),
            color = if (i < doneLegs) color.copy(alpha = 0.35f) else color,
            dashed = leg.mode == Mode.WALK,
        )
    }

/** Pickup, drop and the stops where the rider changes vehicle. */
fun journeyPins(vm: TripViewModel, journey: Journey, pickupLabel: String?, dropLabel: String?): List<MapPin> = buildList {
    val (start, end) = vm.places.journeyEnds(journey)
    journey.legs.filter { it.mode != Mode.WALK }.forEachIndexed { i, leg ->
        val style = if (leg.mode == Mode.METRO) PinStyle.STATION else PinStyle.STOP
        vm.places.locate(leg.from)?.let { add(MapPin("board-$i", it, style, color = modeColor(leg.mode))) }
        vm.places.locate(leg.to)?.let { add(MapPin("alight-$i", it, style, color = modeColor(leg.mode))) }
    }
    start?.let { add(MapPin("pickup", it, PinStyle.PICKUP, label = pickupLabel)) }
    end?.let { add(MapPin("drop", it, PinStyle.DROP, label = dropLabel)) }
}

fun journeyPoints(vm: TripViewModel, journey: Journey): List<LatLon> =
    journey.legs.flatMap { vm.shapeOf(it) }
