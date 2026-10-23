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
import com.raasta.app.map.pointAlong
import com.raasta.app.planner.Corridor
import com.raasta.app.planner.Journey
import com.raasta.app.planner.JourneyPlanner
import com.raasta.app.planner.Leg
import com.raasta.app.planner.Mode
import com.raasta.app.planner.PlanResult
import com.raasta.app.planner.Priority
import com.raasta.app.planner.plan
import com.raasta.app.planner.recover
import com.raasta.app.safety.CrimeLayer
import com.raasta.app.safety.JourneyRoutes
import com.raasta.app.safety.JourneySafety
import com.raasta.app.safety.RouteVariant
import com.raasta.app.safety.SafeRouter
import com.raasta.app.safety.SafetyModel
import com.raasta.app.safety.StreetGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** "My bus did not come" at [pointId]: plan from that stop instead of from an origin. */
data class RecoveryRequest(val pointId: String, val failedRoute: String)

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

/** App-wide state for the Uber-style flow: pick places, compare routes, ride, arrive. */
class TripViewModel(app: Application) : AndroidViewModel(app) {
    val corridor: Corridor = Corridor.load(app)
    val places = PlaceIndex(corridor)
    private val planner = JourneyPlanner(corridor)
    private val shapes = RouteShapes(places)

    /** The phone clock, refreshed so "Leave now" stays current. */
    var nowMinute by mutableIntStateOf(currentMinuteOfDay())
        private set

    var originId by mutableStateOf(corridor.origins.first().id)
    var destinationId by mutableStateOf<String?>(null)
    /** Minute of day to leave at, or null for now. */
    var departAt by mutableStateOf<Int?>(null)
    var priority by mutableStateOf(Priority.BALANCED)
    var recovery by mutableStateOf<RecoveryRequest?>(null)
    var selectedIndex by mutableIntStateOf(0)

    /** Road shapes for legs, keyed by leg; filled in as they arrive from the routing server. */
    val legShapes = mutableStateMapOf<Leg, List<LatLon>>()

    var safetyStatus by mutableStateOf<SafetyStatus>(SafetyStatus.Loading)
        private set
    /** Shortest or safety streets for the walking and auto legs of the chosen option. */
    var routeVariant by mutableStateOf(RouteVariant.SAFEST)
    /** Shortest-vs-safety comparison per journey; a null value means the journey has no street legs to compare. */
    val comparisons = mutableStateMapOf<Journey, JourneyRoutes?>()
    private val comparing = mutableSetOf<Journey>()
    var showCrowdHeat by mutableStateOf(false)
    var showCrimeHeat by mutableStateOf(false)

    val startMinute: Int get() = departAt ?: nowMinute

    val plan: PlanResult? by derivedStateOf {
        val destination = destinationId ?: return@derivedStateOf null
        val rescue = recovery
        if (rescue != null) {
            planner.recover(rescue.pointId, destination, startMinute, rescue.failedRoute, priority)
        } else {
            planner.plan(originId, destination, startMinute, priority)
        }
    }

    val fromName: String
        get() = recovery?.let { corridor.recoveryPoint(it.pointId).name } ?: corridor.origin(originId).name

    val fromPoint: LatLon
        get() = recovery?.let { corridor.recoveryPoint(it.pointId) }?.let { LatLon(it.lat, it.lon) }
            ?: corridor.origin(originId).let { LatLon(it.lat, it.lon) }

    // Active trip, simulated on an accelerated clock for the demo.
    var trip by mutableStateOf<Journey?>(null)
        private set
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

    private fun loadSafety(app: Application): SafetyStatus = runCatching {
        val graph = app.assets.open("street_graph.bin").use(StreetGraph::read)
        // Routing still works on crowd alone if the crime file is missing or broken.
        val crime = runCatching {
            CrimeLayer.parse(app.assets.open("crime_zones.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val model = SafetyModel(graph, crime)
        SafetyStatus.Ready(JourneySafety(SafeRouter(model), places, corridor.fares.autoFare), model, crime)
    }.getOrElse { SafetyStatus.Unavailable(it.message ?: it.javaClass.simpleName) }

    fun chooseDestination(id: String) {
        destinationId = id
        selectedIndex = 0
    }

    fun startPlanning() {
        recovery = null
        selectedIndex = 0
    }

    fun startRecovery(pointId: String, failedRoute: String) {
        recovery = RecoveryRequest(pointId, failedRoute)
        departAt = null
        selectedIndex = 0
    }

    fun startTrip(journey: Journey) {
        trip = journey
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

    /** Replaces the rest of the trip with a recovery option that starts now. */
    fun reroute(journey: Journey) {
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
        arrived = false
        awaitingBoarding = null
    }

    /** Options for a rider whose bus did not come, from the stop they are at, starting now. */
    fun rescuePlan(): Pair<RecoveryRequest, PlanResult>? {
        val journey = trip ?: return null
        val destination = destinationId ?: return null
        val leg = journey.legs.getOrNull(progress(journey, tripMinute).legIndex) ?: return null
        if (leg.mode != Mode.BUS) return null
        val point = corridor.recoveryPoints.firstOrNull { it.name == leg.from } ?: return null
        val request = RecoveryRequest(point.id, leg.route.orEmpty())
        return request to planner.recover(point.id, destination, tripMinute.toInt(), request.failedRoute, priority)
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
    }
}

fun currentMinuteOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}
