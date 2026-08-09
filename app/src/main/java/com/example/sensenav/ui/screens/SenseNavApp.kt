package com.example.sensenav.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.example.sensenav.data.HistoryStore
import com.example.sensenav.data.LandmarkRepository
import com.example.sensenav.data.LocationProvider
import com.example.sensenav.data.MockSenseNavRepository
import com.example.sensenav.data.PlaceGeocoder
import com.example.sensenav.data.ProfileStore
import com.example.sensenav.data.RouteRepository
import com.example.sensenav.model.SavedPlace
import com.example.sensenav.model.GeoPoint
import com.example.sensenav.model.Refuge
import com.example.sensenav.model.RouteResult
import com.example.sensenav.model.ScoredRoute
import com.example.sensenav.model.SearchResult
import com.example.sensenav.model.SearchResultType
import com.example.sensenav.model.Sensitivity
import com.example.sensenav.data.WikimediaImageRepository
import com.example.sensenav.ui.refuge.LocalRefugeImages
import com.example.sensenav.ui.refuge.RefugeImage
import com.example.sensenav.ui.refuge.rememberRefugeImage
import com.example.sensenav.ui.map.MapRoute
import com.example.sensenav.ui.map.SenseNavMap
import com.example.sensenav.ui.map.rememberSenseNavCameraState
import com.example.sensenav.ui.map.toLatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import com.example.sensenav.data.RefugeRepository
import com.example.sensenav.data.WarningRepository
import com.example.sensenav.model.WarningInfo
import com.example.sensenav.data.SearchRepository

private const val TAG = "SenseNav"

private val SenseBlue = Color(0xFF2F5FBD)
private val SenseSoftBlue = Color(0xFFEAF2FF)
private val SenseInk = Color(0xFF1F2633)
private val SenseMuted = Color(0xFF798293)
private val SensePink = Color(0xFFFF4F86)
private val SenseGreen = Color(0xFF1B9F5A)
private val ScreenBg = Color(0xFFF7F9FD)

// Fallbacks only - the API supplies a colour per route and it takes precedence.
private val SenseRiskLow = Color(0xFF3B8BD4)
private val SenseRiskMedium = Color(0xFFEF9F27)
private val SenseRiskHigh = Color(0xFFE24B4A)

private fun ScoredRoute.displayColor(): Color =
    colorHex?.let { hex -> runCatching { Color(AndroidColor.parseColor(hex)) }.getOrNull() }
        ?: when (sensitivity) {
            Sensitivity.Low -> SenseRiskLow
            Sensitivity.Medium -> SenseRiskMedium
            Sensitivity.High -> SenseRiskHigh
            Sensitivity.Unknown -> SenseMuted
        }

private fun ScoredRoute.toMapRoute(isDimmed: Boolean = false) = MapRoute(
    points = path.map { it.toLatLng() },
    color = displayColor(),
    isDimmed = isDimmed
)

/** Quietest first, breaking ties on the raw pedestrian count. */
private fun List<ScoredRoute>.rankedBySensory(): List<ScoredRoute> =
    sortedWith(
        compareBy(
            { it.sensitivity.rank },
            { it.avgPedestrianCount ?: Double.MAX_VALUE }
        )
    )

private fun Refuge.toGeoPoint() = GeoPoint(latitude, longitude)

private fun GeoPoint.toCoordinateLabel() = "%.4f, %.4f".format(latitude, longitude)

/** Null for refuges that came from a source with no distance, so the row omits it. */
private fun Refuge.distanceLabel(): String? = distanceKm?.let { km ->
    if (km < 1.0) "${(km * 1000).roundToInt()} m away" else "%.1f km away".format(km)
}

private class PlaceNotFoundException(val query: String) : Exception()

private fun Throwable.toUserMessage(): String = when (this) {
    is PlaceNotFoundException ->
        "Couldn't find \"$query\". Try a fuller address, or clear the box to " +
            "route from your current location."
    is HttpException -> "The routing service returned an error (HTTP ${code()})."
    is IOException ->
        "Can't reach the routing service. Check your connection, or confirm the " +
            "API address is still current - the VM's IP is not static."
    else -> message ?: "Something went wrong while loading routes."
}

private sealed interface RoutesUiState {
    data object Loading : RoutesUiState
    data class Error(val message: String) : RoutesUiState
    data class Loaded(
        val origin: GeoPoint,
        val destination: GeoPoint,
        val result: RouteResult
    ) : RoutesUiState
}

private enum class AppScreen {
    Splash,
    Home,
    NearbyMap,
    Search,
    Routes,
    Warning
}

@Composable
fun SenseNavApp() {
    val repository = remember { MockSenseNavRepository() }
    var screen by remember { mutableStateOf(AppScreen.Splash) }
    val warningRepository = remember { WarningRepository() }
    val refugeRepository = remember { RefugeRepository() }
    val landmarkRepository = remember { LandmarkRepository() }
    val searchRepository = remember { SearchRepository() }
    // Held at the top so its lookup cache is shared by every screen.
    val refugeImages = remember { WikimediaImageRepository() }

    // History is device-local, so it is read straight from disk at start-up
    // rather than fetched, and mirrored here so the screens recompose on change.
    val context = LocalContext.current
    val historyStore = remember(context) { HistoryStore(context) }
    val profileStore = remember(context) { ProfileStore(context) }
    val locationProvider = remember(context) { LocationProvider(context) }
    val placeGeocoder = remember(context) { PlaceGeocoder(context) }

    var displayName by remember { mutableStateOf(profileStore.displayName()) }
    // Null means "follow the device". A pinned place overrides it everywhere,
    // so what the header says and what the app searches around stay the same.
    var savedPlace by remember { mutableStateOf(profileStore.savedPlace()) }
    var editingName by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf(false) }
    // Bumped whenever the user explicitly asks for their own location. Without
    // it, a lookup that came back empty - indoors, no fix yet - leaves the
    // header reading "Location unavailable" with nothing that retries it.
    var locationRefresh by remember { mutableStateOf(0) }
    var recentSearches by remember { mutableStateOf(historyStore.recentSearches()) }
    var recentlyViewed by remember { mutableStateOf(historyStore.recentlyViewed()) }

    var refuges by remember { mutableStateOf<List<Refuge>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<WarningInfo>>(emptyList()) }
    // Shown under the user's name on the home screen. Starts as a status line
    // rather than a placeholder suburb, so it never claims a location we have
    // not actually read from the device.
    var locationLabel by remember { mutableStateOf("Finding your location...") }

    // Both the label and the refuge list are anchored on the device position, so
    // permission is asked for here rather than waiting for the map screen to do
    // it. Asked once per session: a second prompt after a denial is dismissed by
    // the system anyway.
    var hasLocationPermission by remember { mutableStateOf(locationProvider.hasPermission()) }
    var permissionRequested by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> hasLocationPermission = granted.values.any { it } }

    // Held until the splash clears, so the dialog lands on the home screen
    // where the location it unlocks is visible.
    LaunchedEffect(screen, hasLocationPermission, savedPlace) {
        // Someone who has pinned a location is not asking to be located.
        if (savedPlace == null &&
            screen == AppScreen.Home && !hasLocationPermission && !permissionRequested
        ) {
            permissionRequested = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Recommendations come from the Calm-rated entries in the API's landmark
    // dataset, anchored on the pinned location or, failing that, where the user
    // actually is. Outside the dataset's coverage that query legitimately
    // returns nothing, so the older refuge endpoint and then the local catalogue
    // stand in rather than leaving the home screen empty. Re-runs when the pin
    // changes or permission is granted, so neither leaves the list stale.
    LaunchedEffect(hasLocationPermission, savedPlace, locationRefresh) {
        val pinned = savedPlace
        val near = if (pinned != null) {
            locationLabel = pinned.label
            GeoPoint(pinned.latitude, pinned.longitude)
        } else {
            locationLabel = "Finding your location..."
            val current = locationProvider.currentLocation()
            locationLabel = when {
                // Permission denied, location off, or no fix yet - say so instead
                // of naming the fallback point, which is not where the user is.
                current == null -> "Location unavailable"
                // Coordinates are a poor label but still honest when the device's
                // geocoder has no name for the point (offline, or unmapped area).
                else -> placeGeocoder.describe(current) ?: current.toCoordinateLabel()
            }
            current ?: repository.defaultOrigin
        }
        val anchorSource = if (pinned != null) "pinned '${pinned.label}'" else "device"
        Log.i(TAG, "Loading refuges near ${near.latitude},${near.longitude} (from $anchorSource)")

        val nearby = try {
            landmarkRepository.getLowSensoryRefuges(near)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // Silently falling back here is what makes a dead API look like a
            // working app serving stale data, so every drop-through is logged.
            Log.w(TAG, "Landmark API failed, falling back", e)
            emptyList()
        }

        if (nearby.isEmpty()) {
            Log.w(TAG, "No calm landmarks near the user - falling back to the refuge endpoint")
        } else {
            Log.i(TAG, "Loaded ${nearby.size} calm landmarks from the API")
        }

        refuges = nearby.ifEmpty {
            try {
                refugeRepository.getRefuges()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Refuge endpoint failed, using the local catalogue", e)
                repository.getRefuges()
            }
        }
    }

    LaunchedEffect(Unit) {
        warnings = try {
            warningRepository.getWarnings()
        } catch (e: Exception) {
            repository.getWarnings()
        }
    }

    var destination by remember { mutableStateOf(repository.getRefuges().first()) }

    LaunchedEffect(refuges) {
        if (refuges.isNotEmpty()) {
            destination = refuges.first()
        }
    }
    // Kept so the warning screen can draw the same routes without refetching.
    var loadedRoutes by remember { mutableStateOf<List<ScoredRoute>>(emptyList()) }
    // Endpoints typed on the search screen. A blank start means "my current
    // location"; a blank end falls back to the selected refuge's own name.
    var startPoint by remember { mutableStateOf("") }
    var endPoint by remember { mutableStateOf("") }

    // Opening a refuge is what counts as "viewed" - marker taps on the map are
    // browsing, not a visit, so they are not recorded.
    val openRefuge: (Refuge) -> Unit = { refuge ->
        destination = refuge
        // Clears any typed destination, so routing uses the refuge's own
        // coordinates instead of geocoding a stale search term.
        endPoint = refuge.name
        recentlyViewed = historyStore.recordViewed(refuge)
    }

    // Keeps the system back gesture consistent with the in-app back buttons.
    BackHandler(enabled = screen != AppScreen.Splash && screen != AppScreen.Home) {
        screen = when (screen) {
            AppScreen.Routes -> AppScreen.NearbyMap
            AppScreen.Warning -> AppScreen.Routes
            else -> AppScreen.Home
        }
    }

    MaterialTheme {
      CompositionLocalProvider(LocalRefugeImages provides refugeImages) {
        when (screen) {
            AppScreen.Splash -> SplashScreen(onFinished = { screen = AppScreen.Home })
            AppScreen.Home -> HomeScreen(
                refuges = refuges,
                displayName = displayName,
                locationLabel = locationLabel,
                onEditName = { editingName = true },
                onEditLocation = { editingLocation = true },
                onSearch = { screen = AppScreen.Search },
                onNearbyMap = { screen = AppScreen.NearbyMap },
                onOpenRefuge = { refuge ->
                    openRefuge(refuge)
                    screen = AppScreen.NearbyMap
                },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.NearbyMap -> NearbyMapScreen(
                refuges = refuges,
                initialRefuge = destination,
                onBack = { screen = AppScreen.Home },
                onSearch = { screen = AppScreen.Search },
                onNavigate = { refuge ->
                    openRefuge(refuge)
                    screen = AppScreen.Routes
                },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.Search -> SearchScreen(
                repository = repository,
                refuges = refuges,
                searchRepository = searchRepository,
                origin = startPoint,
                onOriginChange = { startPoint = it },
                recentSearches = recentSearches,
                recentlyViewed = recentlyViewed,
                onSearchRecorded = { result ->
                    recentSearches = historyStore.recordSearch(result)
                },
                onClearSearches = { recentSearches = historyStore.clearSearches() },
                onClearViewed = { recentlyViewed = historyStore.clearViewed() },
                onBack = { screen = AppScreen.Home },
                onRoute = { typedDestination ->
                    endPoint = typedDestination
                    screen = AppScreen.Routes
                },
                onRefugeSelected = { refuge ->
                    openRefuge(refuge)
                    screen = AppScreen.NearbyMap
                },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.Routes -> RouteOptionsScreen(
                destination = destination,
                pinnedOrigin = savedPlace?.let { GeoPoint(it.latitude, it.longitude) },
                defaultOrigin = repository.defaultOrigin,
                initialOrigin = startPoint,
                initialDestination = endPoint.ifBlank { destination.name },
                onBack = { screen = AppScreen.NearbyMap },
                onWarning = { screen = AppScreen.Warning },
                onNavigate = { screen = AppScreen.NearbyMap },
                onRoutesLoaded = { loadedRoutes = it }
            )
            AppScreen.Warning -> WarningScreen(
                warnings = warnings,
                routes = loadedRoutes,
                onBack = { screen = AppScreen.Routes },
                onReroute = { screen = AppScreen.Routes }
            )
        }

        if (editingName) {
            EditNameDialog(
                initialName = displayName,
                onDismiss = { editingName = false },
                onSave = { entered ->
                    displayName = profileStore.saveDisplayName(entered)
                    editingName = false
                }
            )
        }

        if (editingLocation) {
            EditLocationDialog(
                initialQuery = savedPlace?.label.orEmpty(),
                isPinned = savedPlace != null,
                geocoder = placeGeocoder,
                onDismiss = { editingLocation = false },
                onUseDeviceLocation = {
                    profileStore.clearPlace()
                    savedPlace = null
                    permissionRequested = false
                    locationRefresh++
                    editingLocation = false
                },
                onSave = { place ->
                    savedPlace = profileStore.savePlace(place)
                    editingLocation = false
                }
            )
        }
      }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9C90AE))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "M",
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "SenseNav",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(210.dp)
        ) {
            val ink = Color(0xFF241A35)
            drawCircle(
                color = ink.copy(alpha = 0.12f),
                center = Offset(size.width * 0.75f, size.height * 0.70f),
                radius = 68f
            )
            drawCircle(
                color = ink,
                center = Offset(size.width * 0.50f, size.height * 0.38f),
                radius = 76f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(8f)
            )
            drawCircle(
                color = ink,
                center = Offset(size.width * 0.50f, size.height * 0.38f),
                radius = 30f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(7f)
            )
            drawLine(ink, Offset(size.width * 0.22f, size.height * 0.72f), Offset(size.width * 0.43f, size.height * 0.46f), 8f)
            drawLine(ink, Offset(size.width * 0.66f, size.height * 0.38f), Offset(size.width * 0.66f, size.height * 0.78f), 8f)
        }
    }
}

@Composable
private fun HomeScreen(
    refuges: List<Refuge>,
    displayName: String,
    locationLabel: String,
    onEditName: () -> Unit,
    onEditLocation: () -> Unit,
    onSearch: () -> Unit,
    onNearbyMap: () -> Unit,
    onOpenRefuge: (Refuge) -> Unit,
    onWarning: () -> Unit
) {

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Text("Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNearbyMap,
                    icon = { Text("Map") },
                    label = { Text("Refuges") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFFFFD6A5), Color(0xFF4A2C2A))))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Both lines are edited in place. Padding keeps each one a
                    // usable tap target, and the tightened line heights stop
                    // that padding from reading as a gap between the two.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onEditName)
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                            color = SenseInk,
                            fontSize = 18.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$locationLabel  (edit)",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onEditLocation)
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                            color = SenseMuted,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SmallRoundButton("Search", onSearch)
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallRoundButton("Alert", onWarning)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SenseSoftBlue)
                        .clickable(onClick = onNearbyMap)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin", color = SenseBlue, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Discover quiet spaces and sensory-friendly routes around you",
                        modifier = Modifier.weight(1f),
                        color = SenseInk,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                    Text(">", color = SenseInk, fontWeight = FontWeight.Bold)
                }
            }

            // Heading and its caption share one item: as separate items the
            // column's 16dp arrangement spacing lands between them, on top of
            // the height the "See All" button already contributes.
            item {
                Column {
                    SectionHeader("Nearest Sensory Refuges", "See All", onNearbyMap)
                    Text(
                        // Deliberately not "near you" - the dataset is CBD-only, so
                        // for a suburban user the closest of these can still be a
                        // long way off. Each row carries its own distance. The
                        // caveat stays: these ratings are inferred, and someone
                        // choosing where to go on one is entitled to know that.
                        text = "Places rated calm, sorted by distance. Sensory ratings " +
                            "are indicative and reflect the type of each place, not " +
                            "live sensor readings.",
                        color = SenseMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(refuges.take(3)) { refuge ->
                        RefugePhotoCard(refuge = refuge, onClick = { onOpenRefuge(refuge) })
                    }
                }
            }

            // Everything the carousel did not already show, so no refuge appears
            // twice on the screen.
            items(refuges.drop(3)) { refuge ->
                RefugeListItem(refuge = refuge, onClick = { onOpenRefuge(refuge) })
            }
        }
    }
}

@Composable
private fun NearbyMapScreen(
    refuges: List<Refuge>,
    initialRefuge: Refuge,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onNavigate: (Refuge) -> Unit,
    onWarning: () -> Unit
) {
    var selectedRefuge by remember { mutableStateOf(initialRefuge) }
    val cameraPositionState = rememberSenseNavCameraState(
        target = LatLng(selectedRefuge.latitude, selectedRefuge.longitude),
        zoom = 14.5f
    )

    // Follow the selection, whether it came from a marker tap or the bottom card.
//    LaunchedEffect(selectedRefuge.id, cameraPositionState.isMoving) {
//        if (cameraPositionState.position.zoom > 0f) {
//                cameraPositionState.animate(
//                    CameraUpdateFactory.newLatLngZoom(
//                        LatLng(selectedRefuge.latitude, selectedRefuge.longitude),
//                        15f
//                    )
//                )
//            }
//        }

    Box(modifier = Modifier.fillMaxSize()) {
        SenseNavMap(
            modifier = Modifier.fillMaxSize(),
            refuges = refuges,
            selectedRefugeId = selectedRefuge.id,
            onRefugeClick = { selectedRefuge = it },
            cameraPositionState = cameraPositionState,
            enableMyLocation = true,
            // Keeps the Google logo and controls clear of the overlays.
            contentPadding = PaddingValues(top = 150.dp, bottom = 210.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallRoundButton("<", onBack)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Nearby Sensory Refuges",
                modifier = Modifier.weight(1f),
                color = SenseInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            SmallRoundButton("Filter", onSearch)
        }

        SearchPill(
            text = "Search quiet spaces, libraries...",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 88.dp)
                .padding(horizontal = 20.dp),
            onClick = onSearch
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(18.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                RefugeListItem(refuge = selectedRefuge, onClick = {}, showCredit = true)
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(selectedRefuge) },
                        colors = ButtonDefaults.buttonColors(containerColor = SenseBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Navigate Here")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = onWarning, shape = RoundedCornerShape(12.dp)) {
                        Text("Alert")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    repository: MockSenseNavRepository,
    refuges: List<Refuge>,
    searchRepository: SearchRepository,
    origin: String,
    onOriginChange: (String) -> Unit,
    recentSearches: List<SearchResult>,
    recentlyViewed: List<Refuge>,
    onSearchRecorded: (SearchResult) -> Unit,
    onClearSearches: () -> Unit,
    onClearViewed: () -> Unit,
    onBack: () -> Unit,
    onRoute: (String) -> Unit,
    onRefugeSelected: (Refuge) -> Unit,
    onWarning: () -> Unit
) {
    // The destination field doubles as the search box. What is typed and what
    // has been searched for are separate: the query is only sent on enter.
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var backendSearchResults by remember {
        mutableStateOf<List<SearchResult>>(emptyList())
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val destinationFocus = remember { FocusRequester() }

    // Land the user in the destination field so they can type straight away.
    LaunchedEffect(Unit) {
        destinationFocus.requestFocus()
    }

    // Emptying the box drops back to the history rather than stranding the
    // results of a search the user has visibly deleted.
    LaunchedEffect(query) {
        if (query.isBlank()) submittedQuery = ""
    }

    LaunchedEffect(submittedQuery) {
        // "My location" is a chosen marker, not a place to look up, so it leaves
        // the list on the history rather than reporting no matches for itself.
        if (submittedQuery.isBlank() || submittedQuery.isMyLocation()) {
            backendSearchResults = emptyList()
            searching = false
        } else {
            searching = true
            backendSearchResults = try {
                searchRepository.search(submittedQuery).map {
                    SearchResult(
                        id = it.id.toString(),
                        title = it.name,
                        subtitle = it.address,
                        type = SearchResultType.Refuge,
                        sensoryLabel = it.sensoryLevel
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
            searching = false
        }
    }

    val showingHistory = submittedQuery.isBlank() || submittedQuery.isMyLocation()
    val shown = if (showingHistory) recentSearches else backendSearchResults

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // Both history lists hold up to ten entries, so the page has to scroll.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallRoundButton("<", onBack)
            Text(
                text = "Search",
                modifier = Modifier.weight(1f),
                color = SenseInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            SmallRoundButton("Alert", onWarning)
        }

        Spacer(modifier = Modifier.height(18.dp))
        // The start is left blank on purpose: empty means "my current location",
        // which is what the routing screen falls back to.
        RoutePlannerCard(
            originInput = origin,
            onOriginInputChange = onOriginChange,
            destinationInput = query,
            onDestinationInputChange = { query = it },
            onSubmit = {
                submittedQuery = query.trim()
                keyboard?.hide()
            },
            modifier = Modifier.fillMaxWidth(),
            destinationFocusRequester = destinationFocus,
            onRoute = {
                keyboard?.hide()
                val typed = query.trim()
                // The marker is not a place someone searched for, so it stays out
                // of the history list.
                if (!typed.isMyLocation()) {
                    onSearchRecorded(
                        SearchResult(
                            id = "typed_${typed.lowercase()}",
                            title = typed,
                            subtitle = "Searched destination",
                            type = SearchResultType.Route,
                            sensoryLabel = ""
                        )
                    )
                }
                onRoute(typed)
            }
        )

        Spacer(modifier = Modifier.height(26.dp))
        SectionHeader(
            title = if (showingHistory) "Recent Searches" else "Search Results",
            action = "Clear All".takeIf { showingHistory && recentSearches.isNotEmpty() },
            onAction = onClearSearches
        )

        Spacer(modifier = Modifier.height(6.dp))
        when {
            searching -> HistoryEmptyHint("Searching...")
            showingHistory && shown.isEmpty() ->
                HistoryEmptyHint("Places you search for will be listed here.")
            !showingHistory && shown.isEmpty() ->
                HistoryEmptyHint("No matches for \"$submittedQuery\".")
        }
        shown.forEach { result ->
            SearchResultRow(
                result = result,
                onClick = {
                    onSearchRecorded(result)
                    val refuge = refuges.firstOrNull {
                        it.name.equals(result.title, ignoreCase = true)
                    } ?: repository.getRefugeDetail(result.id)
                    when {
                        refuge != null -> onRefugeSelected(refuge)
                        result.type == SearchResultType.Route ||
                            result.type == SearchResultType.Station -> onRoute(result.title)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionHeader(
            title = "Recently Viewed",
            action = "Clear All".takeIf { recentlyViewed.isNotEmpty() },
            onAction = onClearViewed
        )
        if (recentlyViewed.isEmpty()) {
            HistoryEmptyHint("Refuges you open will be listed here.")
        }
        recentlyViewed.forEach { refuge ->
            RefugeListItem(refuge = refuge, onClick = { onRefugeSelected(refuge) })
        }
    }
}

@Composable
private fun RouteOptionsScreen(
    destination: Refuge,
    pinnedOrigin: GeoPoint?,
    defaultOrigin: GeoPoint,
    initialOrigin: String,
    initialDestination: String,
    onBack: () -> Unit,
    onWarning: () -> Unit,
    onNavigate: () -> Unit,
    onRoutesLoaded: (List<ScoredRoute>) -> Unit
) {
    val context = LocalContext.current
    val routeRepository = remember { RouteRepository() }
    val locationProvider = remember { LocationProvider(context) }
    val placeGeocoder = remember { PlaceGeocoder(context) }
    val keyboard = LocalSoftwareKeyboardController.current

    // What the user is typing, and the values actually submitted for routing.
    // A blank origin means "use my current location".
    var originInput by remember(initialOrigin) { mutableStateOf(initialOrigin) }
    var submittedOrigin by remember(initialOrigin) { mutableStateOf(initialOrigin) }
    // Seeded from the refuge that was tapped, or from what was typed on the
    // search screen, and reset if either changes.
    var destinationInput by remember(initialDestination) { mutableStateOf(initialDestination) }
    var submittedDestination by remember(initialDestination) {
        mutableStateOf(initialDestination)
    }

    var retryCount by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<RoutesUiState>(RoutesUiState.Loading) }
    val cameraPositionState = rememberSenseNavCameraState(
        target = LatLng(destination.latitude, destination.longitude),
        zoom = 14.2f
    )

    LaunchedEffect(destination.id, submittedOrigin, submittedDestination, retryCount, pinnedOrigin) {
        state = RoutesUiState.Loading
        state = try {
            // "My location" is a marker the user picked from the suggestions, not
            // a place name - sending it to the geocoder would just fail. A blank
            // box has always meant the same thing, so both resolve here.
            //
            // The device position comes first. The suggestion says "use where you
            // are now", and a pinned location is a place the user chose to browse
            // around, not a claim about where they are standing - routing from it
            // would send them walking from somewhere they are not. The pin only
            // stands in when there is no fix to be had.
            suspend fun whereTheUserIs(): GeoPoint =
                locationProvider.currentLocation() ?: pinnedOrigin ?: defaultOrigin

            val origin = if (submittedOrigin.isBlank() || submittedOrigin.isMyLocation()) {
                whereTheUserIs()
            } else {
                placeGeocoder.resolve(submittedOrigin)
                    ?: throw PlaceNotFoundException(submittedOrigin)
            }

            val destinationQuery = submittedDestination.ifBlank { destination.name }
            val target = when {
                destinationQuery.isMyLocation() -> whereTheUserIs()
                // Unedited: use the refuge's own coordinates rather than
                // round-tripping its name through the geocoder.
                destinationQuery.equals(destination.name, ignoreCase = true) ->
                    destination.toGeoPoint()
                else -> placeGeocoder.resolve(destinationQuery)
                    ?: throw PlaceNotFoundException(destinationQuery)
            }

            val result = routeRepository.getRoutes(origin, target)
            onRoutesLoaded(result.routes)
            RoutesUiState.Loaded(origin, target, result)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            RoutesUiState.Error(error.toUserMessage())
        }
    }

    val submitPlaces = {
        keyboard?.hide()
        submittedOrigin = originInput.trim()
        submittedDestination = destinationInput.trim()
    }

    val loaded = state as? RoutesUiState.Loaded
    val ranked = loaded?.result?.routes?.rankedBySensory().orEmpty()

    // Frame the whole trip once geometry arrives.
    LaunchedEffect(ranked) {
        val allPoints = ranked.flatMap { it.path }
        if (allPoints.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.builder()
            .apply { allPoints.forEach { include(it.toLatLng()) } }
            .build()
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }.onFailure {
            // Bounds animation needs a laid-out map; fall back to centring.
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(bounds.center, 14f)
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SenseNavMap(
                modifier = Modifier.fillMaxSize(),
                // Quietest route drawn last so it sits on top.
                routes = ranked.mapIndexed { index, route ->
                    route.toMapRoute(isDimmed = index != 0)
                },
                origin = loaded?.origin?.toLatLng(),
                destination = loaded?.destination?.toLatLng(),
                originColor = SenseBlue,
                destinationColor = SensePink,
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(top = 300.dp)
            )
            RoutePlannerCard(
                originInput = originInput,
                onOriginInputChange = { originInput = it },
                destinationInput = destinationInput,
                onDestinationInputChange = { destinationInput = it },
                onSubmit = submitPlaces,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // Clears the back button sitting above it.
                    .padding(top = 90.dp)
            )
            SmallRoundButton(
                text = "<",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 34.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Route Options",
                        modifier = Modifier.weight(1f),
                        color = SenseInk,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SmallRoundButton("X", onBack)
                }
                Spacer(modifier = Modifier.height(8.dp))

                when (val current = state) {
                    is RoutesUiState.Loading -> RoutesLoading()

                    is RoutesUiState.Error -> RoutesError(
                        message = current.message,
                        onRetry = { retryCount++ }
                    )

                    is RoutesUiState.Loaded -> {
                        if (!current.result.isScored) {
                            // No sensor coverage: must not imply a sensory rating.
                            Text(
                                text = "No pedestrian sensor coverage for this trip - " +
                                    "showing the plain walking route.",
                                color = SenseMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        ranked.forEachIndexed { index, route ->
                            RouteCard(
                                route = route,
                                isRecommended = index == 0 && current.result.isScored,
                                onDirections = {
                                    if (route.sensitivity == Sensitivity.High) {
                                        onWarning()
                                    } else {
                                        onNavigate()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Both ends are free text; a blank origin means "start from my location".
 *
 * [onSubmit] fires on the keyboard's enter/search key, [onRoute] on the button.
 * The routing screen points both at the same handler; the search screen keeps
 * them apart, so enter runs a search and the button opens the route options.
 */
@Composable
private fun RoutePlannerCard(
    originInput: String,
    onOriginInputChange: (String) -> Unit,
    destinationInput: String,
    onDestinationInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    destinationFocusRequester: FocusRequester? = null,
    onRoute: () -> Unit = onSubmit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            PlaceField(
                marker = { OriginDot() },
                value = originInput,
                onValueChange = onOriginInputChange,
                placeholder = "Your location",
                imeAction = ImeAction.Next,
                onSubmit = onSubmit
            )

            EndpointConnector()

            PlaceField(
                marker = { DestinationDot() },
                value = destinationInput,
                onValueChange = onDestinationInputChange,
                placeholder = "Where to?",
                imeAction = ImeAction.Search,
                onSubmit = onSubmit,
                modifier = destinationFocusRequester
                    ?.let { Modifier.focusRequester(it) }
                    ?: Modifier
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRoute,
                enabled = destinationInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SenseBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Route")
            }
        }
    }
}

/** Hollow ring for the start point, mirroring the usual maps convention. */
@Composable
private fun OriginDot() {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(width = 3.dp, color = SenseBlue, shape = CircleShape)
    )
}

/** Solid rounded square for the destination, so the two ends read differently. */
@Composable
private fun DestinationDot() {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SensePink)
    )
}

/** The trail of dots joining the two endpoint markers. */
@Composable
private fun EndpointConnector() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .width(MarkerColumnWidth)
                .height(18.dp)
        ) {
            val centreX = size.width / 2f
            val step = 6.dp.toPx()
            val radius = 1.6.dp.toPx()
            var y = step / 2f
            while (y < size.height) {
                drawCircle(
                    color = SenseMuted.copy(alpha = 0.45f),
                    radius = radius,
                    center = Offset(centreX, y)
                )
                y += step
            }
        }
    }
}

private val MarkerColumnWidth = 22.dp

@Composable
private fun PlaceField(
    marker: @Composable () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // Every place box in the app is one of these, so offering the suggestion
    // here is what puts it on the search page and the route planner alike.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.width(MarkerColumnWidth),
                contentAlignment = Alignment.Center
            ) {
                marker()
            }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).then(modifier),
                placeholder = { Text(placeholder, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = senseTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    // "Next" moves From -> To; only "Search" fires the request.
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onSearch = { onSubmit() }
                ),
                trailingIcon = {
                    if (value.isNotBlank()) {
                        TextButton(onClick = { onValueChange("") }) {
                            Text("Clear", color = SenseMuted, fontSize = 11.sp)
                        }
                    }
                }
            )
        }

        if (matchesMyLocation(value)) {
            MyLocationSuggestion(
                onClick = {
                    onValueChange(MY_LOCATION_LABEL)
                    focusManager.clearFocus()
                }
            )
        }
    }
}

/**
 * Pinned explicitly rather than inherited, so these fields stay legible even if
 * the colour scheme changes again.
 */
@Composable
private fun senseTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SenseInk,
    unfocusedTextColor = SenseInk,
    disabledTextColor = SenseMuted,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = SenseBlue,
    focusedBorderColor = SenseBlue,
    unfocusedBorderColor = Color(0xFFD8DEE9),
    focusedPlaceholderColor = SenseMuted,
    unfocusedPlaceholderColor = SenseMuted
)

@Composable
private fun RoutesLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = SenseBlue)
        Spacer(modifier = Modifier.width(14.dp))
        Text("Scoring routes nearby...", color = SenseMuted, fontSize = 14.sp)
    }
}

@Composable
private fun RoutesError(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Couldn't load routes", color = SenseInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, color = SenseMuted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = SenseBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun WarningScreen(
    warnings: List<WarningInfo>,
    routes: List<ScoredRoute>,
    onBack: () -> Unit,
    onReroute: () -> Unit
) {
    val warning = warnings.firstOrNull() ?: return
    // The busiest of the routes just scored is the one being warned about.
    val flaggedRoute = routes.maxByOrNull { it.avgPedestrianCount ?: 0.0 }
    val mapCentre = flaggedRoute?.path?.let { it[it.size / 2] }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SenseNavMap(
                modifier = Modifier.fillMaxSize(),
                routes = listOfNotNull(flaggedRoute?.toMapRoute()),
                cameraPositionState = rememberSenseNavCameraState(
                    target = mapCentre?.toLatLng() ?: LatLng(-37.8183, 144.9671),
                    zoom = 14.5f
                ),
                contentPadding = PaddingValues(top = 90.dp)
            )
            SmallRoundButton(
                text = "<",
                onClick = onBack,
                modifier = Modifier.padding(start = 18.dp, top = 34.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "! ${warning.title}",
                    color = SenseInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "${warning.locationName} (High Congestion)",
                    color = SenseInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Crowd density at ${warning.densityPercent}% - ${warning.riskSummary}",
                    color = SenseMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(22.dp))
                InfoLine("Data Source", warning.dataSource)
                InfoLine("Suggested Action", warning.suggestedAction)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onReroute,
                    colors = ButtonDefaults.buttonColors(containerColor = SenseBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Reroute")
                }
            }
        }
    }
}

@Composable
private fun EditNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text("Name shown on the home screen") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(value) })
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Pins the location by name. The typed text is geocoded before it is accepted,
 * so a place that cannot be found is rejected here rather than silently
 * anchoring the whole refuge list somewhere wrong.
 */
@Composable
private fun EditLocationDialog(
    initialQuery: String,
    isPinned: Boolean,
    geocoder: PlaceGeocoder,
    onDismiss: () -> Unit,
    onUseDeviceLocation: () -> Unit,
    onSave: (SavedPlace) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val submit = {
        val typed = query.trim()
        if (typed.isEmpty()) {
            error = "Enter a suburb or address."
        } else {
            checking = true
            error = null
            scope.launch {
                val point = runCatching { geocoder.resolve(typed) }.getOrNull()
                checking = false
                if (point == null) {
                    error = "Couldn't find \"$typed\". Try a suburb or a fuller address."
                } else {
                    onSave(SavedPlace(typed, point.latitude, point.longitude))
                }
            }
            Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your location") },
        text = {
            Column {
                Text(
                    text = "Refuges are found around this location. Leave it on your " +
                        "device location to follow you as you move.",
                    color = SenseMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !checking,
                    placeholder = { Text("Suburb or address") },
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() })
                )
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = SensePink, fontSize = 12.sp, lineHeight = 16.sp)
                }
                // Always offered, not just when pinned: unpinned it is the retry
                // for a lookup that came back empty, which is otherwise a dead end.
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onUseDeviceLocation) {
                    Text(
                        text = if (isPinned) {
                            "Use my device location instead"
                        } else {
                            "Find my location again"
                        },
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !checking) {
                Text(if (checking) "Checking..." else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Stands in for a history list that has nothing in it yet. */
@Composable
private fun HistoryEmptyHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 10.dp),
        color = SenseMuted,
        fontSize = 13.sp
    )
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = SenseInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (action != null) {
            TextButton(onClick = { onAction?.invoke() }) {
                Text(action, color = SenseBlue, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RefugePhotoCard(refuge: Refuge, onClick: () -> Unit) {
    val image = rememberRefugeImage(refuge)

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(188.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        RefugeImage(refuge = refuge, image = image, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC101826))))
        )
        Text(
            text = if (refuge.isSaved) "Saved" else "Save",
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            color = if (refuge.isSaved) SensePink else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(refuge.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(refuge.category, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            refuge.rating?.let { rating ->
                Text("Star $rating", color = Color(0xFFFFD166), fontSize = 12.sp)
            }
            refuge.distanceLabel()?.let { distance ->
                Text(distance, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            // The CC licences these photos carry require the credit to travel
            // with the image, so it sits on the card rather than in a settings
            // screen nobody opens.
            image?.let {
                Text(
                    text = it.credit,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RefugeListItem(
    refuge: Refuge,
    onClick: () -> Unit,
    showCredit: Boolean = false
) {
    val image = rememberRefugeImage(refuge)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RefugeImage(
            refuge = refuge,
            image = image,
            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = refuge.name,
                    modifier = Modifier.weight(1f),
                    color = SenseInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                refuge.rating?.let { rating ->
                    Text("Star $rating", color = SenseInk, fontSize = 12.sp)
                }
                refuge.distanceLabel()?.let { distance ->
                    Text(distance, color = SenseMuted, fontSize = 12.sp)
                }
            }
            Text(refuge.subtitle, color = SenseMuted, fontSize = 12.sp)
            Text(refuge.sensoryTag, color = SenseBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            // Only where there is room for it - the list rows are covered by the
            // blanket Wikimedia credit under the section heading instead.
            if (showCredit && image != null) {
                Text(
                    text = "Photo: ${image.credit}",
                    color = SenseMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Autocomplete row offered beneath a place field while the user is typing
 * towards their own location. Tapping it fills the field with
 * [MY_LOCATION_LABEL], which the routing screen resolves to a real position
 * rather than sending to the geocoder.
 */
@Composable
private fun MyLocationSuggestion(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Indented to sit under the text field rather than the marker column.
            .padding(start = MarkerColumnWidth + 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(SenseSoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Text("Pin", color = SenseBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = MY_LOCATION_LABEL,
                color = SenseInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Use where you are now", color = SenseMuted, fontSize = 11.sp)
        }
    }
}

/**
 * What a place field holds when it means "wherever I am". Kept as a sentinel
 * rather than a blank field so the choice is visible in the box, and resolved
 * to coordinates at routing time instead of being geocoded as text.
 */
private const val MY_LOCATION_LABEL = "My location"

private fun String.isMyLocation(): Boolean = trim().equals(MY_LOCATION_LABEL, ignoreCase = true)

/**
 * Whether partly-typed text is reaching for the user's own location, so "loc",
 * "my loc", "current" or "gps" all surface the suggestion.
 */
private fun matchesMyLocation(query: String): Boolean {
    val typed = query.trim().lowercase()
    if (typed.length < 2) return false
    if (typed == MY_LOCATION_LABEL.lowercase()) return false
    return MY_LOCATION_TERMS.any { it.contains(typed) || typed.contains(it) }
}

private val MY_LOCATION_TERMS = listOf(
    "my location", "current location", "my current location",
    "my position", "where i am", "gps", "here", "me"
)

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(ScreenBg),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = SenseMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, color = SenseInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(result.subtitle, color = SenseMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RouteCard(route: ScoredRoute, isRecommended: Boolean, onDirections: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(route.displayColor())
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = route.summary,
                modifier = Modifier.weight(1f),
                color = SenseInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isRecommended) {
                Text("Recommended", color = SenseGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = listOfNotNull(
                route.durationText.takeIf { it.isNotBlank() },
                route.distanceText.takeIf { it.isNotBlank() }
            ).joinToString(" - "),
            color = SenseMuted,
            fontSize = 13.sp
        )
        Text(
            text = if (route.isScored) {
                "${route.sensitivity} sensory load" +
                    (route.avgPedestrianCount?.let { " - ~${it.roundToInt()} people/min nearby" } ?: "")
            } else {
                "No sensor data for this route"
            },
            color = if (route.isScored) route.displayColor() else SenseMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDirections, colors = ButtonDefaults.buttonColors(containerColor = SenseSoftBlue, contentColor = SenseBlue)) {
                Text("Directions")
            }
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SenseSoftBlue, contentColor = SenseBlue)) {
                Text("Share")
            }
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SenseSoftBlue, contentColor = SenseBlue)) {
                Text("Save")
            }
        }
    }
}


@Composable
private fun SearchPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Search", color = SenseMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f), color = SenseInk, fontSize = 14.sp, maxLines = 1)
        Text("X", color = SenseMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SmallRoundButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = SenseInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(SenseSoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Text("i", color = SenseBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, color = SenseMuted, fontSize = 12.sp)
            Text(value, color = SenseInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
