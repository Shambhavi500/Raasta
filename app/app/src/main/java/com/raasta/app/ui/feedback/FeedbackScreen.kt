package com.raasta.app.ui.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.MapCamera
import com.raasta.app.map.RaastaMap
import com.raasta.app.planner.Condition
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Mode
import com.raasta.app.store.TripRating
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.common.MapSheet
import com.raasta.app.ui.plan.ModeBadge
import com.raasta.app.ui.plan.SectionTitle
import com.raasta.app.ui.plan.clock
import com.raasta.app.ui.plan.conditionLabel
import com.raasta.app.ui.plan.journeyLines
import com.raasta.app.ui.plan.journeyPins
import com.raasta.app.ui.plan.journeyPoints
import com.raasta.app.ui.plan.legTitle
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian

/** Arrival summary, then optional feedback: how it went and any route conditions to report. */
@Composable
fun FeedbackScreen(vm: TripViewModel, onPlanAnother: () -> Unit, onReports: () -> Unit) {
    val journey = vm.trip ?: return
    val original = vm.original ?: journey
    var rating by rememberSaveable { mutableStateOf<TripRating?>(null) }
    // Reported condition -> place it was at.
    val reports = remember { mutableStateMapOf<Condition, String>() }
    var sent by rememberSaveable { mutableStateOf(false) }
    var sentReports by rememberSaveable { mutableStateOf(0) }
    val places = (vm.journeyPlaces(original) + vm.journeyPlaces(journey)).distinct()
    val finish = { vm.planAnother(); onPlanAnother() }
    BackHandler(onBack = finish)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight * 0.72f
        RaastaMap(
            pins = journeyPins(vm, journey, startLabel = null, endLabel = vm.destinationName.substringBefore(",")),
            lines = journeyLines(vm, journey),
            camera = MapCamera.Fit(journeyPoints(vm, journey)),
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight - sheetMax + 120.dp),
        )
        MapSheet(
            Modifier
                .align(Alignment.BottomCenter)
                .heightIn(max = sheetMax),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ElectricLime),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Obsidian, modifier = Modifier.size(32.dp))
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.arrived_title), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.arrived_at, vm.destinationName, clock(journey.arriveMin)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    SummaryStat(stringResource(R.string.journey_minutes, journey.arriveMin - vm.tripStartMinute), stringResource(R.string.arrived_time), Modifier.weight(1f))
                    SummaryStat(stringResource(R.string.journey_fare, vm.paidSoFar()), stringResource(R.string.arrived_fare), Modifier.weight(1f))
                    SummaryStat(journey.transfers.toString(), stringResource(R.string.arrived_changes), Modifier.weight(1f))
                }
                if (vm.reroutes > 0) {
                    Text(
                        pluralStringResource(R.plurals.arrived_rerouted, vm.reroutes, vm.reroutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricLime,
                    )
                }
                journey.legs.filter { it.fare > 0 }.forEach { leg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModeBadge(leg.mode, size = 28.dp)
                        Text(
                            legTitle(leg),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        )
                        Text(stringResource(R.string.journey_fare, leg.fare), style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (sent) {
                    SectionTitle(stringResource(R.string.feedback_thanks))
                    Text(
                        if (sentReports > 0) {
                            pluralStringResource(R.plurals.feedback_pending, sentReports, sentReports)
                        } else {
                            stringResource(R.string.feedback_saved)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onReports, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reports_title))
                    }
                } else {
                    SectionTitle(stringResource(R.string.feedback_how))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripRating.entries.forEach { option ->
                            Choice(stringResource(ratingLabel(option)), rating == option) {
                                rating = if (rating == option) null else option
                            }
                        }
                    }
                    SectionTitle(stringResource(R.string.feedback_report))
                    Text(
                        stringResource(R.string.feedback_report_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Condition.entries.forEach { condition ->
                            Choice(stringResource(conditionLabel(condition)), condition in reports) {
                                if (condition in reports) reports -= condition
                                else reports[condition] = defaultPlace(condition, original, places)
                            }
                        }
                    }
                    Condition.entries.filter { it in reports }.forEach { condition ->
                        Text(
                            stringResource(R.string.feedback_where, stringResource(conditionLabel(condition))),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            places.forEach { place ->
                                Choice(place, reports[condition] == place) { reports[condition] = place }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            vm.submitFeedback(
                                rating ?: TripRating.OKAY,
                                reports.map { (condition, place) -> Triple(condition, place, routeAt(condition, place, original, journey)) },
                            )
                            sentReports = reports.size
                            sent = true
                        },
                        enabled = rating != null || reports.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                    ) {
                        Text(stringResource(R.string.feedback_send), fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = finish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feedback_plan_another), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricLime, selectedLabelColor = Obsidian),
    )
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The most likely place for a report: the bus stop for a missing bus, the metro for a missed connection. */
private fun defaultPlace(condition: Condition, journey: Journey, places: List<String>): String = when (condition) {
    Condition.BUS_LATE_OR_MISSING -> journey.legs.firstOrNull { it.mode == Mode.BUS }?.from
    Condition.MISSED_CONNECTION -> journey.legs.firstOrNull { it.mode == Mode.METRO }?.from
    else -> null
} ?: places.first()

/** The bus route a report is about, when it is about a bus at that stop. */
private fun routeAt(condition: Condition, place: String, vararg journeys: Journey): String? {
    if (condition != Condition.BUS_LATE_OR_MISSING) return null
    return journeys.asSequence().flatMap { it.legs }.firstOrNull { it.mode == Mode.BUS && it.from == place }?.route
}

private fun ratingLabel(rating: TripRating): Int = when (rating) {
    TripRating.GOOD -> R.string.rating_good
    TripRating.OKAY -> R.string.rating_okay
    TripRating.POOR -> R.string.rating_poor
}
