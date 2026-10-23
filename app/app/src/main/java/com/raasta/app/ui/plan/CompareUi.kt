package com.raasta.app.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.Condition
import com.raasta.app.planner.Goal
import com.raasta.app.planner.Journey
import com.raasta.app.planner.Point
import com.raasta.app.planner.RankedJourney
import com.raasta.app.planner.Tradeoffs
import com.raasta.app.planner.TravelPreferences
import com.raasta.app.ui.Problem
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.SuccessGreen

/** What a route's pros and cons are measured against. */
enum class Versus { NEXT_BEST, RECOMMENDED }

/** One route in the comparison: what it is, its numbers, and why to pick it or not. */
@Composable
fun RouteCard(
    option: RankedJourney,
    tradeoffs: Tradeoffs,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    safetyScore: Int? = null,
    /** Shown above the pros and cons; null hides the comparison header. */
    versus: Versus? = if (recommended) Versus.NEXT_BEST else Versus.RECOMMENDED,
    title: String = journeyTitle(option.journey.kind, option.journey.busRoute, option.journey.retriesFailedRoute),
) {
    val journey = option.journey
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) BorderStroke(2.dp, ElectricLime) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (recommended) Tag(stringResource(R.string.compare_recommended), ElectricLime)
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ModeStrip(journey)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.journey_minutes, journey.totalMin), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.journey_fare, journey.fare),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.journey_arrive, clock(journey.departMin), clock(journey.arriveMin)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricsLine(journey)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            option.highlights.sortedBy { it.ordinal }.forEach {
                Tag(stringResource(highlightLabel(it)), ElectricLime, outlined = true)
            }
            ConfidenceTag(journey.confidence)
            safetyScore?.let { Tag(stringResource(R.string.safety_tag, it), safetyColor(it), outlined = true) }
        }
        if (tradeoffs.pros.isNotEmpty() || tradeoffs.cons.isNotEmpty()) {
            versus?.let {
                Text(
                    stringResource(if (it == Versus.NEXT_BEST) R.string.compare_vs_next else R.string.compare_vs_recommended),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TradeoffList(tradeoffs)
        }
    }
}

/** Waiting, walking and changes for a journey, in one line. */
@Composable
fun MetricsLine(journey: Journey) {
    Text(
        stringResource(R.string.journey_metrics, journey.waitMin, distance(journey.walkM), journey.transfers),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Advantages with a plus, disadvantages with a minus. */
@Composable
fun TradeoffList(tradeoffs: Tradeoffs, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tradeoffs.pros.forEach { PointRow(pointText(it), good = true) }
        tradeoffs.cons.forEach { PointRow(pointText(it), good = false) }
    }
}

@Composable
private fun PointRow(text: String, good: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (good) Icons.Filled.AddCircle else Icons.Filled.RemoveCircle,
            contentDescription = stringResource(if (good) R.string.point_pro else R.string.point_con),
            tint = if (good) SuccessGreen else AttentionAmber,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun pointText(point: Point): String = when (point) {
    is Point.Faster -> stringResource(R.string.point_faster, point.min)
    is Point.Slower -> stringResource(R.string.point_slower, point.min)
    is Point.Cheaper -> stringResource(R.string.point_cheaper, point.rupees)
    is Point.Pricier -> stringResource(R.string.point_pricier, point.rupees)
    is Point.LessWaiting -> stringResource(R.string.point_less_wait, point.min)
    is Point.MoreWaiting -> stringResource(R.string.point_more_wait, point.min)
    is Point.LessWalking -> stringResource(R.string.point_less_walk, distance(point.metres))
    is Point.MoreWalking -> stringResource(R.string.point_more_walk, distance(point.metres))
    is Point.FewerChanges -> pluralStringResource(R.plurals.point_fewer_changes, point.changes, point.changes)
    is Point.MoreChanges -> pluralStringResource(R.plurals.point_more_changes, point.changes, point.changes)
    is Point.SaferStreets -> stringResource(R.string.point_safer, point.score, point.otherScore)
    is Point.LessSafeStreets -> stringResource(R.string.point_less_safe, point.score, point.otherScore)
    is Point.ShorterDarkStretch -> stringResource(R.string.point_shorter_dark, point.metres, point.otherMetres)
    is Point.LongerDarkStretch -> stringResource(R.string.point_longer_dark, point.metres, point.otherMetres)
    is Point.MoreOpenPlaces -> stringResource(R.string.point_more_open, point.count, point.otherCount)
    is Point.FewerOpenPlaces -> stringResource(R.string.point_fewer_open, point.count, point.otherCount)
    Point.ReliableData -> stringResource(R.string.point_reliable)
    Point.MostlyEstimates -> stringResource(R.string.point_estimates)
    is Point.LongWait -> stringResource(R.string.point_long_wait, point.min)
    is Point.LongWalk -> stringResource(R.string.point_long_walk, distance(point.metres))
    is Point.Reported -> stringResource(R.string.point_reported, stringResource(conditionLabel(point.condition)), point.place)
}

/** "Priorities: Time, Cost · Safety-aware" */
@Composable
fun preferencesText(preferences: TravelPreferences): String {
    val goals = if (preferences.goals.isEmpty()) {
        stringResource(R.string.prefs_balanced)
    } else {
        Goal.entries.filter { it in preferences.goals }.map { stringResource(goalLabel(it)) }.joinToString(", ")
    }
    val extras = buildList {
        if (preferences.safetyAware) add(stringResource(R.string.prefs_safety_short))
        if (preferences.avoidLongWalks) add(stringResource(R.string.prefs_walks_short))
    }
    return (listOf(stringResource(R.string.prefs_summary, goals)) + extras).joinToString(" · ")
}

@StringRes
fun goalLabel(goal: Goal): Int = when (goal) {
    Goal.TIME -> R.string.goal_time
    Goal.COST -> R.string.goal_cost
    Goal.CONVENIENCE -> R.string.goal_convenience
    Goal.ACCESSIBILITY -> R.string.goal_accessibility
}

@StringRes
fun goalDescription(goal: Goal): Int = when (goal) {
    Goal.TIME -> R.string.goal_time_body
    Goal.COST -> R.string.goal_cost_body
    Goal.CONVENIENCE -> R.string.goal_convenience_body
    Goal.ACCESSIBILITY -> R.string.goal_accessibility_body
}

@StringRes
fun conditionLabel(condition: Condition): Int = when (condition) {
    Condition.BUS_LATE_OR_MISSING -> R.string.condition_bus_late
    Condition.MISSED_CONNECTION -> R.string.condition_missed
    Condition.CROWDED -> R.string.condition_crowded
    Condition.POOR_LIGHTING -> R.string.condition_lighting
    Condition.FOOTPATH_PROBLEM -> R.string.condition_footpath
    Condition.FELT_UNSAFE -> R.string.condition_unsafe
}

@StringRes
fun problemLabel(problem: Problem): Int = when (problem) {
    Problem.BUS_DID_NOT_COME -> R.string.problem_bus
    Problem.MISSED_CONNECTION -> R.string.problem_missed
    Problem.SERVICE_UNAVAILABLE -> R.string.problem_unavailable
    Problem.RUNNING_LATE -> R.string.problem_late
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}
