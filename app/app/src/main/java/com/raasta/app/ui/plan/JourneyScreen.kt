package com.raasta.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.raasta.app.planner.VersusOriginal
import com.raasta.app.ui.NoRecovery
import com.raasta.app.ui.Problem
import com.raasta.app.ui.Recovery
import com.raasta.app.ui.RecoveryResult
import com.raasta.app.ui.StatusReason
import com.raasta.app.ui.TripHealth
import com.raasta.app.ui.TripProgress
import com.raasta.app.ui.TripStatus
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapButton
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.DangerRed
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.ErrorOnDark
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class CameraMode { FOLLOW, FREE, OVERVIEW }

private val Speeds = listOf(1f, 2f, 5f, 10f)

/**
 * The journey in progress: the current step with directions, the journey's status, and
 * "Report a problem", which plans new options from where the rider is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyScreen(vm: TripViewModel, onArrived: () -> Unit, onEnded: () -> Unit) {
    val journey = vm.trip ?: return
    val progress = vm.progress(journey, vm.tripMinute)
    val leg = journey.legs[progress.legIndex]
    val status = vm.status(journey, progress)
    var cameraMode by remember { mutableStateOf(CameraMode.FOLLOW) }
    var overviewToken by remember { mutableIntStateOf(0) }
    var confirmEnd by remember { mutableStateOf(false) }
    var choosingProblem by remember { mutableStateOf(false) }
    var sosOpen by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<RecoveryResult?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(vm.arrived) { if (vm.arrived) onArrived() }
    BackHandler { confirmEnd = true }

    val position = progress.position
    val pins = journeyPins(vm, journey, startLabel = null, endLabel = vm.destinationName.substringBefore(",")) +
        listOfNotNull(position?.let { MapPin("me", it, PinStyle.YOU) })
    val camera = when {
        cameraMode == CameraMode.FOLLOW && position != null -> MapCamera.Follow(position, 16.5)
        cameraMode == CameraMode.OVERVIEW -> MapCamera.Fit(journeyPoints(vm, journey), overviewToken)
        else -> MapCamera.Free
    }
    val report = { problem: Problem ->
        choosingProblem = false
        recovery = vm.recover(problem)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.58f
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
                                    vm.destinationName,
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
                    StatusRow(status, journey)

                    val boarding = vm.awaitingBoarding
                    if (boarding != null) {
                        val busLeg = journey.legs[boarding]
                        BoardingPrompt(
                            route = displayRoute(busLeg.route.orEmpty()),
                            stop = busLeg.from,
                            onBoarded = vm::confirmBoarded,
                            onDidNotCome = { report(Problem.BUS_DID_NOT_COME) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { sosOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.sos_button), fontWeight = FontWeight.Bold)
                        }
                        if (leg.mode == Mode.WALK) {
                            val from = vm.places.locate(leg.from)
                            val to = vm.places.locate(leg.to)
                            if (from != null && to != null) {
                                OutlinedButton(
                                    onClick = {
                                        uriHandler.openUri(
                                            "https://www.google.com/maps/dir/?api=1&origin=${from.lat},${from.lon}" +
                                                "&destination=${to.lat},${to.lon}&travelmode=walking",
                                        )
                                    },
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.trip_directions))
                                }
                            }
                        }
                        OutlinedButton(onClick = { choosingProblem = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = AttentionAmber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.trip_report_problem), color = AttentionAmber)
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

    if (sosOpen) {
        val here = position ?: vm.places.locate(leg.from)
        SosSheet(
            journey = journey,
            destination = vm.destinationName.substringBefore(","),
            at = here,
            stop = here?.let { vm.nearbyStop(it) },
            onDismiss = { sosOpen = false },
        )
    }

    if (choosingProblem) {
        ModalBottomSheet(
            onDismissRequest = { choosingProblem = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.problem_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.problem_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Problem.entries.forEach { problem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { report(problem) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = AttentionAmber)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(problemLabel(problem)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    recovery?.let { result ->
        ModalBottomSheet(
            onDismissRequest = { recovery = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            when (result) {
                is RecoveryResult.None -> NoOptions(result.reason, onClose = { recovery = null })
                is RecoveryResult.Options -> RecoveryOptions(
                    vm,
                    result.recovery,
                    onPick = { option ->
                        vm.reroute(vm.chosen(option))
                        recovery = null
                        cameraMode = CameraMode.FOLLOW
                        val message = context.getString(R.string.trip_rerouted)
                        scope.launch { snackbar.showSnackbar(message) }
                    },
                    onKeep = { recovery = null },
                )
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

/** Monitoring result: on track, needs a check, or at risk, with what was checked. */
@Composable
private fun StatusRow(status: TripStatus, journey: Journey) {
    val (color, title) = when (status.health) {
        TripHealth.ON_TRACK -> SuccessGreen to stringResource(R.string.status_on_track)
        TripHealth.CHECK_NEEDED -> AttentionAmber to stringResource(R.string.status_check)
        TripHealth.AT_RISK -> ErrorOnDark to stringResource(R.string.status_at_risk)
    }
    val detail = when (status.reason) {
        null -> stringResource(R.string.status_on_track_detail)
        StatusReason.BUS_DUE -> stringResource(R.string.status_bus_due)
        StatusReason.LAST_METRO -> stringResource(R.string.status_last_metro)
        StatusReason.REPORTED_PROBLEM -> stringResource(R.string.status_reported, journeyTitle(journey.kind, journey.busRoute))
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** New options from where the rider is, compared with their original plan and ranked by their preferences. */
@Composable
private fun RecoveryOptions(vm: TripViewModel, recovery: Recovery, onPick: (Journey) -> Unit, onKeep: () -> Unit) {
    val options = recovery.result.ranked.filter { it.journey.waitMin <= MAX_RECOVERY_WAIT_MIN }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.recovery_title, stringResource(problemLabel(recovery.problem))),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            stringResource(R.string.recovery_from, recovery.fromName, clock(vm.tripMinute.toInt())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            stringResource(R.string.recovery_prefs_kept, preferencesText(vm.preferences)),
            style = MaterialTheme.typography.bodySmall,
            color = ElectricLime,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        recovery.failedRoute?.let { route ->
            if (options.isNotEmpty()) RecommendationBanner(recovery.result, route)
        }
        if (options.isEmpty()) {
            Text(stringResource(R.string.recovery_none_now), style = MaterialTheme.typography.bodyLarge)
        }
        options.forEachIndexed { i, option ->
            RouteCard(
                option = option,
                tradeoffs = vm.tradeoffs(option, options),
                recommended = i == 0,
                selected = i == 0,
                onClick = { onPick(option.journey) },
                safetyScore = vm.safetyScore(option.journey),
            )
            vm.versusOriginal(option.journey)?.let { OriginalLine(it) }
        }
        TextButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.recovery_keep))
        }
    }
}

@Composable
private fun OriginalLine(versus: VersusOriginal) {
    val time = when {
        versus.lateMin > 0 -> stringResource(R.string.vs_original_later, versus.lateMin)
        versus.lateMin < 0 -> stringResource(R.string.vs_original_earlier, versus.lateMin.absoluteValue)
        else -> stringResource(R.string.vs_original_same_time)
    }
    val fare = when {
        versus.extraFare > 0 -> stringResource(R.string.vs_original_more, versus.extraFare)
        versus.extraFare < 0 -> stringResource(R.string.vs_original_less, versus.extraFare.absoluteValue)
        else -> stringResource(R.string.vs_original_same_fare)
    }
    Text(
        stringResource(R.string.vs_original, "$time · $fare"),
        style = MaterialTheme.typography.bodySmall,
        color = if (versus.lateMin > 0) AttentionAmber else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun NoOptions(reason: NoRecovery, onClose: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.recovery_none_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                when (reason) {
                    NoRecovery.ON_BOARD -> R.string.recovery_none_on_board
                    NoRecovery.LAST_LEG -> R.string.recovery_none_last_leg
                    NoRecovery.NOT_IN_DATA -> R.string.recovery_none_data
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
        ) { Text(stringResource(R.string.trip_keep_going)) }
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
            val stops = (stations.indexOfFirst { it.name == leg.to } - stations.indexOfFirst { it.name == leg.from }).absoluteValue
            stringResource(R.string.trip_on_metro, leg.route.orEmpty(), leg.to) to stringResource(R.string.trip_get_off_stops, minutes, stops)
        }
        Mode.AUTO -> if (progress.waiting) {
            stringResource(R.string.trip_auto_pickup, minutes) to stringResource(R.string.trip_wait_at, leg.from)
        } else {
            stringResource(R.string.leg_auto, leg.to) to stringResource(R.string.trip_auto_arrive_in, minutes)
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
            stringResource(R.string.trip_board_why, stop),
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

private const val MAX_RECOVERY_WAIT_MIN = 90
