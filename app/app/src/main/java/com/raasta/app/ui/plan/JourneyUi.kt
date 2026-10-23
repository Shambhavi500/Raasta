package com.raasta.app.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.Basis
import com.raasta.app.planner.Caution
import com.raasta.app.planner.Confidence
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.Highlight
import com.raasta.app.planner.Journey
import com.raasta.app.planner.JourneyKind
import com.raasta.app.planner.Leg
import com.raasta.app.planner.MINUTES_PER_DAY
import com.raasta.app.planner.Mode
import com.raasta.app.planner.PlanResult
import com.raasta.app.planner.Source
import com.raasta.app.planner.versusWaiting
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.ErrorOnDark
import com.raasta.app.ui.theme.MetroAqua
import com.raasta.app.ui.theme.MutedOnDark
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.SuccessGreen
import kotlin.math.absoluteValue

fun modeColor(mode: Mode): Color = when (mode) {
    Mode.WALK -> MutedOnDark
    Mode.BUS -> AttentionAmber
    Mode.METRO -> MetroAqua
    Mode.AUTO -> SuccessGreen
}

fun modeIcon(mode: Mode): ImageVector = when (mode) {
    Mode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    Mode.BUS -> Icons.Filled.DirectionsBus
    Mode.METRO -> Icons.Filled.DirectionsSubway
    Mode.AUTO -> Icons.Filled.ElectricRickshaw
}

/** A round, mode-coloured icon. */
@Composable
fun ModeBadge(mode: Mode, size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(modeColor(mode)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(modeIcon(mode), contentDescription = null, tint = Obsidian, modifier = Modifier.size(size * 0.6f))
    }
}

/** The modes of a journey in order, e.g. walk > bus > metro > walk. */
@Composable
fun ModeStrip(journey: Journey) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        journey.legs.forEachIndexed { i, leg ->
            if (i > 0) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (leg.mode == Mode.WALK) {
                Icon(modeIcon(leg.mode), contentDescription = null, tint = MutedOnDark, modifier = Modifier.size(18.dp))
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(modeColor(leg.mode))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(modeIcon(leg.mode), contentDescription = null, tint = Obsidian, modifier = Modifier.size(15.dp))
                    leg.route?.takeIf { leg.mode == Mode.BUS }?.let {
                        Text(
                            shortRoute(it),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Obsidian,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Every step of a journey, with where each number comes from. */
@Composable
fun JourneySteps(journey: Journey) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        journey.cautions.distinct().forEach { CautionRow(it) }
        if (journey.cautions.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        journey.legs.forEach { LegRow(it) }
        Text(
            stringResource(R.string.journey_metrics, journey.waitMin, distance(journey.walkM), journey.transfers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LegRow(leg: Leg) {
    Row(verticalAlignment = Alignment.Top) {
        ModeBadge(leg.mode)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(legTitle(leg), style = MaterialTheme.typography.bodyLarge)
            val parts = buildList {
                add(clock(leg.startMin))
                if (leg.waitMin > 0) add(stringResource(R.string.leg_wait, leg.waitMin))
                add(stringResource(R.string.journey_minutes, leg.moveMin))
                if (leg.distanceM != null && leg.distanceM > 0) add(distance(leg.distanceM))
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Tag(stringResource(label), color, outlined = true)
}

@Composable
internal fun CautionRow(caution: Caution) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AttentionAmber, modifier = Modifier.size(18.dp))
        Text(cautionText(caution), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SourcesList(corridor: Corridor) = SourcesList(corridor.sources)

@Composable
internal fun SourcesList(sources: List<Source>) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sources.forEach { source ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { uriHandler.openUri(source.url) }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(source.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(source.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.source_retrieved, source.retrieved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The one-line answer to "should I keep waiting?", built from the ranked options. */
@Composable
fun RecommendationBanner(result: PlanResult, failedRoute: String, modifier: Modifier = Modifier) {
    val best = result.ranked.firstOrNull()?.journey ?: return
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
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pick a departure time, or go back to leaving now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveTimeDialog(initialMinute: Int, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_dialog_title)) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = ElectricLime,
                    timeSelectorSelectedContainerColor = ElectricLime,
                    timeSelectorSelectedContentColor = Obsidian,
                    periodSelectorSelectedContainerColor = ElectricLime,
                    periodSelectorSelectedContentColor = Obsidian,
                    clockDialSelectedContentColor = Obsidian,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.time_dialog_set)) }
        },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.time_dialog_now)) }
        },
    )
}

@Composable
internal fun journeyTitle(kind: JourneyKind, busRoute: String?, retriesFailedRoute: Boolean = false): String = when (kind) {
    JourneyKind.BUS_METRO -> stringResource(
        if (retriesFailedRoute) R.string.journey_keep_waiting else R.string.journey_bus_metro,
        displayRoute(busRoute.orEmpty()),
    )
    JourneyKind.AUTO_METRO -> stringResource(R.string.journey_auto_metro)
    JourneyKind.AUTO_DIRECT -> stringResource(R.string.journey_auto_direct)
    JourneyKind.METRO -> stringResource(R.string.journey_metro)
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
    Caution.AutoDistanceEstimated -> stringResource(R.string.caution_auto_distance)
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

/** "METRO SHUTTLE 20" -> "MS20" for tight spaces. */
internal fun shortRoute(route: String): String =
    if (route.contains(' ')) route.split(" ").let { parts -> parts.dropLast(1).joinToString("") { it.take(1) } + parts.last() }
    else route
