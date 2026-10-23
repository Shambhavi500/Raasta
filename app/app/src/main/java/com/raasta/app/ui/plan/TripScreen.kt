package com.raasta.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.MapCamera
import com.raasta.app.map.MapPin
import com.raasta.app.map.PinStyle
import com.raasta.app.map.RaastaMap
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import com.raasta.app.planner.PlanResult
import com.raasta.app.ui.RecoveryRequest
import com.raasta.app.ui.TripProgress
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapButton
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.ErrorOnDark
import com.raasta.app.ui.theme.Obsidian
import kotlinx.coroutines.launch

private enum class CameraMode { FOLLOW, FREE, OVERVIEW }

private val Speeds = listOf(1f, 2f, 5f, 10f)

/** Live trip: the rider moves along the route, with the next instruction on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(vm: TripViewModel, onArrived: () -> Unit, onEnded: () -> Unit) {
    val journey = vm.trip ?: return
    val destinationName = vm.destinationId?.let { vm.corridor.destination(it).name }.orEmpty()
    val progress = vm.progress(journey, vm.tripMinute)
    val leg = journey.legs[progress.legIndex]
    var cameraMode by remember { mutableStateOf(CameraMode.FOLLOW) }
    var overviewToken by remember { mutableIntStateOf(0) }
    var confirmEnd by remember { mutableStateOf(false) }
    var rescue by remember { mutableStateOf<Pair<RecoveryRequest, PlanResult>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(vm.arrived) { if (vm.arrived) onArrived() }
    BackHandler { confirmEnd = true }

    val position = progress.position
    val pins = journeyPins(vm, journey, pickupLabel = null, dropLabel = destinationName.substringBefore(",")) +
        listOfNotNull(position?.let { MapPin("me", it, PinStyle.VEHICLE, color = modeColor(leg.mode)) })
    val camera = when {
        cameraMode == CameraMode.FOLLOW && position != null -> MapCamera.Follow(position, 16.5)
        cameraMode == CameraMode.OVERVIEW -> MapCamera.Fit(journeyPoints(vm, journey), overviewToken)
        else -> MapCamera.Free
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.55f
        RaastaMap(
            pins = pins,
            lines = journeyLines(vm, journey, doneLegs = progress.legIndex),
            camera = camera,
            modifier = Modifier.fillMaxSize(),
            centerOffsetY = if (cameraMode == CameraMode.FOLLOW) -maxHeight * 0.2f else 0.dp,
            onMapTouched = { cameraMode = CameraMode.FREE },
        )

        Column(
            Modifier
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            InstructionCard(vm, leg, progress)
            SnackbarHost(snackbar) { Snackbar(it, containerColor = ElectricLime, contentColor = Obsidian) }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapButton(Icons.Filled.Map, stringResource(R.string.trip_overview), onClick = {
                    cameraMode = CameraMode.OVERVIEW
                    overviewToken++
                })
                MapButton(Icons.Filled.MyLocation, stringResource(R.string.map_recenter), onClick = { cameraMode = CameraMode.FOLLOW })
            }
            MapSheet(Modifier.heightIn(max = maxSheet)) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.trip_arrive, clock(journey.arriveMin)),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(
                                    R.string.trip_left,
                                    (journey.arriveMin - vm.tripMinute).toInt().coerceAtLeast(0),
                                    destinationName,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.journey_fare, journey.fare), style = MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(
                        progress = {
                            ((vm.tripMinute - journey.departMin) / journey.totalMin.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        color = ElectricLime,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )

                    val boarding = vm.awaitingBoarding
                    if (boarding != null) {
                        val busLeg = journey.legs[boarding]
                        BoardingPrompt(
                            route = displayRoute(busLeg.route.orEmpty()),
                            stop = busLeg.from,
                            onBoarded = vm::confirmBoarded,
                            onDidNotCome = { rescue = vm.rescuePlan() },
                        )
                    } else if (leg.mode == Mode.BUS && progress.waiting) {
                        OutlinedButton(
                            onClick = { rescue = vm.rescuePlan() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.trip_bus_late, displayRoute(leg.route.orEmpty())), color = AttentionAmber)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(journey.legs) { i, step ->
                            StepRow(step, done = i < progress.legIndex, current = i == progress.legIndex)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::togglePause, enabled = vm.awaitingBoarding == null) {
                            Icon(
                                if (vm.tripPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = stringResource(if (vm.tripPaused) R.string.trip_resume else R.string.trip_pause),
                            )
                        }
                        TextButton(onClick = {
                            vm.speed = Speeds[(Speeds.indexOf(vm.speed) + 1) % Speeds.size]
                        }) {
                            Text(stringResource(R.string.trip_speed, vm.speed.toInt()))
                        }
                        IconButton(onClick = vm::skipAhead, enabled = vm.awaitingBoarding == null) {
                            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.trip_skip))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { confirmEnd = true }) {
                            Text(stringResource(R.string.trip_end), color = ErrorOnDark)
                        }
                    }
                }
            }
        }
    }

    rescue?.let { (request, plan) ->
        ModalBottomSheet(
            onDismissRequest = { rescue = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.trip_rescue_title, displayRoute(request.failedRoute)),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                RecommendationBanner(plan, request.failedRoute)
                plan.ranked.forEachIndexed { i, option ->
                    OptionRow(option, selected = i == 0, onClick = {
                        vm.reroute(option.journey)
                        rescue = null
                        cameraMode = CameraMode.FOLLOW
                        val message = context.getString(R.string.trip_rerouted)
                        scope.launch { snackbar.showSnackbar(message) }
                    })
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.trip_end_title)) },
            text = { Text(stringResource(R.string.trip_end_body)) },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; vm.endTrip(); onEnded() }) {
                    Text(stringResource(R.string.trip_end), color = ErrorOnDark)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.trip_keep_going)) } },
        )
    }
}

@Composable
private fun InstructionCard(vm: TripViewModel, leg: Leg, progress: TripProgress) {
    val minutes = progress.minutesToNextEvent
    val route = displayRoute(leg.route.orEmpty())
    val (title, detail) = when (leg.mode) {
        Mode.WALK -> stringResource(R.string.leg_walk, leg.to) to
            listOfNotNull(
                stringResource(R.string.journey_minutes, minutes),
                leg.distanceM?.takeIf { it > 0 && leg.moveMin > 0 }?.let { distance(it * minutes.coerceAtMost(leg.moveMin) / leg.moveMin) },
            ).joinToString(" · ")
        Mode.BUS -> if (progress.waiting && minutes == 0) {
            stringResource(R.string.trip_bus_due, route) to stringResource(R.string.trip_wait_at, leg.from)
        } else if (progress.waiting) {
            stringResource(R.string.trip_bus_arrives, route, minutes) to stringResource(R.string.trip_wait_at, leg.from)
        } else {
            stringResource(R.string.trip_on_bus, route, leg.to) to stringResource(R.string.trip_get_off_in, minutes)
        }
        Mode.METRO -> if (progress.waiting) {
            stringResource(R.string.trip_train_arrives, leg.route.orEmpty(), minutes) to stringResource(R.string.trip_platform, leg.from)
        } else {
            val stations = vm.corridor.metro.stations
            val stops = (stations.indexOfFirst { it.name == leg.to } - stations.indexOfFirst { it.name == leg.from }).let { kotlin.math.abs(it) }
            stringResource(R.string.trip_on_metro, leg.route.orEmpty(), leg.to) to stringResource(R.string.trip_get_off_stops, minutes, stops)
        }
        Mode.AUTO -> if (progress.waiting) {
            stringResource(R.string.trip_auto_pickup, minutes) to stringResource(R.string.trip_wait_at, leg.from)
        } else {
            stringResource(R.string.leg_auto, leg.to) to stringResource(R.string.trip_drop_in, minutes)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeBadge(leg.mode, size = 48.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BoardingPrompt(route: String, stop: String, onBoarded: () -> Unit, onDidNotCome: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(AttentionAmber.copy(alpha = 0.14f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.trip_board_question, route), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.trip_wait_at, stop),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onBoarded,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
            ) { Text(stringResource(R.string.trip_boarded)) }
            OutlinedButton(onClick = onDidNotCome, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.trip_did_not_come), color = AttentionAmber)
            }
        }
    }
}

@Composable
private fun StepRow(leg: Leg, done: Boolean, current: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ElectricLime, modifier = Modifier.size(28.dp))
            } else {
                ModeBadge(leg.mode, size = 28.dp)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                legTitle(leg),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                clock(leg.startMin) + " · " + stringResource(R.string.journey_minutes, leg.waitMin + leg.moveMin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (leg.fare > 0) Text(stringResource(R.string.journey_fare, leg.fare), style = MaterialTheme.typography.titleSmall)
        if (current) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ElectricLime),
            )
        }
    }
}

/** Fare and time actually spent, for the arrival summary. */
fun Journey.fareLegs(): List<Leg> = legs.filter { it.fare > 0 }
