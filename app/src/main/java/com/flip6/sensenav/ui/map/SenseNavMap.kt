package com.flip6.sensenav.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.flip6.sensenav.BuildConfig
import com.flip6.sensenav.model.GeoPoint
import com.flip6.sensenav.model.Refuge
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor

/** Melbourne CBD - the fallback camera target when nothing else is selected. */
val MelbourneCbd = LatLng(-37.8136, 144.9631)

fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

/** A path to stroke on the map, already coloured by sensory risk. */
data class MapRoute(
    val points: List<LatLng>,
    val color: Color,
    val isDimmed: Boolean = false
)

private val EndpointDotSize = 26.dp

/**
 * A filled dot with a white ring, matching the start/end markers in the route
 * planner card. Built as a bitmap rather than a geographic circle so it keeps a
 * constant on-screen size at every zoom level.
 */
private fun buildDotIcon(fillArgb: Int, diameterPx: Int): BitmapDescriptor {
    val bitmap = createBitmap(diameterPx, diameterPx)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centre = diameterPx / 2f

    // White ring first, so the dot separates from whatever it sits on.
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(centre, centre, centre, paint)
    paint.color = fillArgb
    canvas.drawCircle(centre, centre, centre * 0.62f, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
fun rememberSenseNavCameraState(
    target: LatLng = MelbourneCbd,
    zoom: Float = 14f
): CameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(target, zoom)
}

/**
 * The app's single real map surface. Renders refuge markers and route
 * polylines over Google Maps, and degrades to a readable setup card when no
 * API key has been configured rather than showing a blank grey tile.
 */
@Composable
fun SenseNavMap(
    modifier: Modifier = Modifier,
    refuges: List<Refuge> = emptyList(),
    selectedRefugeId: String? = null,
    onRefugeClick: (Refuge) -> Unit = {},
    routes: List<MapRoute> = emptyList(),
    origin: LatLng? = null,
    destination: LatLng? = null,
    originColor: Color = Color(0xFF2F5FBD),
    destinationColor: Color = Color(0xFFFF4F86),
    cameraPositionState: CameraPositionState = rememberSenseNavCameraState(),
    enableMyLocation: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // Compose previews and unkeyed builds can't render a real map.
    if (BuildConfig.MAPS_API_KEY.isBlank() || LocalInspectionMode.current) {
        MapUnavailableCard(modifier = modifier)
        return
    }

    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocationPermission = granted.values.any { it }
    }

    LaunchedEffect(enableMyLocation) {
        if (enableMyLocation && !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = enableMyLocation && hasLocationPermission
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = enableMyLocation && hasLocationPermission,
            mapToolbarEnabled = false,
            compassEnabled = true
        )
    ) {
        // Dimmed alternatives first so the highlighted route strokes over them.
        routes.sortedByDescending { it.isDimmed }.forEach { route ->
            if (route.points.size >= 2) {
                Polyline(
                    points = route.points,
                    color = if (route.isDimmed) route.color.copy(alpha = 0.35f) else route.color,
                    width = if (route.isDimmed) 10f else 16f
                )
            }
        }

        // Endpoint dots, drawn above the routes so A and B stay readable.
        val density = LocalDensity.current
        val dotSizePx = remember(density) { with(density) { EndpointDotSize.roundToPx() } }

        origin?.let { point ->
            val icon = remember(originColor, dotSizePx) {
                buildDotIcon(originColor.toArgb(), dotSizePx)
            }
            Marker(
                state = rememberMarkerState(
                    key = "origin-${point.latitude},${point.longitude}",
                    position = point
                ),
                title = "Start",
                icon = icon,
                anchor = Offset(0.5f, 0.5f), // centre the dot on the point
                zIndex = 1f
            )
        }

        destination?.let { point ->
            val icon = remember(destinationColor, dotSizePx) {
                buildDotIcon(destinationColor.toArgb(), dotSizePx)
            }
            Marker(
                state = rememberMarkerState(
                    key = "destination-${point.latitude},${point.longitude}",
                    position = point
                ),
                title = "Destination",
                icon = icon,
                anchor = Offset(0.5f, 0.5f),
                zIndex = 1f
            )
        }

        refuges.forEach { refuge ->
            val markerState = rememberMarkerState(
                key = refuge.id,
                position = LatLng(refuge.latitude, refuge.longitude)
            )
            Marker(
                state = markerState,
                title = refuge.name,
                // Rating and distance are only present for some sources, so the
                // snippet is assembled from whatever this refuge actually has.
                snippet = listOfNotNull(
                    refuge.sensoryTag,
                    refuge.rating?.toString(),
                    refuge.distanceKm?.let { "$it km" }
                ).joinToString(" - "),
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (refuge.id == selectedRefugeId) {
                        BitmapDescriptorFactory.HUE_ROSE
                    } else {
                        BitmapDescriptorFactory.HUE_AZURE
                    }
                ),
                onClick = {
                    onRefugeClick(refuge)
                    false // keep default behaviour: show info window, centre marker
                }
            )
        }
    }
}

@Composable
private fun MapUnavailableCard(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFEAF2FF)),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                text = "Map needs an API key",
                color = Color(0xFF1F2633),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Box(modifier = Modifier.height(10.dp))
            Text(
                text = "Add MAPS_API_KEY=your_key_here to local.properties, " +
                    "then sync Gradle and rebuild.",
                color = Color(0xFF798293),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
