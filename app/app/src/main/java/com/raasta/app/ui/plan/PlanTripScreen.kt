package com.raasta.app.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.Basis
import com.raasta.app.planner.Caution
import com.raasta.app.planner.Confidence
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.Highlight
import com.raasta.app.planner.JourneyKind
import com.raasta.app.planner.JourneyPlanner
import com.raasta.app.planner.Leg
import com.raasta.app.planner.MINUTES_PER_DAY
import com.raasta.app.planner.Mode
import com.raasta.app.planner.Priority
import com.raasta.app.planner.RankedJourney
import com.raasta.app.planner.Unavailable
import com.raasta.app.planner.plan
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ErrorOnDark
import com.raasta.app.ui.theme.MetroAqua
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.SuccessGreen
import java.util.Calendar

/** Departure presets; NOW reads the phone clock. */
enum class DepartureChoice(val minuteOfDay: Int?) {
    NOW(null),
    EVENING(18 * 60),
    NIGHT(21 * 60 + 30),
    LATE_NIGHT(23 * 60 + 30),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTripScreen(corridor: Corridor, onBack: () -> Unit) {
    val planner = remember(corridor) { JourneyPlanner(corridor) }
    var originId by rememberSaveable { mutableStateOf(corridor.origins.first().id) }
    var destinationId by rememberSaveable { mutableStateOf(corridor.destinations.first().id) }
    var departure by rememberSaveable { mutableStateOf(DepartureChoice.NIGHT) }
    var priority by rememberSaveable { mutableStateOf(Priority.BALANCED) }
    var expandedIndex by rememberSaveable { mutableStateOf(0) }
    var showSources by rememberSaveable { mutableStateOf(false) }

    val nowMinute = remember(departure) { currentMinuteOfDay() }
    val departMin = departure.minuteOfDay ?: nowMinute
    val result = remember(originId, destinationId, departMin, priority) {
        planner.plan(originId, destinationId, departMin, priority)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_plan_title)) },
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
                    text = stringResource(R.string.plan_corridor_note, corridor.corridor, corridor.generatedOn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                PlaceSelector(
                    label = stringResource(R.string.plan_from),
                    selectedName = corridor.origin(originId).name,
                    options = corridor.origins.map { it.id to it.name },
                    onSelect = { originId = it; expandedIndex = 0 },
                )
            }
            item {
                PlaceSelector(
                    label = stringResource(R.string.plan_to),
                    selectedName = corridor.destination(destinationId).name,
                    options = corridor.destinations.map { it.id to it.name },
                    onSelect = { destinationId = it; expandedIndex = 0 },
                )
            }
            item {
                ChipGroup(
                    title = stringResource(R.string.plan_leave_at),
                    options = DepartureChoice.entries,
                    selected = departure,
                    label = { departureLabel(it, nowMinute) },
                    onSelect = { departure = it; expandedIndex = 0 },
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

@Composable
internal fun PlaceSelector(
    label: String,
    selectedName: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selectedName, style = MaterialTheme.typography.titleMedium)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

@Composable
internal fun <T> ChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
internal fun JourneyCard(rank: Int, ranked: RankedJourney, expanded: Boolean, onToggle: () -> Unit) {
    val journey = ranked.journey
    val best = rank == 1
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (best) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (best) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(rank, best)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        journeyTitle(journey.kind, journey.busRoute, journey.retriesFailedRoute),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.journey_arrive, clock(journey.departMin), clock(journey.arriveMin)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.journey_minutes, journey.totalMin), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.journey_fare, journey.fare),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(R.string.journey_metrics, journey.waitMin, distance(journey.walkM), journey.transfers),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ConfidenceTag(journey.confidence)
                ranked.highlights.sortedBy { it.ordinal }.forEach { highlight ->
                    Tag(stringResource(highlightLabel(highlight)), MaterialTheme.colorScheme.primary, outlined = true)
                }
            }
            journey.cautions.distinct().forEach { CautionRow(it) }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                journey.legs.forEach { LegRow(it) }
            }
            Text(
                stringResource(if (expanded) R.string.journey_hide_steps else R.string.journey_show_steps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun RankBadge(rank: Int, best: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (best) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(rank.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun LegRow(leg: Leg) {
    Row(verticalAlignment = Alignment.Top) {
        ModeTag(leg.mode)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(legTitle(leg), style = MaterialTheme.typography.bodyLarge)
            val parts = buildList {
                add(clock(leg.startMin))
                if (leg.waitMin > 0) add(stringResource(R.string.leg_wait, leg.waitMin))
                add(stringResource(R.string.journey_minutes, leg.moveMin))
                if (leg.distanceM != null && leg.distanceM > 0) add(distance(leg.distanceM))
            }
            val timing = parts.joinToString(" · ")
            Text(timing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(stringResource(R.string.basis_time, stringResource(basisLabel(leg.timeBasis))), basisColor(leg.timeBasis), outlined = true)
                leg.fareBasis?.let {
                    Tag(stringResource(R.string.basis_fare, stringResource(basisLabel(it))), basisColor(it), outlined = true)
                }
            }
        }
        if (leg.fare > 0) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.journey_fare, leg.fare), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun ModeTag(mode: Mode) {
    val (label, color) = when (mode) {
        Mode.WALK -> R.string.mode_walk to MaterialTheme.colorScheme.onSurfaceVariant
        Mode.BUS -> R.string.mode_bus to AttentionAmber
        Mode.METRO -> R.string.mode_metro to MetroAqua
        Mode.AUTO -> R.string.mode_auto to SuccessGreen
    }
    Surface(color = color, contentColor = Obsidian, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(56.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
internal fun Tag(text: String, color: Color, outlined: Boolean = false) {
    Surface(
        color = if (outlined) Color.Transparent else color,
        contentColor = if (outlined) color else Obsidian,
        shape = MaterialTheme.shapes.extraSmall,
        border = if (outlined) BorderStroke(1.dp, color.copy(alpha = 0.6f)) else null,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
internal fun ConfidenceTag(confidence: Confidence) {
    val (label, color) = when (confidence) {
        Confidence.HIGH -> R.string.confidence_high to SuccessGreen
        Confidence.MEDIUM -> R.string.confidence_medium to AttentionAmber
        Confidence.LOW -> R.string.confidence_low to ErrorOnDark
    }
    Tag(stringResource(label), color)
}

@Composable
internal fun CautionRow(caution: Caution) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AttentionAmber, modifier = Modifier.size(18.dp))
        Text(cautionText(caution), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun UnavailableCard(unavailable: List<Unavailable>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.unavailable_header), style = MaterialTheme.typography.titleSmall)
            unavailable.forEach {
                Text(
                    stringResource(R.string.unavailable_metro, journeyTitle(it.kind, it.busRoute), it.metroOpensAt, it.metroClosesBy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SourcesCard(corridor: Corridor, expanded: Boolean, onToggle: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sources_header), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onToggle) {
                    Text(stringResource(if (expanded) R.string.sources_hide else R.string.sources_show))
                }
            }
            if (expanded) {
                corridor.sources.forEach { source ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(source.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(source.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.source_retrieved, source.retrieved) + " · " + source.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(source.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun journeyTitle(kind: JourneyKind, busRoute: String?, retriesFailedRoute: Boolean = false): String = when (kind) {
    JourneyKind.BUS_METRO -> stringResource(
        if (retriesFailedRoute) R.string.journey_keep_waiting else R.string.journey_bus_metro,
        displayRoute(busRoute.orEmpty()),
    )
    JourneyKind.AUTO_METRO -> stringResource(R.string.journey_auto_metro)
    JourneyKind.AUTO_DIRECT -> stringResource(R.string.journey_auto_direct)
}

@Composable
internal fun legTitle(leg: Leg): String = when (leg.mode) {
    Mode.WALK -> stringResource(R.string.leg_walk, leg.to)
    Mode.BUS -> stringResource(R.string.leg_bus, displayRoute(leg.route.orEmpty()), leg.to)
    Mode.METRO -> stringResource(R.string.leg_metro, leg.route.orEmpty(), leg.to)
    Mode.AUTO -> stringResource(R.string.leg_auto, leg.to)
}

@Composable
internal fun cautionText(caution: Caution): String = when (caution) {
    is Caution.TimetableEnds -> stringResource(R.string.caution_timetable_ends, displayRoute(caution.route), caution.lastDeparture)
    is Caution.BusFareUnknown -> stringResource(R.string.caution_bus_fare_unknown, displayRoute(caution.route))
    Caution.MetroLastTrainUncertain -> stringResource(R.string.caution_metro_last_train)
    Caution.AutoAvailabilityUnknown -> stringResource(R.string.caution_auto_availability)
    Caution.AutoNightSurcharge -> stringResource(R.string.caution_auto_night)
    is Caution.FailedRouteMayRepeat -> stringResource(R.string.caution_failed_route, displayRoute(caution.route))
}

@Composable
internal fun departureLabel(choice: DepartureChoice, nowMinute: Int): String = when (choice) {
    DepartureChoice.NOW -> stringResource(R.string.leave_now, clock(nowMinute))
    DepartureChoice.EVENING -> stringResource(R.string.leave_1800)
    DepartureChoice.NIGHT -> stringResource(R.string.leave_2130)
    DepartureChoice.LATE_NIGHT -> stringResource(R.string.leave_2330)
}

@StringRes
internal fun priorityLabel(priority: Priority): Int = when (priority) {
    Priority.BALANCED -> R.string.priority_balanced
    Priority.CHEAPEST -> R.string.priority_cheapest
    Priority.LEAST_WAITING -> R.string.priority_least_waiting
    Priority.FEWEST_CHANGES -> R.string.priority_fewest_changes
}

@StringRes
internal fun highlightLabel(highlight: Highlight): Int = when (highlight) {
    Highlight.FASTEST -> R.string.highlight_fastest
    Highlight.CHEAPEST -> R.string.highlight_cheapest
    Highlight.LEAST_WAITING -> R.string.highlight_least_waiting
    Highlight.FEWEST_CHANGES -> R.string.highlight_fewest_changes
}

@StringRes
internal fun basisLabel(basis: Basis): Int = when (basis) {
    Basis.TIMETABLE -> R.string.basis_timetable
    Basis.OFFICIAL_FARE -> R.string.basis_official_fare
    Basis.REPORTED -> R.string.basis_reported
    Basis.ESTIMATE -> R.string.basis_estimate
    Basis.MAP_ROUTE -> R.string.basis_map_route
}

@Composable
internal fun basisColor(basis: Basis): Color = when (basis) {
    Basis.TIMETABLE, Basis.OFFICIAL_FARE, Basis.MAP_ROUTE -> SuccessGreen
    Basis.REPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
    Basis.ESTIMATE -> AttentionAmber
}

@Composable
internal fun distance(metres: Int): String =
    if (metres < 1000) stringResource(R.string.distance_m, metres)
    else stringResource(R.string.distance_km, metres / 1000.0)

/** Minutes after midnight as a 12-hour clock, e.g. 1302 -> "9:42 PM". */
@Composable
internal fun clock(minute: Int): String {
    val dayMinute = Math.floorMod(minute, MINUTES_PER_DAY)
    val h = dayMinute / 60
    val text = "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, dayMinute % 60, if (h < 12) "AM" else "PM")
    return if (minute >= MINUTES_PER_DAY) stringResource(R.string.clock_next_day, text) else text
}

/** "METRO SHUTTLE 20" -> "Metro Shuttle 20"; numeric routes unchanged. */
internal fun displayRoute(route: String): String =
    if (route.any(Char::isLetter) && route == route.uppercase()) {
        route.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    } else route

internal fun currentMinuteOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}
