package com.raasta.app.ui

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raasta.app.map.LatLon
import com.raasta.app.map.PlaceIndex
import com.raasta.app.map.RouteShapes
import com.raasta.app.map.distanceM
import com.raasta.app.map.pointAlong
import com.raasta.app.planner.Caution
import com.raasta.app.planner.Compare
import com.raasta.app.planner.Condition
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.DataCheck
import com.raasta.app.planner.DataChecks
import com.raasta.app.planner.Evidence
import com.raasta.app.planner.Journey
import com.raasta.app.planner.JourneyPlanner
import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import com.raasta.app.planner.PlanResult
import com.raasta.app.planner.RankedJourney
import com.raasta.app.planner.StreetSafety
import com.raasta.app.planner.Tradeoffs
import com.raasta.app.planner.TravelPreferences
import com.raasta.app.planner.VersusOriginal
import com.raasta.app.planner.plan
import com.raasta.app.planner.recover
import com.raasta.app.planner.recoverAtTransfer
import com.raasta.app.planner.versusOriginal as compareWithOriginal
import com.raasta.app.safety.CrimeLayer
import com.raasta.app.safety.JourneyRoutes
import com.raasta.app.safety.JourneySafety
import com.raasta.app.safety.RouteVariant
import com.raasta.app.safety.SafeRouter
import com.raasta.app.safety.SafetyModel
import com.raasta.app.safety.StreetGraph
import com.raasta.app.store.FeedbackLog
import com.raasta.app.store.JourneyFeedback
import com.raasta.app.store.PreferencesStore
import com.raasta.app.store.ReportStatus
import com.raasta.app.store.ReportStore
import com.raasta.app.store.RouteReport
import com.raasta.app.store.TripRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Where the rider is within a trip at a given minute. */
data class TripProgress(
    val legIndex: Int,
    /** True while waiting for the leg's vehicle (bus, metro or auto pickup). */
    val waiting: Boolean,
    val minutesToNextEvent: Int,
    val position: LatLon?,
)

/** Street safety data, loaded from assets in the background. */
sealed interface SafetyStatus {
    data object Loading : SafetyStatus
    data class Ready(val safety: JourneySafety, val model: SafetyModel, val crime: CrimeLayer?) : SafetyStatus
    data class Unavailable(val reason: String) : SafetyStatus
}

/** What the rider says went wrong during a journey. */
enum class Problem { BUS_DID_NOT_COME, MISSED_CONNECTION, SERVICE_UNAVAILABLE, RUNNING_LATE }

/** New options after a disruption, starting from where the rider is now. */
data class Recovery(
    val problem: Problem,
    val fromName: String,
    /** The bus route that failed, when that is the problem. */
    val failedRoute: String?,
    val result: PlanResult,
)

/** Why no new options can be offered from where the rider is. */
enum class NoRecovery { ON_BOARD, LAST_LEG, NOT_IN_DATA }

sealed interface RecoveryResult {
    data class Options(val recovery: Recovery) : RecoveryResult
    data class None(val reason: NoRecovery) : RecoveryResult
}

/** How the journey looks right now. */
enum class TripHealth { ON_TRACK, CHECK_NEEDED, AT_RISK }

enum class StatusReason { BUS_DUE, LAST_METRO, REPORTED_PROBLEM }

data class TripStatus(val health: TripHealth, val reason: StatusReason? = null)

/**
 * App-wide state for a Raasta journey: input, preferences, route analysis and comparison,
 * the journey itself with monitoring and recovery, then feedback.
 */
class TripViewModel(app: Application) : AndroidViewModel(app) {
    val corridor: Corridor = Corridor.load(app)
    val places = PlaceIndex(corridor)
    private val planner = JourneyPlanner(corridor)
    private val shapes = RouteShapes(places)
    private val preferencesStore = PreferencesStore(app)
    private val reportStore = ReportStore(app)

    /** The phone clock, refreshed so "Leave now" stays current. */
    var nowMinute by mutableIntStateOf(currentMinuteOfDay())
        private set

    // Journey input.
    var originId by mutableStateOf(corridor.origins.first().id)
    var destinationId by mutableStateOf<String?>(null)
    /** Minute of day to leave at, or null for now. */
    var departAt by mutableStateOf<Int?>(null)

    // Personalisation. Kept unchanged through recovery.
    var preferences by mutableStateOf(TravelPreferences())
    /** True once preferences were loaded from a previous journey. */
    var hasSavedPreferences by mutableStateOf(false)
        private set

    // Feedback and the validated evidence it produces.
    var feedbackLog by mutableStateOf(FeedbackLog())
        private set
    val evidence: List<Evidence> by derivedStateOf { feedbackLog.verified.map { it.toEvidence() } }

    /** The departure minute routes were planned for, fixed when the rider asks for routes. */
    var searchMinute by mutableIntStateOf(nowMinute)
        private set
    var selectedIndex by mutableIntStateOf(0)

    /** Road shapes for legs, keyed by leg; filled in as they arrive from the routing server. */
    val legShapes = mutableStateMapOf<Leg, List<LatLon>>()

    var safetyStatus by mutableStateOf<SafetyStatus>(SafetyStatus.Loading)
        private set
    /** Shortest or safety streets for the walking and auto legs of the chosen option. */
    var routeVariant by mutableStateOf(RouteVariant.SHORTEST)
    /** Shortest-vs-safety comparison per journey; a null value means the journey has no street legs to compare. */
    val comparisons = mutableStateMapOf<Journey, JourneyRoutes?>()
    private val comparing = mutableSetOf<Journey>()
    var showCrowdHeat by mutableStateOf(false)
    var showCrimeHeat by mutableStateOf(false)

    val startMinute: Int get() = departAt ?: nowMinute

    private val basePlan: PlanResult? by derivedStateOf {
        val destination = destinationId ?: return@derivedStateOf null
        planner.plan(originId, destination, searchMinute, preferences, evidence)
    }

    /** Ranked routes; with safety-aware routing, safer streets move a route up once they are known. */
    val plan: PlanResult? by derivedStateOf { basePlan?.let(::withSafety) }

    /** Routes that can actually be taken: waiting hours for the first bus is not a real choice. */
    val options: List<RankedJourney> by derivedStateOf {
        plan?.ranked.orEmpty().filter { it.journey.waitMin <= MAX_WAIT_MIN }
    }

    /** Routes not running at the planned time. */
    val notRunningCount: Int by derivedStateOf {
        val result = plan ?: return@derivedStateOf 0
        result.ranked.size - options.size + result.unavailable.size
    }

    val streetSafety: StreetSafety
        get() = when (val status = safetyStatus) {
            SafetyStatus.Loading -> StreetSafety.LOADING
            is SafetyStatus.Unavailable -> StreetSafety.NOT_AVAILABLE
            is SafetyStatus.Ready -> if (status.crime == null || status.crime.isDemo) StreetSafety.PARTIAL else StreetSafety.READY
        }

    /** True when everything the ranking depends on has been worked out. */
    val analysisDone: Boolean by derivedStateOf {
        if (!preferences.safetyAware) return@derivedStateOf true
        when (safetyStatus) {
            SafetyStatus.Loading -> false
            is SafetyStatus.Unavailable -> true
            is SafetyStatus.Ready -> options.all { it.journey in comparisons }
        }
    }

    /** What the ranking rests on, with unknown and outdated data called out. */
    val dataChecks: List<DataCheck> by derivedStateOf {
        val result = plan ?: return@derivedStateOf emptyList()
        DataChecks.build(
            corridor, result, preferences, today(),
            verifiedReports = feedbackLog.verified.size,
            pendingReports = feedbackLog.pending.size,
            streetSafety = streetSafety,
        )
    }

    val fromName: String get() = corridor.origin(originId).name
    val fromPoint: LatLon get() = corridor.origin(originId).let { LatLon(it.lat, it.lon) }
    val destinationName: String get() = destinationId?.let { corridor.destination(it).name }.orEmpty()

    // Active journey, simulated on an accelerated clock for the demo.
    var trip by mutableStateOf<Journey?>(null)
        private set
    /** The journey the rider first chose; recovery options are compared with it. */
    var original by mutableStateOf<Journey?>(null)
        private set
    /** Fares paid on journeys left behind by a reroute. */
    private var paidBefore by mutableIntStateOf(0)
    var tripMinute by mutableDoubleStateOf(0.0)
        private set
    var tripPaused by mutableStateOf(false)
        private set
    /** Set when the rider must confirm they boarded the bus at this leg index. */
    var awaitingBoarding by mutableStateOf<Int?>(null)
        private set
    var arrived by mutableStateOf(false)
        private set
    /** Trip minutes that pass per real second. */
    var speed by mutableFloatStateOf(2f)
    var reroutes by mutableIntStateOf(0)
        private set
    /** When the rider set off, kept across reroutes for the trip summary. */
    var tripStartMinute by mutableIntStateOf(0)
        private set
    private val confirmedBoarding = mutableSetOf<Int>()
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            safetyStatus = withContext(Dispatchers.IO) { loadSafety(app) }
        }
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                nowMinute = currentMinuteOfDay()
            }
        }
        viewModelScope.launch {
            preferencesStore.saved.collect { saved ->
                if (saved != null && !hasSavedPreferences) {
                    preferences = saved
                    hasSavedPreferences = true
                }
            }
        }
        viewModelScope.launch { reportStore.log.collect { feedbackLog = it } }
    }

    fun shapeOf(leg: Leg): List<LatLon> = legShapes[leg] ?: places.fallbackPath(leg)

    fun loadShapes(journey: Journey) {
        val ready = safetyStatus as? SafetyStatus.Ready
        journey.legs.filter { it !in legShapes }.forEach { leg ->
            viewModelScope.launch {
                // Walks and auto rides use the same street graph as the safety comparison.
                val street = ready?.let { withContext(Dispatchers.Default) { it.safety.routesFor(leg) }?.shortest?.points }
                val shape = street ?: shapes.shape(leg)
                // A comparison may have filled this leg in meanwhile; it wins.
                if (leg !in legShapes) legShapes[leg] = shape
            }
        }
    }

    /** Works out the shortest and safety streets for [journey], once. */
    fun compareRoutes(journey: Journey) {
        val ready = safetyStatus as? SafetyStatus.Ready ?: return
        if (journey in comparisons || !comparing.add(journey)) return
        viewModelScope.launch {
            val routes = withContext(Dispatchers.Default) { ready.safety.compare(journey) }
            routes?.let { legShapes.putAll(it.shapes) }
            // Plans are rebuilt as the clock moves; keep only recent comparisons.
            if (comparisons.size > MAX_COMPARISONS) comparisons.clear()
            comparisons[journey] = routes
            comparing -= journey
        }
    }

    /** Gathers route shapes and, with safety-aware routing, street safety for every option. */
    fun analyse(journeys: List<Journey>) {
        journeys.forEach { journey ->
            loadShapes(journey)
            if (preferences.safetyAware) compareRoutes(journey)
        }
    }

    private fun loadSafety(app: Application): SafetyStatus = runCatching {
        val graph = app.assets.open("street_graph.bin").use(StreetGraph::read)
        // Routing still works on crowd alone if the crime file is missing or broken.
        val crime = runCatching {
            CrimeLayer.parse(app.assets.open("crime_zones.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val model = SafetyModel(graph, crime)
        SafetyStatus.Ready(JourneySafety(SafeRouter(model), places, corridor.fares.autoFare), model, crime)
    }.getOrElse { SafetyStatus.Unavailable(it.message ?: it.javaClass.simpleName) }

    private fun withSafety(result: PlanResult): PlanResult {
        if (!preferences.safetyAware) return result
        val ranked = result.ranked
            .map { option ->
                val score = comparisons[option.journey]?.safestExposure?.score ?: return@map option
                option.copy(score = option.score + SAFETY_WEIGHT * (100 - score) / 100.0)
            }
            .sortedBy { it.score }
        return result.copy(ranked = ranked)
    }

    /** Safety score (0..100) of [journey] on the chosen streets, when safety-aware routing is on and it is known. */
    fun safetyScore(journey: Journey): Int? {
        if (!preferences.safetyAware) return null
        return comparisons[journey]?.exposure(routeVariant)?.score
    }

    /** [journey] with the shortest or safety streets the rider picked. */
    fun chosen(journey: Journey): Journey = comparisons[journey]?.journey(routeVariant) ?: journey

    /** Advantages and disadvantages: the recommended route against the next best, others against the recommended. */
    fun tradeoffs(option: RankedJourney, among: List<RankedJourney> = options): Tradeoffs {
        val best = among.firstOrNull()
        val reference = if (option == best) among.getOrNull(1) else best
        return Compare.tradeoffs(
            option.journey,
            reference?.journey,
            evidence,
            safetyScore(option.journey),
            reference?.let { safetyScore(it.journey) },
        )
    }

    fun chooseDestination(id: String) {
        destinationId = id
        selectedIndex = 0
    }

    /** The rider has set their preferences and wants routes for the journey they entered. */
    fun findRoutes(chosen: TravelPreferences) {
        preferences = chosen
        hasSavedPreferences = true
        searchMinute = startMinute
        selectedIndex = 0
        routeVariant = if (chosen.safetyAware) RouteVariant.SAFEST else RouteVariant.SHORTEST
        viewModelScope.launch { preferencesStore.save(chosen) }
    }

    fun startTrip(journey: Journey) {
        trip = journey
        original = journey
        paidBefore = 0
        tripMinute = journey.departMin.toDouble()
        tripStartMinute = journey.departMin
        arrived = false
        tripPaused = false
        awaitingBoarding = null
        confirmedBoarding.clear()
        reroutes = 0
        loadShapes(journey)
        runTicker()
    }

    /** Fares the rider has paid so far: earlier journeys plus vehicles boarded on this one. */
    fun paidSoFar(): Int {
        val journey = trip ?: return paidBefore
        val progress = progress(journey, tripMinute)
        return paidBefore + journey.legs.withIndex().sumOf { (i, leg) ->
            if (i < progress.legIndex || (i == progress.legIndex && !progress.waiting)) leg.fare else 0
        }
    }

    /** How a recovery option compares with the journey the rider first chose. */
    fun versusOriginal(option: Journey): VersusOriginal? =
        original?.let { compareWithOriginal(it, paidSoFar(), option) }

    /** Replaces the rest of the journey with a recovery option that starts now. */
    fun reroute(journey: Journey) {
        paidBefore = paidSoFar()
        trip = journey
        tripMinute = journey.departMin.toDouble()
        awaitingBoarding = null
        confirmedBoarding.clear()
        tripPaused = false
        reroutes++
        loadShapes(journey)
    }

    fun confirmBoarded() {
        awaitingBoarding?.let { confirmedBoarding += it }
        awaitingBoarding = null
        tripPaused = false
    }

    fun togglePause() {
        if (awaitingBoarding == null) tripPaused = !tripPaused
    }

    /** Jumps to the start of the next leg (or the end of this wait). */
    fun skipAhead() {
        val journey = trip ?: return
        if (awaitingBoarding != null) return
        val progress = progress(journey, tripMinute)
        val leg = journey.legs[progress.legIndex]
        val boardAt = (leg.startMin + leg.waitMin).toDouble()
        tripMinute = if (progress.waiting) boardAt else leg.endMin.toDouble()
        step(0.0)
    }

    fun endTrip() {
        ticker?.cancel()
        trip = null
        original = null
        arrived = false
        awaitingBoarding = null
    }

    /** Monitoring: whether the journey still looks practical. */
    fun status(journey: Journey, progress: TripProgress): TripStatus {
        if (awaitingBoarding != null) return TripStatus(TripHealth.CHECK_NEEDED, StatusReason.BUS_DUE)
        val metro = journey.legs.indexOfFirst { it.mode == Mode.METRO }
        if (metro >= progress.legIndex && Caution.MetroLastTrainUncertain in journey.cautions) {
            return TripStatus(TripHealth.AT_RISK, StatusReason.LAST_METRO)
        }
        if (evidence.any { it.affects(journey) }) return TripStatus(TripHealth.AT_RISK, StatusReason.REPORTED_PROBLEM)
        return TripStatus(TripHealth.ON_TRACK)
    }

    /** New options after [problem], from where the rider is now, ranked by their original preferences. */
    fun recover(problem: Problem): RecoveryResult {
        val journey = trip ?: return RecoveryResult.None(NoRecovery.NOT_IN_DATA)
        val destination = destinationId ?: return RecoveryResult.None(NoRecovery.NOT_IN_DATA)
        val progress = progress(journey, tripMinute)
        val index = progress.legIndex
        val leg = journey.legs[index]
        val now = tripMinute.toInt()
        val metro = journey.legs.indexOfFirst { it.mode == Mode.METRO }
        val beforeMetro = metro < 0 || index < metro - 1

        val recovery = when {
            // On a moving bus or auto there is nothing to change until the next stop.
            (leg.mode == Mode.BUS || leg.mode == Mode.AUTO) && !progress.waiting ->
                return RecoveryResult.None(NoRecovery.ON_BOARD)
            beforeMetro -> {
                val point = corridor.recoveryPoints.firstOrNull { it.name == leg.from }
                    ?: progress.position?.let { here ->
                        corridor.recoveryPoints
                            .minBy { distanceM(here, LatLon(it.lat, it.lon)) }
                            .takeIf { distanceM(here, LatLon(it.lat, it.lon)) <= NEAR_STOP_M }
                    }
                    ?: return RecoveryResult.None(NoRecovery.NOT_IN_DATA)
                // The bus the rider was going to take, if it failed.
                val busLeg = journey.legs.drop(index).firstOrNull { it.mode == Mode.BUS }
                val failed = busLeg?.route?.takeIf { problem == Problem.BUS_DID_NOT_COME || problem == Problem.SERVICE_UNAVAILABLE }
                Recovery(problem, point.name, failed, planner.recover(point.id, destination, now, failed.orEmpty(), preferences, evidence))
            }
            index < metro || (index == metro && progress.waiting) -> {
                val unavailable = problem == Problem.SERVICE_UNAVAILABLE
                Recovery(
                    problem,
                    "${corridor.metro.transferStation} Metro",
                    null,
                    planner.recoverAtTransfer(destination, now, unavailable, preferences, evidence),
                )
            }
            else -> return RecoveryResult.None(NoRecovery.LAST_LEG)
        }
        analyse(recovery.result.ranked.map { it.journey })
        return RecoveryResult.Options(recovery)
    }

    /** Stop, station and place names along [journey], for reporting where a problem was. */
    fun journeyPlaces(journey: Journey): List<String> {
        val platform = "${corridor.metro.transferStation} Metro platform"
        // The platform is the same place as the station for a report.
        return journey.legs.flatMap { listOf(it.from, it.to) }.filter { it != platform }.distinct()
    }

    /** Stores journey feedback; each report waits for review before it can affect routes. */
    fun submitFeedback(rating: TripRating, reports: List<Triple<Condition, String, String?>>) {
        val label = "$fromName → $destinationName"
        val now = System.currentTimeMillis()
        val routeReports = reports.map { (condition, place, route) ->
            RouteReport(UUID.randomUUID().toString(), now, condition, place, route, label)
        }
        val feedback = JourneyFeedback(UUID.randomUUID().toString(), now, label, rating, routeReports.map { it.id })
        viewModelScope.launch { reportStore.addFeedback(feedback, routeReports) }
    }

    /** Demo reviewer decision on a report. */
    fun review(reportId: String, status: ReportStatus) {
        viewModelScope.launch { reportStore.setStatus(reportId, status) }
    }

    /** Back to journey input for another trip, keeping where the rider starts and their preferences. */
    fun planAnother() {
        endTrip()
        destinationId = null
        departAt = null
        selectedIndex = 0
    }

    fun progress(journey: Journey, minute: Double): TripProgress {
        val index = journey.legs.indexOfFirst { minute < it.endMin }.let { if (it < 0) journey.legs.lastIndex else it }
        val leg = journey.legs[index]
        val boardAt = leg.startMin + leg.waitMin
        // Still at the stop while the rider has not confirmed boarding.
        val waiting = minute < boardAt || awaitingBoarding == index
        val path = shapeOf(leg)
        val fraction = if (waiting || leg.moveMin == 0) 0.0 else (minute - boardAt) / leg.moveMin
        val position = if (minute >= journey.arriveMin) path.lastOrNull() else pointAlong(path, fraction.coerceIn(0.0, 1.0))
        val next = if (waiting) boardAt - minute else leg.endMin - minute
        return TripProgress(index, waiting, kotlin.math.ceil(next).toInt().coerceAtLeast(0), position)
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (trip != null && !arrived) {
                delay(TICK_MS)
                if (!tripPaused) step(speed * TICK_MS / 1000.0)
            }
        }
    }

    private fun step(minutes: Double) {
        val journey = trip ?: return
        var next = tripMinute + minutes
        // Stop at each bus boarding so the rider can say whether the bus actually came.
        journey.legs.forEachIndexed { i, leg ->
            val boardAt = (leg.startMin + leg.waitMin).toDouble()
            if (leg.mode == Mode.BUS && i !in confirmedBoarding && tripMinute <= boardAt && next >= boardAt) {
                next = boardAt
                awaitingBoarding = i
                tripPaused = true
                tripMinute = next
                return
            }
        }
        tripMinute = next.coerceAtMost(journey.arriveMin.toDouble())
        if (tripMinute >= journey.arriveMin) arrived = true
    }

    private companion object {
        const val TICK_MS = 100L
        const val MAX_COMPARISONS = 60
        const val MAX_WAIT_MIN = 90
        /** Score added for a route whose streets score 0/100 for safety; 100/100 adds nothing. */
        const val SAFETY_WEIGHT = 1.5
        /** How close the rider must be to a known bus stop to plan from it. */
        const val NEAR_STOP_M = 800.0
    }
}

fun currentMinuteOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
