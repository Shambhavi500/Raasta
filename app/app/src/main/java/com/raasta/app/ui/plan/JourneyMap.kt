package com.raasta.app.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.LatLon
import com.raasta.app.map.MapCamera
import com.raasta.app.map.MapLine
import com.raasta.app.map.MapPin
import com.raasta.app.map.PinStyle
import com.raasta.app.map.RaastaMap
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Mode
import com.raasta.app.safety.RouteVariant
import com.raasta.app.safety.hourOf
import com.raasta.app.ui.SafetyStatus
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapButton
import com.raasta.app.ui.theme.MutedSlate

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

/** Start, destination and the stops where the rider changes vehicle. */
fun journeyPins(vm: TripViewModel, journey: Journey, startLabel: String?, endLabel: String?): List<MapPin> = buildList {
    val (start, end) = vm.places.journeyEnds(journey)
    journey.legs.filter { it.mode != Mode.WALK }.forEachIndexed { i, leg ->
        val style = if (leg.mode == Mode.METRO) PinStyle.STATION else PinStyle.STOP
        vm.places.locate(leg.from)?.let { add(MapPin("board-$i", it, style, color = modeColor(leg.mode))) }
        vm.places.locate(leg.to)?.let { add(MapPin("alight-$i", it, style, color = modeColor(leg.mode))) }
    }
    start?.let { add(MapPin("start", it, PinStyle.PICKUP, label = startLabel)) }
    end?.let { add(MapPin("end", it, PinStyle.DROP, label = endLabel)) }
}

fun journeyPoints(vm: TripViewModel, journey: Journey): List<LatLon> =
    journey.legs.flatMap { vm.shapeOf(it) }

/**
 * A planned journey on the map with the streets the rider picked; the other street choice is drawn
 * in grey. Shows the people and crime layers when switched on.
 */
@Composable
fun RouteMap(vm: TripViewModel, journey: Journey?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tappedZone by rememberSaveable { mutableStateOf<String?>(null) }
    val routes = journey?.let { vm.comparisons[it] }
    val shown = journey?.let(vm::chosen)
    val other = routes?.takeIf { !it.same && vm.preferences.safetyAware }?.journey(
        if (vm.routeVariant == RouteVariant.SAFEST) RouteVariant.SHORTEST else RouteVariant.SAFEST,
    )
    val hour = hourOf(shown?.departMin ?: vm.searchMinute)
    val crime = (vm.safetyStatus as? SafetyStatus.Ready)?.crime

    Box(modifier) {
        if (journey != null && shown != null) {
            val otherLines = other?.legs.orEmpty()
                .filter { it.mode == Mode.WALK || it.mode == Mode.AUTO }
                .map { MapLine(vm.shapeOf(it), MutedSlate) }
            RaastaMap(
                pins = journeyPins(vm, shown, startLabel = vm.fromName.substringBefore(","), endLabel = vm.destinationName.substringBefore(",")),
                lines = otherLines + journeyLines(vm, shown),
                // Fit both versions so switching between them does not move the map.
                camera = MapCamera.Fit(journeyPoints(vm, journey) + routes?.safest?.let { journeyPoints(vm, it) }.orEmpty()),
                modifier = Modifier.fillMaxSize(),
                heat = heatSpots(vm, hour),
                onHeatSpotClick = { tappedZone = it },
            )
        } else {
            RaastaMap(
                pins = emptyList(),
                lines = emptyList(),
                camera = MapCamera.Fit(listOf(vm.fromPoint)),
                modifier = Modifier.fillMaxSize(),
                heat = heatSpots(vm, hour),
                onHeatSpotClick = { tappedZone = it },
            )
        }
        MapButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp),
        )
        HeatLegend(
            vm,
            hour,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 40.dp),
        )
        val zone = tappedZone?.let { crime?.zone(it) }
        if (zone != null && crime != null && vm.showCrimeHeat) {
            CrimeZoneCard(
                zone,
                crime,
                onClose = { tappedZone = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}
