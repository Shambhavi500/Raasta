package com.raasta.app.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.map.HeatSpot
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Mode
import com.raasta.app.safety.CrimeLayer
import com.raasta.app.safety.CrimeLevel
import com.raasta.app.safety.CrimeZone
import com.raasta.app.safety.Exposure
import com.raasta.app.safety.JourneyRoutes
import com.raasta.app.safety.RouteVariant
import com.raasta.app.safety.crimeLevel
import com.raasta.app.ui.SafetyStatus
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.DangerRed
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.SuccessGreen
import kotlin.math.exp
import kotlin.math.roundToInt

private val PeopleCyan = Color(0xFF22D3EE)
private val CrimeOrange = Color(0xFFF97316)
private val CrimeYellow = Color(0xFFFACC15)

fun crimeColor(level: CrimeLevel): Color = when (level) {
    CrimeLevel.HIGH -> DangerRed
    CrimeLevel.MEDIUM -> CrimeOrange
    CrimeLevel.LOW -> CrimeYellow
}

fun safetyColor(score: Int): Color = when {
    score >= 80 -> SuccessGreen
    score >= 60 -> AttentionAmber
    else -> DangerRed
}

/** Heatmap spots for the layers the rider has switched on, at [hour]. */
fun heatSpots(vm: TripViewModel, hour: Int): List<HeatSpot> {
    val ready = vm.safetyStatus as? SafetyStatus.Ready ?: return emptyList()
    return buildList {
        if (vm.showCrowdHeat) {
            ready.model.activity(hour).forEach { cell ->
                val strength = 0.12 + 0.5 * (1 - exp(-cell.openPlaces / 6.0))
                add(HeatSpot(cell.center, 230.0, PeopleCyan, strength.toFloat()))
            }
        }
        if (vm.showCrimeHeat) {
            ready.crime?.zones?.forEach { zone ->
                add(HeatSpot(zone.center, zone.drawRadiusM, crimeColor(crimeLevel(zone.intensity)), 0.65f, id = zone.id))
            }
        }
    }
}

/** Metres of a journey on streets (walking and auto). */
private fun streetMetres(journey: Journey): Int =
    journey.legs.filter { it.mode == Mode.WALK || it.mode == Mode.AUTO }.sumOf { it.distanceM ?: 0 }

private fun percent(share: Double): Int = (share * 100).roundToInt()

/** "Shortest" and "Safety route" cards for the chosen option, with why the safety route is safer. */
@Composable
fun SafetyRoutePicker(vm: TripViewModel, journey: Journey, modifier: Modifier = Modifier) {
    val status = vm.safetyStatus
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            status is SafetyStatus.Unavailable -> Note(stringResource(R.string.safety_unavailable))
            status is SafetyStatus.Loading || journey !in vm.comparisons -> Checking()
            else -> {
                val routes = vm.comparisons[journey]
                when {
                    routes == null -> Note(stringResource(R.string.safety_no_streets))
                    routes.same -> SameRoute(routes.shortestExposure.score)
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RouteVariant.entries.forEach { variant ->
                                VariantCard(
                                    routes = routes,
                                    variant = variant,
                                    selected = vm.routeVariant == variant,
                                    onClick = { vm.routeVariant = variant },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Reasons(routes, hasCrime = (status as SafetyStatus.Ready).crime != null)
                    }
                }
            }
        }
        if (status is SafetyStatus.Ready) {
            HeatToggles(vm)
            val crime = status.crime
            Text(
                when {
                    crime == null || crime.isDemo -> stringResource(R.string.safety_note_demo)
                    else -> stringResource(R.string.safety_note_real, crime.data.source.title)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantCard(routes: JourneyRoutes, variant: RouteVariant, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val journey = routes.journey(variant)
    val exposure = routes.exposure(variant)
    val shape = RoundedCornerShape(16.dp)
    val (title, icon) = when (variant) {
        RouteVariant.SHORTEST -> stringResource(R.string.safety_shortest) to Icons.Filled.Speed
        RouteVariant.SAFEST -> stringResource(R.string.safety_safest) to Icons.Filled.Shield
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) BorderStroke(2.dp, ElectricLime) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) ElectricLime else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.safety_route_stats, journey.totalMin, distance(streetMetres(journey))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SafetyBadge(exposure)
    }
}

@Composable
private fun SafetyBadge(exposure: Exposure) {
    val color = safetyColor(exposure.score)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.safety_score, exposure.score), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun Reasons(routes: JourneyRoutes, hasCrime: Boolean) {
    val short = routes.shortestExposure
    val safe = routes.safestExposure
    val extra = routes.safest.arriveMin - routes.shortest.arriveMin
    val parts = buildList {
        if (hasCrime && (short.crimeZoneShare > 0.005 || safe.crimeZoneShare > 0.005)) {
            add(stringResource(R.string.safety_crime, percent(short.crimeZoneShare), percent(safe.crimeZoneShare)))
        }
        add(stringResource(R.string.safety_busy, percent(short.busyShare), percent(safe.busyShare)))
        add(if (extra > 0) stringResource(R.string.safety_extra_time, extra) else stringResource(R.string.safety_no_extra_time))
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SameRoute(score: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = safetyColor(score), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.safety_same, score), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Checking() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricLime)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.safety_checking), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** People and crime heatmap switches. */
@Composable
fun HeatToggles(vm: TripViewModel, modifier: Modifier = Modifier) {
    val ready = vm.safetyStatus as? SafetyStatus.Ready ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HeatChip(stringResource(R.string.heat_people), Icons.Filled.Groups, vm.showCrowdHeat) { vm.showCrowdHeat = !vm.showCrowdHeat }
        if (ready.crime != null) {
            HeatChip(
                stringResource(if (ready.crime.isDemo) R.string.heat_crime_demo else R.string.heat_crime),
                Icons.Filled.Warning,
                vm.showCrimeHeat,
            ) { vm.showCrimeHeat = !vm.showCrimeHeat }
        }
    }
}

@Composable
private fun HeatChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = ElectricLime,
            selectedLabelColor = Obsidian,
            selectedLeadingIconColor = Obsidian,
        ),
    )
}

/** Explains the colours of the layers that are switched on. */
@Composable
fun HeatLegend(vm: TripViewModel, hour: Int, modifier: Modifier = Modifier) {
    val ready = vm.safetyStatus as? SafetyStatus.Ready ?: return
    val crime = ready.crime
    if (!vm.showCrowdHeat && !(vm.showCrimeHeat && crime != null)) return
    Surface(
        modifier = modifier.widthIn(max = 260.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (vm.showCrimeHeat && crime != null) {
                Text(
                    if (crime.isDemo) stringResource(R.string.legend_crime_demo) else stringResource(R.string.legend_crime, crime.data.title),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (crime.isDemo) AttentionAmber else MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrimeLevel.entries.forEach { level -> LegendDot(crimeColor(level), stringResource(crimeLevelLabel(level))) }
                }
            }
            if (vm.showCrowdHeat) {
                LegendDot(PeopleCyan, stringResource(R.string.legend_people, clock(hour * 60)))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun crimeLevelLabel(level: CrimeLevel): Int = when (level) {
    CrimeLevel.LOW -> R.string.crime_low
    CrimeLevel.MEDIUM -> R.string.crime_medium
    CrimeLevel.HIGH -> R.string.crime_high
}

/** What a tapped crime zone is, where the data comes from and how far to trust it. */
@Composable
fun CrimeZoneCard(zone: CrimeZone, layer: CrimeLayer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val level = crimeLevel(zone.intensity)
    Surface(modifier = modifier.widthIn(max = 320.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(crimeColor(level)),
                )
                Spacer(Modifier.width(8.dp))
                Text(zone.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
            }
            Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.zone_level, stringResource(crimeLevelLabel(level))), style = MaterialTheme.typography.bodyMedium)
                zone.category?.let { Text(stringResource(R.string.zone_category, it), style = MaterialTheme.typography.bodySmall) }
                zone.reportedCases?.let { Text(stringResource(R.string.zone_cases, it), style = MaterialTheme.typography.bodySmall) }
                layer.data.period?.let { Text(stringResource(R.string.zone_period, it), style = MaterialTheme.typography.bodySmall) }
                Text(
                    layer.data.notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (layer.isDemo) AttentionAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.zone_source, layer.data.source.title, layer.data.source.retrieved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
