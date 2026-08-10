package com.flip6.sensenav.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Walking guidance derived from a route's own geometry.
 *
 * The scoring API returns each route as an encoded polyline and nothing else -
 * there are no street names and no manoeuvre list - so the turns here are read
 * off the shape of the path rather than handed down by a directions provider.
 * That is why an instruction says "Turn left" and never "Turn left onto Collins
 * St": the street name is not something this app has, and guessing one would be
 * worse than leaving it out.
 */

private const val EarthRadiusMeters = 6_371_000.0

/** Metres per degree of latitude - close enough for the short spans projected here. */
private const val MetersPerDegree = 111_320.0

/** Great-circle distance in metres. */
fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EarthRadiusMeters * atan2(sqrt(a), sqrt(1 - a))
}

/** Initial bearing to [other], in degrees clockwise from north. */
fun GeoPoint.bearingTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Turn from one bearing to another, negative for left and positive for right. */
private fun signedTurn(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0) - 180.0

enum class Maneuver {
    Depart,
    Straight,
    SlightLeft,
    Left,
    SharpLeft,
    SlightRight,
    Right,
    SharpRight,
    Arrive;

    /** Short label for the manoeuvre badge - this app spells its icons out. */
    val badge: String
        get() = when (this) {
            Depart -> "Start"
            Straight -> "Ahead"
            SlightLeft, Left, SharpLeft -> "Left"
            SlightRight, Right, SharpRight -> "Right"
            Arrive -> "End"
        }
}

private fun maneuverFor(turn: Double): Maneuver {
    val magnitude = abs(turn)
    val left = turn < 0
    return when {
        magnitude < 20 -> Maneuver.Straight
        magnitude < 45 -> if (left) Maneuver.SlightLeft else Maneuver.SlightRight
        magnitude < 120 -> if (left) Maneuver.Left else Maneuver.Right
        else -> if (left) Maneuver.SharpLeft else Maneuver.SharpRight
    }
}

private fun instructionFor(maneuver: Maneuver): String = when (maneuver) {
    Maneuver.Depart -> "Head off"
    Maneuver.Straight -> "Continue straight"
    Maneuver.SlightLeft -> "Bear left"
    Maneuver.Left -> "Turn left"
    Maneuver.SharpLeft -> "Sharp left"
    Maneuver.SlightRight -> "Bear right"
    Maneuver.Right -> "Turn right"
    Maneuver.SharpRight -> "Sharp right"
    Maneuver.Arrive -> "Arrive"
}

/** Eight-point compass name, so the first instruction can say which way to set off. */
fun compassName(bearing: Double): String {
    val points = listOf(
        "north", "north-east", "east", "south-east",
        "south", "south-west", "west", "north-west"
    )
    val index = (((bearing % 360.0) + 360.0) % 360.0 / 45.0).roundToInt() % 8
    return points[index]
}

/**
 * One manoeuvre on the path.
 *
 * [distanceFromStartMeters] is where along the route it happens, [legMeters] how
 * far is walked on the leg that ends at it.
 */
data class NavStep(
    val maneuver: Maneuver,
    val instruction: String,
    val distanceFromStartMeters: Double,
    val legMeters: Double
)

/** How far along the route a position is, and how far off it. */
data class RouteProgress(
    val traveledMeters: Double,
    val remainingMeters: Double,
    val offRouteMeters: Double
) {
    val hasArrived: Boolean get() = remainingMeters <= ArrivalRadiusMeters
    val isOffRoute: Boolean get() = offRouteMeters > OffRouteMeters

    companion object {
        const val ArrivalRadiusMeters = 30.0
        const val OffRouteMeters = 60.0
    }
}

/** A route's path with its manoeuvres and per-vertex distances worked out once. */
data class RouteGuidance(
    val path: List<GeoPoint>,
    val steps: List<NavStep>,
    val cumulativeMeters: List<Double>,
    val totalMeters: Double
) {

    /**
     * Where [position] sits relative to the route, by projecting it onto the
     * nearest segment. Off-route distance is reported rather than acted on: the
     * screen tells the user, it does not silently re-plan.
     */
    fun progressAt(position: GeoPoint): RouteProgress? {
        if (path.size < 2) return null

        var bestOffRoute = Double.MAX_VALUE
        var bestTraveled = 0.0

        for (i in 0 until path.lastIndex) {
            val (fraction, offRoute) = projectOnSegment(position, path[i], path[i + 1])
            if (offRoute < bestOffRoute) {
                bestOffRoute = offRoute
                val segment = cumulativeMeters[i + 1] - cumulativeMeters[i]
                bestTraveled = cumulativeMeters[i] + fraction * segment
            }
        }

        return RouteProgress(
            traveledMeters = bestTraveled,
            remainingMeters = (totalMeters - bestTraveled).coerceAtLeast(0.0),
            offRouteMeters = bestOffRoute
        )
    }

    /** Everything still ahead: the first is what to do next, the rest the list. */
    fun stepsAfter(traveledMeters: Double): List<NavStep> =
        steps.filter { it.distanceFromStartMeters > traveledMeters + StepReachedMeters }

    private companion object {
        /** A manoeuvre this close behind is one the user has already made. */
        const val StepReachedMeters = 5.0
    }
}

/**
 * Projects [position] onto the segment a-b on a local flat plane.
 *
 * Returns how far along the segment the closest point is (0 to 1) and how far
 * [position] is from it in metres. Over a segment of a few tens of metres the
 * flat-earth error is far below the accuracy of a phone's own fix.
 */
private fun projectOnSegment(
    position: GeoPoint,
    a: GeoPoint,
    b: GeoPoint
): Pair<Double, Double> {
    val lonScale = cos(Math.toRadians(a.latitude)) * MetersPerDegree
    val bx = (b.longitude - a.longitude) * lonScale
    val by = (b.latitude - a.latitude) * MetersPerDegree
    val px = (position.longitude - a.longitude) * lonScale
    val py = (position.latitude - a.latitude) * MetersPerDegree

    val lengthSquared = bx * bx + by * by
    val fraction = if (lengthSquared <= 0.0) 0.0 else ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
    val dx = px - fraction * bx
    val dy = py - fraction * by
    return fraction to sqrt(dx * dx + dy * dy)
}

/**
 * Reads the manoeuvres off [path].
 *
 * A decoded polyline has a vertex every few metres, so a single street corner
 * shows up as a run of small heading changes rather than one clean turn. Each
 * vertex is therefore compared over a window either side of it, and the runs
 * that survive are collapsed to their sharpest point - otherwise one corner
 * would be announced three times.
 */
fun buildRouteGuidance(path: List<GeoPoint>, destinationName: String): RouteGuidance {
    if (path.size < 2) return RouteGuidance(path, emptyList(), listOf(0.0), 0.0)

    val cumulative = ArrayList<Double>(path.size)
    cumulative.add(0.0)
    for (i in 1..path.lastIndex) {
        cumulative.add(cumulative[i - 1] + path[i - 1].distanceTo(path[i]))
    }
    val total = cumulative.last()

    fun indexBehind(from: Int): Int {
        var i = from
        while (i > 0 && cumulative[from] - cumulative[i] < TurnWindowMeters) i--
        return i
    }

    fun indexAhead(from: Int): Int {
        var i = from
        while (i < path.lastIndex && cumulative[i] - cumulative[from] < TurnWindowMeters) i++
        return i
    }

    val candidates = mutableListOf<Pair<Int, Double>>()
    for (i in 1 until path.lastIndex) {
        val behind = indexBehind(i)
        val ahead = indexAhead(i)
        if (behind == i || ahead == i) continue
        // Turns in the first and last few metres are the user stepping onto and
        // off the footpath, not manoeuvres worth announcing.
        if (cumulative[i] < TurnWindowMeters || total - cumulative[i] < TurnWindowMeters) continue

        val turn = signedTurn(path[behind].bearingTo(path[i]), path[i].bearingTo(path[ahead]))
        if (abs(turn) >= TurnThresholdDegrees) candidates += i to turn
    }

    // Collapse each run of candidates to its sharpest vertex.
    val corners = mutableListOf<Pair<Int, Double>>()
    var run = mutableListOf<Pair<Int, Double>>()
    for (candidate in candidates) {
        val continuesRun = run.isNotEmpty() &&
            cumulative[candidate.first] - cumulative[run.last().first] <= CornerSpacingMeters
        if (continuesRun) {
            run += candidate
        } else {
            run.maxByOrNull { abs(it.second) }?.let { corners += it }
            run = mutableListOf(candidate)
        }
    }
    run.maxByOrNull { abs(it.second) }?.let { corners += it }

    val steps = mutableListOf<NavStep>()
    val firstAhead = indexAhead(0)
    steps += NavStep(
        maneuver = Maneuver.Depart,
        instruction = "Head ${compassName(path[0].bearingTo(path[firstAhead]))}",
        distanceFromStartMeters = 0.0,
        legMeters = 0.0
    )

    var previousDistance = 0.0
    for ((index, turn) in corners) {
        val distance = cumulative[index]
        val maneuver = maneuverFor(turn)
        steps += NavStep(
            maneuver = maneuver,
            instruction = instructionFor(maneuver),
            distanceFromStartMeters = distance,
            legMeters = distance - previousDistance
        )
        previousDistance = distance
    }

    steps += NavStep(
        maneuver = Maneuver.Arrive,
        instruction = "Arrive at $destinationName",
        distanceFromStartMeters = total,
        legMeters = total - previousDistance
    )

    return RouteGuidance(path, steps, cumulative, total)
}

/** How far either side of a vertex the heading is measured before and after it. */
private const val TurnWindowMeters = 20.0

/** Below this the path is just curving, not turning. */
private const val TurnThresholdDegrees = 35.0

/** Candidates closer together than this are the same corner. */
private const val CornerSpacingMeters = 30.0

/** "180 m", or "1.2 km" once metres stop being a useful unit. */
fun formatMeters(meters: Double): String = when {
    meters < 20 -> "${meters.roundToInt()} m"
    meters < 950 -> "${(meters / 10).roundToInt() * 10} m"
    else -> "%.1f km".format(meters / 1000)
}

fun formatMinutes(minutes: Int): String = when {
    minutes < 1 -> "under a minute"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/**
 * Minutes out of a duration string like "26 mins" or "1 hour 5 mins".
 *
 * Null when the text cannot be read, so callers fall back rather than showing a
 * confidently wrong arrival time.
 */
fun parseDurationMinutes(text: String): Int? {
    if (text.isBlank()) return null
    val hours = Regex("""(\d+)\s*(hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minutes = Regex("""(\d+)\s*(minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return (hours * 60 + minutes).takeIf { it > 0 }
}
