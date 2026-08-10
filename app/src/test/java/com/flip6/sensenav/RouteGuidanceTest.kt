package com.flip6.sensenav

import com.flip6.sensenav.model.GeoPoint
import com.flip6.sensenav.model.Maneuver
import com.flip6.sensenav.model.buildRouteGuidance
import com.flip6.sensenav.model.distanceTo
import com.flip6.sensenav.model.parseDurationMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/**
 * The manoeuvres shown while navigating are read off the route's own geometry,
 * so these check that a corner is found where there is one and not found where
 * the path is merely curving.
 */
class RouteGuidanceTest {

    private val start = GeoPoint(-37.8136, 144.9631)

    private fun northOf(point: GeoPoint, meters: Double) =
        GeoPoint(point.latitude + meters / 111_320.0, point.longitude)

    private fun eastOf(point: GeoPoint, meters: Double) = GeoPoint(
        point.latitude,
        point.longitude + meters / (111_320.0 * cos(Math.toRadians(point.latitude)))
    )

    /** An L: 200 m north, then 200 m east, sampled like a decoded polyline. */
    private fun elbowPath(): List<GeoPoint> {
        val up = (0..10).map { northOf(start, it * 20.0) }
        val corner = up.last()
        val across = (1..10).map { eastOf(corner, it * 20.0) }
        return up + across
    }

    @Test
    fun `distance between two points is measured in metres`() {
        assertEquals(200.0, start.distanceTo(northOf(start, 200.0)), 1.0)
    }

    @Test
    fun `an L-shaped route yields one turn between depart and arrive`() {
        val guidance = buildRouteGuidance(elbowPath(), "Carlton Gardens")

        assertEquals(400.0, guidance.totalMeters, 2.0)
        assertEquals(3, guidance.steps.size)
        assertEquals(Maneuver.Depart, guidance.steps.first().maneuver)
        assertEquals(Maneuver.Right, guidance.steps[1].maneuver)
        assertEquals(Maneuver.Arrive, guidance.steps.last().maneuver)
        // The turn is at the corner, 200 m along.
        assertEquals(200.0, guidance.steps[1].distanceFromStartMeters, 20.0)
        assertTrue(guidance.steps.first().instruction.contains("north"))
    }

    @Test
    fun `a straight route has no turns announced`() {
        val straight = (0..20).map { northOf(start, it * 20.0) }
        val guidance = buildRouteGuidance(straight, "State Library")

        assertEquals(2, guidance.steps.size)
        assertEquals(Maneuver.Depart, guidance.steps.first().maneuver)
        assertEquals(Maneuver.Arrive, guidance.steps.last().maneuver)
    }

    @Test
    fun `progress projects a position onto the route`() {
        val guidance = buildRouteGuidance(elbowPath(), "Carlton Gardens")
        // 30 m to the side of a point 100 m along the first leg.
        val beside = eastOf(northOf(start, 100.0), 30.0)
        val progress = guidance.progressAt(beside)!!

        assertEquals(100.0, progress.traveledMeters, 5.0)
        assertEquals(300.0, progress.remainingMeters, 5.0)
        assertEquals(30.0, progress.offRouteMeters, 3.0)
        assertTrue(!progress.hasArrived)
    }

    @Test
    fun `arrival is recognised near the end of the path`() {
        val path = elbowPath()
        val guidance = buildRouteGuidance(path, "Carlton Gardens")
        val progress = guidance.progressAt(path.last())!!

        assertTrue(progress.hasArrived)
        assertTrue(abs(progress.remainingMeters) < 1.0)
    }

    @Test
    fun `duration text is read back as minutes`() {
        assertEquals(26, parseDurationMinutes("26 mins"))
        assertEquals(65, parseDurationMinutes("1 hour 5 mins"))
        assertEquals(60, parseDurationMinutes("1 hour"))
        assertEquals(null, parseDurationMinutes(""))
        assertEquals(null, parseDurationMinutes("a while"))
    }
}
