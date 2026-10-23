package com.raasta.app.planner

import kotlin.math.roundToInt

enum class Mode { WALK, BUS, METRO, AUTO }

/** Where a time or fare comes from. Shown next to every number in the app. */
enum class Basis { TIMETABLE, OFFICIAL_FARE, REPORTED, ESTIMATE, MAP_ROUTE }

enum class JourneyKind { BUS_METRO, AUTO_METRO, AUTO_DIRECT }

enum class Confidence { HIGH, MEDIUM, LOW }

/** Things the rider should know before choosing an option. */
sealed interface Caution {
    data class TimetableEnds(val route: String, val lastDeparture: String) : Caution
    data class BusFareUnknown(val route: String) : Caution
    data object MetroLastTrainUncertain : Caution
    data object AutoAvailabilityUnknown : Caution
    data object AutoNightSurcharge : Caution
    data class FailedRouteMayRepeat(val route: String) : Caution
}

data class Leg(
    val mode: Mode,
    val from: String,
    val to: String,
    val route: String? = null,
    /** Minutes after midnight on the travel day when this leg begins (may pass 24:00). */
    val startMin: Int,
    val waitMin: Int,
    val moveMin: Int,
    val distanceM: Int? = null,
    val fare: Int = 0,
    val timeBasis: Basis,
    val fareBasis: Basis? = null,
) {
    val endMin: Int get() = startMin + waitMin + moveMin
}

data class Journey(
    val kind: JourneyKind,
    val busRoute: String?,
    val legs: List<Leg>,
    val cautions: List<Caution>,
    /** 0 = every number is timetabled or official; 1 = mostly estimates. */
    val uncertainty: Double,
    /** True when this option keeps waiting for the bus route that just failed. */
    val retriesFailedRoute: Boolean = false,
) {
    val departMin: Int get() = legs.first().startMin
    val arriveMin: Int get() = legs.last().endMin
    val totalMin: Int get() = arriveMin - departMin
    val fare: Int get() = legs.sumOf { it.fare }
    val waitMin: Int get() = legs.sumOf { it.waitMin }
    val walkM: Int get() = legs.filter { it.mode == Mode.WALK }.sumOf { it.distanceM ?: 0 }
    val transfers: Int get() = (legs.count { it.mode != Mode.WALK } - 1).coerceAtLeast(0)
    val confidence: Confidence
        get() = when {
            uncertainty < 0.25 -> Confidence.HIGH
            uncertainty < 0.6 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
}

/** An option that cannot be used at the chosen time, with the reason. */
data class Unavailable(val kind: JourneyKind, val busRoute: String?, val metroOpensAt: String, val metroClosesBy: String)

/**
 * Builds the candidate journeys for one origin, destination and departure time.
 * Times come from the PMPML timetable where it has trips; everything else is labelled.
 */
class JourneyPlanner(private val corridor: Corridor) {

    /** Journeys for a trip planned before leaving. */
    fun journeys(originId: String, destinationId: String, departMin: Int): Pair<List<Journey>, List<Unavailable>> {
        val origin = corridor.origin(originId)
        val direct = corridor.autoDirect(origin.id, destinationId)
        return build(
            fromName = origin.name,
            accessOptions = origin.accessToMetro,
            autoDirectKm = direct.km,
            autoDirectFreeFlowMin = direct.freeFlowMin,
            destination = corridor.destination(destinationId),
            startMin = departMin,
            failedRoute = null,
        )
    }

    /**
     * Journeys for a rider stranded at [pointId] because [failedRoute] did not come.
     * Includes waiting for that route's next bus, other routes (boarded at the nearest stop
     * that serves them), auto to the metro and auto all the way.
     */
    fun recoveryJourneys(
        pointId: String,
        destinationId: String,
        nowMin: Int,
        failedRoute: String,
    ): Pair<List<Journey>, List<Unavailable>> {
        val point = corridor.recoveryPoint(pointId)
        val buses = corridor.busAccesses()
            .mapNotNull { bus ->
                point.walkTo(bus.boardStop.name)?.let { walk ->
                    bus.copy(walkToStopM = walk.m, walkToStopMin = walk.min)
                }
            }
            // The failed route is only worth waiting for where the rider already is.
            .filter { it.route != failedRoute || it.walkToStopM == 0 }
            .groupBy { it.route }
            .map { (_, options) -> options.minBy { it.walkToStopM } }
        val direct = point.autoToDestinations.first { it.destination == destinationId }
        return build(
            fromName = point.name,
            accessOptions = buses + point.autoToTransfer,
            autoDirectKm = direct.km,
            autoDirectFreeFlowMin = direct.freeFlowMin,
            destination = corridor.destination(destinationId),
            startMin = nowMin,
            failedRoute = failedRoute,
        )
    }

    private fun build(
        fromName: String,
        accessOptions: List<AccessOption>,
        autoDirectKm: Double,
        autoDirectFreeFlowMin: Double,
        destination: Destination,
        startMin: Int,
        failedRoute: String?,
    ): Pair<List<Journey>, List<Unavailable>> {
        val journeys = mutableListOf<Journey>()
        val unavailable = mutableListOf<Unavailable>()

        for (access in accessOptions) {
            val chain = Chain(startMin)
            val retry = access is BusAccess && access.route == failedRoute
            val kind = when (access) {
                is BusAccess -> {
                    addBusToTransfer(chain, fromName, access, retry)
                    JourneyKind.BUS_METRO
                }
                is AutoAccess -> {
                    addAutoToTransfer(chain, fromName, access)
                    JourneyKind.AUTO_METRO
                }
            }
            val busRoute = (access as? BusAccess)?.route
            if (addMetroAndWalk(chain, destination)) {
                journeys += chain.toJourney(kind, busRoute, retry)
            } else {
                val service = corridor.metro.service
                unavailable += Unavailable(kind, busRoute, service.firstTrain, service.lastTrainLatest)
            }
        }
        val direct = Chain(startMin)
        addAuto(direct, fromName, destination.name, autoDirectKm, autoDirectFreeFlowMin)
        journeys += direct.toJourney(JourneyKind.AUTO_DIRECT, busRoute = null)
        return journeys to unavailable
    }

    private fun addBusToTransfer(chain: Chain, fromName: String, bus: BusAccess, retryingFailedRoute: Boolean) {
        if (bus.walkToStopM > 0) {
            chain.add(
                Mode.WALK, fromName, bus.boardStop.name, wait = 0.0, move = bus.walkToStopMin,
                distanceM = bus.walkToStopM, timeBasis = Basis.MAP_ROUTE,
            )
        }
        val departures = bus.departures.map(::parseClock)
        val timeOfDay = Math.floorMod(chain.t, MINUTES_PER_DAY)
        // A bus that just failed to come does not count; look for the one after it.
        val next = departures.firstOrNull { if (retryingFailedRoute) it > timeOfDay else it >= timeOfDay }
        val headway = bus.medianHeadwayMin ?: DEFAULT_BUS_HEADWAY_MIN
        val wait: Double
        val waitBasis: Basis
        if (next != null) {
            wait = (next - timeOfDay).toDouble()
            waitBasis = Basis.TIMETABLE
        } else {
            // The feed lists no later trip. Assume the daytime frequency continues and say so.
            // After a missed bus, expect a full gap rather than half of one.
            wait = if (retryingFailedRoute) headway else headway / 2
            waitBasis = Basis.ESTIMATE
            chain.caution(Caution.TimetableEnds(bus.route, bus.departures.last()), uncertainty = 0.4)
        }
        if (retryingFailedRoute) chain.caution(Caution.FailedRouteMayRepeat(bus.route), uncertainty = 0.3)
        val fare = FareRules.busFare(corridor.fares.pmpmlFareSlabs, bus.rideKm)
        if (fare == null) chain.caution(Caution.BusFareUnknown(bus.route), uncertainty = 0.1)
        chain.add(
            Mode.BUS, bus.boardStop.name, bus.alightStop.name, route = bus.route,
            wait = wait, move = bus.rideMin, distanceM = (bus.rideKm * 1000).roundToInt(),
            fare = fare ?: 0, timeBasis = waitBasis, fareBasis = Basis.REPORTED,
        )
        chain.add(
            Mode.WALK, bus.alightStop.name, transferPlatformName(), wait = 0.0,
            move = bus.walkToStationMin + corridor.assumptions.stationEntryMin,
            distanceM = bus.walkToStationM, timeBasis = Basis.ESTIMATE,
        )
    }

    private fun addAutoToTransfer(chain: Chain, fromName: String, auto: AutoAccess) {
        addAuto(chain, fromName, "${corridor.metro.transferStation} Metro", auto.km, auto.freeFlowMin)
        chain.add(
            Mode.WALK, "${corridor.metro.transferStation} Metro", transferPlatformName(), wait = 0.0,
            move = corridor.assumptions.stationEntryMin, timeBasis = Basis.ESTIMATE,
        )
    }

    /** Adds the metro ride and final walk. Returns false when the metro is not running. */
    private fun addMetroAndWalk(chain: Chain, destination: Destination): Boolean {
        val service = corridor.metro.service
        val timeOfDay = Math.floorMod(chain.t, MINUTES_PER_DAY)
        if (!isWithin(timeOfDay, parseClock(service.firstTrain), parseClock(service.lastTrainLatest))) return false
        if (timeOfDay >= parseClock(service.lastTrainEarliest)) {
            chain.caution(Caution.MetroLastTrainUncertain, uncertainty = 0.5)
        }
        val peak = service.peakWindows.any { (start, end) -> isWithin(timeOfDay, parseClock(start), parseClock(end)) }
        val headway = if (peak) service.headwayPeakMin else service.headwayOffPeakMin
        val station = corridor.station(destination.metroStation)
        val transfer = corridor.station(corridor.metro.transferStation)
        val rideKm = station.kmFromRamwadi - transfer.kmFromRamwadi
        chain.uncertainty += 0.1
        chain.add(
            Mode.METRO, transfer.name, station.name, route = corridor.metro.line,
            wait = headway / 2, move = rideKm / corridor.assumptions.metroAvgSpeedKmh * 60,
            distanceM = (rideKm * 1000).roundToInt(), fare = station.fareFromRamwadi,
            timeBasis = Basis.ESTIMATE, fareBasis = Basis.OFFICIAL_FARE,
        )
        chain.add(
            Mode.WALK, station.name, destination.name, wait = 0.0, move = destination.walkFromStationMin,
            distanceM = destination.walkFromStationM, timeBasis = Basis.MAP_ROUTE,
        )
        return true
    }

    private fun addAuto(chain: Chain, from: String, to: String, km: Double, freeFlowMin: Double) {
        val assumptions = corridor.assumptions
        val rule = corridor.fares.autoFare
        val pickupMin = chain.t + assumptions.autoPickupWaitMin.roundToInt()
        // Both pickup wait and travel time are assumptions, so an auto is never "high confidence".
        chain.caution(Caution.AutoAvailabilityUnknown, uncertainty = 0.3)
        if (FareRules.isAutoNight(rule, pickupMin)) chain.caution(Caution.AutoNightSurcharge, uncertainty = 0.0)
        chain.add(
            Mode.AUTO, from, to, wait = assumptions.autoPickupWaitMin,
            // Free-flow routing ignores traffic, so use the slower of it and the assumed city speed.
            move = maxOf(freeFlowMin, km / assumptions.autoAvgSpeedKmh * 60),
            distanceM = (km * 1000).roundToInt(), fare = FareRules.autoFare(rule, km, pickupMin),
            timeBasis = Basis.ESTIMATE, fareBasis = Basis.REPORTED,
        )
    }

    private fun transferPlatformName() = "${corridor.metro.transferStation} Metro platform"

    private class Chain(var t: Int) {
        val legs = mutableListOf<Leg>()
        val cautions = mutableListOf<Caution>()
        var uncertainty = 0.0

        fun add(
            mode: Mode, from: String, to: String, route: String? = null,
            wait: Double, move: Double, distanceM: Int? = null, fare: Int = 0,
            timeBasis: Basis, fareBasis: Basis? = null,
        ) {
            val leg = Leg(
                mode, from, to, route, startMin = t, waitMin = wait.roundToInt(), moveMin = move.roundToInt(),
                distanceM = distanceM, fare = fare, timeBasis = timeBasis, fareBasis = fareBasis,
            )
            legs += leg
            t = leg.endMin
        }

        fun caution(caution: Caution, uncertainty: Double) {
            cautions += caution
            this.uncertainty += uncertainty
        }

        fun toJourney(kind: JourneyKind, busRoute: String?, retriesFailedRoute: Boolean = false) =
            Journey(kind, busRoute, legs.toList(), cautions.toList(), uncertainty.coerceAtMost(1.0), retriesFailedRoute)
    }

    private companion object {
        const val DEFAULT_BUS_HEADWAY_MIN = 30.0
    }
}
