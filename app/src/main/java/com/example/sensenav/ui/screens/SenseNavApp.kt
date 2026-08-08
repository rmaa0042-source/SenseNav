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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.example.sensenav.data.LocationProvider
import com.example.sensenav.data.MockSenseNavRepository
import com.example.sensenav.data.PlaceGeocoder
import com.example.sensenav.data.RouteRepository
import com.example.sensenav.model.GeoPoint
import com.example.sensenav.model.Refuge
import com.example.sensenav.model.RouteResult
import com.example.sensenav.model.ScoredRoute
import com.example.sensenav.model.SearchResult
import com.example.sensenav.model.SearchResultType
import com.example.sensenav.model.Sensitivity
import com.example.sensenav.ui.map.MapRoute
import com.example.sensenav.ui.map.SenseNavMap
import com.example.sensenav.ui.map.rememberSenseNavCameraState
import com.example.sensenav.ui.map.toLatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import com.example.sensenav.data.RefugeRepository
import com.example.sensenav.data.WarningRepository
import com.example.sensenav.model.WarningInfo

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

    var refuges by remember { mutableStateOf<List<Refuge>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<WarningInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        refuges = try {
            refugeRepository.getRefuges()
        } catch (e: Exception) {
            repository.getRefuges()
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

    // Keeps the system back gesture consistent with the in-app back buttons.
    BackHandler(enabled = screen != AppScreen.Splash && screen != AppScreen.Home) {
        screen = when (screen) {
            AppScreen.Routes -> AppScreen.NearbyMap
            AppScreen.Warning -> AppScreen.Routes
            else -> AppScreen.Home
        }
    }

    MaterialTheme {
        when (screen) {
            AppScreen.Splash -> SplashScreen(onFinished = { screen = AppScreen.Home })
            AppScreen.Home -> HomeScreen(
                refuges = refuges,
                onSearch = { screen = AppScreen.Search },
                onNearbyMap = { screen = AppScreen.NearbyMap },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.NearbyMap -> NearbyMapScreen(
                refuges = refuges,
                initialRefuge = destination,
                onBack = { screen = AppScreen.Home },
                onSearch = { screen = AppScreen.Search },
                onNavigate = { refuge ->
                    destination = refuge
                    screen = AppScreen.Routes
                },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.Search -> SearchScreen(
                repository = repository,
                refuges = refuges,
                onBack = { screen = AppScreen.Home },
                onRouteSelected = { screen = AppScreen.Routes },
                onRefugeSelected = { refuge ->
                    destination = refuge
                    screen = AppScreen.NearbyMap
                },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.Routes -> RouteOptionsScreen(
                destination = destination,
                defaultOrigin = repository.defaultOrigin,
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
    onSearch: () -> Unit,
    onNearbyMap: () -> Unit,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Matr Kohler", color = SenseInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Melbourne CBD, VIC", color = SenseMuted, fontSize = 12.sp)
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

            item { SectionHeader("Low Sensory Refuges", "See All", onNearbyMap) }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(refuges.take(3)) { refuge ->
                        RefugePhotoCard(refuge = refuge, onClick = onNearbyMap)
                    }
                }
            }

            item { SectionHeader("Sensory Tolerance Options", "See All", null) }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Low Noise", "Low Crowd", "No Construction")) { label ->
                        FilterPill(label = label, selected = label == "All")
                    }
                }
            }

            items(refuges.drop(2)) { refuge ->
                RefugeListItem(refuge = refuge, onClick = onNearbyMap)
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
                RefugeListItem(refuge = selectedRefuge, onClick = {})
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
    onBack: () -> Unit,
    onRouteSelected: () -> Unit,
    onRefugeSelected: (Refuge) -> Unit,
    onWarning: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) {
        repository.getRecentSearches()
    } else {
        val normalized = query.trim().lowercase()

        refuges
            .filter {
                it.name.lowercase().contains(normalized) ||
                        it.subtitle.lowercase().contains(normalized) ||
                        it.sensoryTag.lowercase().contains(normalized)
            }
            .map {
                SearchResult(
                    id = it.id,
                    title = it.name,
                    subtitle = it.subtitle,
                    type = SearchResultType.Refuge,
                    sensoryLabel = it.sensoryTag
                )
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
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
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search...") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = senseTextFieldColors()
        )

        Spacer(modifier = Modifier.height(26.dp))
        SectionHeader(if (query.isBlank()) "Recent Searches" else "Search Results", "Clear All", null)

        Spacer(modifier = Modifier.height(6.dp))
        shown.forEach { result ->
            SearchResultRow(
                result = result,
                onClick = {
                    val refuge = refuges.firstOrNull {
                        it.name.equals(result.title, ignoreCase = true)
                    } ?: repository.getRefugeDetail(result.id)
                    when {
                        refuge != null -> onRefugeSelected(refuge)
                        result.type == SearchResultType.Route ||
                            result.type == SearchResultType.Station -> onRouteSelected()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionHeader("Recently Viewed", "See All", null)
        refuges.take(3).forEach { refuge ->
            RefugeListItem(refuge = refuge, onClick = { onRefugeSelected(refuge) })
        }
    }
}

@Composable
private fun RouteOptionsScreen(
    destination: Refuge,
    defaultOrigin: GeoPoint,
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
    var originInput by remember { mutableStateOf("") }
    var submittedOrigin by remember { mutableStateOf("") }
    // Seeded from the refuge that was tapped, and reset if that changes.
    var destinationInput by remember(destination.id) { mutableStateOf(destination.name) }
    var submittedDestination by remember(destination.id) { mutableStateOf(destination.name) }

    var retryCount by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<RoutesUiState>(RoutesUiState.Loading) }
    val cameraPositionState = rememberSenseNavCameraState(
        target = LatLng(destination.latitude, destination.longitude),
        zoom = 14.2f
    )

    LaunchedEffect(destination.id, submittedOrigin, submittedDestination, retryCount) {
        state = RoutesUiState.Loading
        state = try {
            val origin = if (submittedOrigin.isBlank()) {
                // Real device position when we have it; the CBD fallback keeps
                // the screen usable on an emulator or with location denied.
                locationProvider.currentLocation() ?: defaultOrigin
            } else {
                placeGeocoder.resolve(submittedOrigin)
                    ?: throw PlaceNotFoundException(submittedOrigin)
            }

            val destinationQuery = submittedDestination.ifBlank { destination.name }
            val target = if (destinationQuery.equals(destination.name, ignoreCase = true)) {
                // Unedited: use the refuge's own coordinates rather than
                // round-tripping its name through the geocoder.
                destination.toGeoPoint()
            } else {
                placeGeocoder.resolve(destinationQuery)
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
                contentPadding = PaddingValues(top = 250.dp)
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
                    .padding(top = 34.dp)
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

/** Both ends are free text; a blank origin means "start from my location". */
@Composable
private fun RoutePlannerCard(
    originInput: String,
    onOriginInputChange: (String) -> Unit,
    destinationInput: String,
    onDestinationInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
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
                placeholder = "My location",
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
                onSubmit = onSubmit
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSubmit,
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
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

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
            modifier = Modifier.weight(1f),
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
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(188.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        ImageBlock(refuge.imageLabel, Modifier.fillMaxSize())
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
            Text("Star ${refuge.rating}", color = Color(0xFFFFD166), fontSize = 12.sp)
        }
    }
}

@Composable
private fun RefugeListItem(refuge: Refuge, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImageBlock(
            label = refuge.imageLabel,
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
                Text("Star ${refuge.rating}", color = SenseInk, fontSize = 12.sp)
            }
            Text(refuge.subtitle, color = SenseMuted, fontSize = 12.sp)
            Text(refuge.sensoryTag, color = SenseBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

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
private fun ImageBlock(label: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(Color(0xFFB9D3F5), Color(0xFF7A94C5), Color(0xFF365486)))),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SenseBlue else ScreenBg)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (selected) Color.White else SenseMuted, fontSize = 12.sp)
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
