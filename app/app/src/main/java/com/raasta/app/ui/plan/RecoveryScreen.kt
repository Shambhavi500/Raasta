package com.raasta.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.JourneyPlanner
import com.raasta.app.planner.PlanResult
import com.raasta.app.planner.Priority
import com.raasta.app.planner.recover
import com.raasta.app.planner.versusWaiting
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian
import kotlin.math.absoluteValue

/** "Time now" presets; NOW reads the phone clock. */
enum class NowChoice(val minuteOfDay: Int?) {
    NOW(null),
    EVENING(18 * 60 + 40),
    NIGHT(21 * 60 + 50),
    LATE(22 * 60 + 40),
}

/** "My connection failed": the rider is at a stop, their bus did not come, what now? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(corridor: Corridor, onBack: () -> Unit) {
    val planner = remember(corridor) { JourneyPlanner(corridor) }
    val points = corridor.recoveryPoints
    var pointId by rememberSaveable { mutableStateOf(points.first().id) }
    var failedRoute by rememberSaveable { mutableStateOf(points.first().routes.first()) }
    var destinationId by rememberSaveable { mutableStateOf(corridor.destinations.first().id) }
    var now by rememberSaveable { mutableStateOf(NowChoice.NIGHT) }
    var priority by rememberSaveable { mutableStateOf(Priority.BALANCED) }
    var expandedIndex by rememberSaveable { mutableStateOf(0) }
    var showSources by rememberSaveable { mutableStateOf(false) }

    val point = corridor.recoveryPoint(pointId)
    val nowMinute = remember(now) { currentMinuteOfDay() }
    val nowMin = now.minuteOfDay ?: nowMinute
    val result = remember(pointId, failedRoute, destinationId, nowMin, priority) {
        planner.recover(pointId, destinationId, nowMin, failedRoute, priority)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_recover_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.recover_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                PlaceSelector(
                    label = stringResource(R.string.recover_at),
                    selectedName = point.name,
                    options = points.map { it.id to it.name },
                    onSelect = { id ->
                        pointId = id
                        failedRoute = corridor.recoveryPoint(id).routes.first()
                        expandedIndex = 0
                    },
                )
            }
            item {
                ChipGroup(
                    title = stringResource(R.string.recover_failed_bus),
                    options = point.routes,
                    selected = failedRoute,
                    label = { stringResource(R.string.recover_bus_route, displayRoute(it)) },
                    onSelect = { failedRoute = it; expandedIndex = 0 },
                )
            }
            item {
                PlaceSelector(
                    label = stringResource(R.string.recover_going_to),
                    selectedName = corridor.destination(destinationId).name,
                    options = corridor.destinations.map { it.id to it.name },
                    onSelect = { destinationId = it; expandedIndex = 0 },
                )
            }
            item {
                ChipGroup(
                    title = stringResource(R.string.recover_time_now),
                    options = NowChoice.entries,
                    selected = now,
                    label = { nowLabel(it, nowMinute) },
                    onSelect = { now = it; expandedIndex = 0 },
                )
            }
            item {
                ChipGroup(
                    title = stringResource(R.string.plan_priority),
                    options = Priority.entries,
                    selected = priority,
                    label = { stringResource(priorityLabel(it)) },
                    onSelect = { priority = it; expandedIndex = 0 },
                )
            }
            if (result.ranked.isNotEmpty()) {
                item { RecommendationBanner(result, failedRoute) }
            }
            item {
                Text(
                    text = if (result.ranked.isEmpty()) {
                        stringResource(R.string.plan_no_results)
                    } else {
                        stringResource(R.string.plan_results_header, result.ranked.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(result.ranked) { index, ranked ->
                JourneyCard(
                    rank = index + 1,
                    ranked = ranked,
                    expanded = expandedIndex == index,
                    onToggle = { expandedIndex = if (expandedIndex == index) -1 else index },
                )
            }
            if (result.unavailable.isNotEmpty()) {
                item { UnavailableCard(result.unavailable) }
            }
            item {
                SourcesCard(corridor, expanded = showSources, onToggle = { showSources = !showSources })
            }
        }
    }
}

/** The one-line answer to "should I keep waiting?", built from the ranked options. */
@Composable
private fun RecommendationBanner(result: PlanResult, failedRoute: String) {
    val best = result.ranked.first().journey
    val bestTitle = journeyTitle(best.kind, best.busRoute, best.retriesFailedRoute)
    val route = displayRoute(failedRoute)
    val versus = result.versusWaiting()

    val (title, body) = when {
        best.retriesFailedRoute ->
            stringResource(R.string.recover_title_wait, route) to
                stringResource(R.string.recover_wait_explained, route)
        versus == null ->
            stringResource(R.string.recover_title_generic) to
                stringResource(R.string.recover_best_generic, bestTitle, route)
        versus.minutesSooner <= 0 ->
            stringResource(R.string.recover_title_switch, route) to
                stringResource(R.string.recover_not_sooner, bestTitle, versus.minutesSooner.absoluteValue)
        versus.extraFare > 0 ->
            stringResource(R.string.recover_title_switch, route) to
                stringResource(R.string.recover_sooner_more, bestTitle, versus.minutesSooner, versus.extraFare)
        versus.extraFare < 0 ->
            stringResource(R.string.recover_title_switch, route) to
                stringResource(R.string.recover_sooner_less, bestTitle, versus.minutesSooner, versus.extraFare.absoluteValue)
        else ->
            stringResource(R.string.recover_title_switch, route) to
                stringResource(R.string.recover_sooner_same, bestTitle, versus.minutesSooner)
    }

    Surface(
        color = ElectricLime,
        contentColor = Obsidian,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun nowLabel(choice: NowChoice, nowMinute: Int): String = when (choice) {
    NowChoice.NOW -> stringResource(R.string.recover_now, clock(nowMinute))
    NowChoice.EVENING -> stringResource(R.string.recover_1840)
    NowChoice.NIGHT -> stringResource(R.string.recover_2150)
    NowChoice.LATE -> stringResource(R.string.recover_2240)
}
