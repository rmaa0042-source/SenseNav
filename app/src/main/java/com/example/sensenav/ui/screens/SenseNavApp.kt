package com.example.sensenav.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.example.sensenav.data.MockSenseNavRepository
import com.example.sensenav.model.Refuge
import com.example.sensenav.model.RouteOption
import com.example.sensenav.model.SearchResult
import com.example.sensenav.model.SearchResultType
import kotlinx.coroutines.delay

private val SenseBlue = Color(0xFF2F5FBD)
private val SenseSoftBlue = Color(0xFFEAF2FF)
private val SenseInk = Color(0xFF1F2633)
private val SenseMuted = Color(0xFF798293)
private val SensePink = Color(0xFFFF4F86)
private val SenseGreen = Color(0xFF1B9F5A)
private val ScreenBg = Color(0xFFF7F9FD)

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

    MaterialTheme {
        when (screen) {
            AppScreen.Splash -> SplashScreen(onFinished = { screen = AppScreen.Home })
            AppScreen.Home -> HomeScreen(
                repository = repository,
                onSearch = { screen = AppScreen.Search },
                onNearbyMap = { screen = AppScreen.NearbyMap },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.NearbyMap -> NearbyMapScreen(
                repository = repository,
                onBack = { screen = AppScreen.Home },
                onSearch = { screen = AppScreen.Search },
                onNavigate = { screen = AppScreen.Routes },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.Search -> SearchScreen(
                repository = repository,
                onBack = { screen = AppScreen.Home },
                onRouteSelected = { screen = AppScreen.Routes },
                onRefugeSelected = { screen = AppScreen.NearbyMap },
                onWarning = { screen = AppScreen.Warning }
            )
            AppScreen.Routes -> RouteOptionsScreen(
                repository = repository,
                onBack = { screen = AppScreen.Search },
                onWarning = { screen = AppScreen.Warning },
                onNavigate = { screen = AppScreen.NearbyMap }
            )
            AppScreen.Warning -> WarningScreen(
                repository = repository,
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
    repository: MockSenseNavRepository,
    onSearch: () -> Unit,
    onNearbyMap: () -> Unit,
    onWarning: () -> Unit
) {
    val refuges = repository.getRefuges()

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
    repository: MockSenseNavRepository,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onNavigate: () -> Unit,
    onWarning: () -> Unit
) {
    val refuge = repository.getRefuges().first()

    Box(modifier = Modifier.fillMaxSize()) {
        FakeMap(showRoute = false, modifier = Modifier.fillMaxSize())

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

        MapBubble("4.0", Modifier.align(Alignment.CenterStart).padding(start = 80.dp))
        MapBubble("4.7", Modifier.align(Alignment.Center).padding(top = 110.dp))
        MapBubble("4.5", Modifier.align(Alignment.CenterEnd).padding(end = 45.dp))

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
                RefugeListItem(refuge = refuge, onClick = {})
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigate,
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
    onBack: () -> Unit,
    onRouteSelected: () -> Unit,
    onRefugeSelected: () -> Unit,
    onWarning: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) repository.getRecentSearches() else repository.search(query)

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
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))
        SectionHeader(if (query.isBlank()) "Recent Searches" else "Search Results", "Clear All", null)

        Spacer(modifier = Modifier.height(6.dp))
        shown.forEach { result ->
            SearchResultRow(
                result = result,
                onClick = {
                    if (result.type == SearchResultType.Route || result.type == SearchResultType.Station) {
                        onRouteSelected()
                    } else {
                        onRefugeSelected()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionHeader("Recently Viewed", "See All", null)
        repository.getRefuges().take(3).forEach {
            RefugeListItem(refuge = it, onClick = onRefugeSelected)
        }
    }
}

@Composable
private fun RouteOptionsScreen(
    repository: MockSenseNavRepository,
    onBack: () -> Unit,
    onWarning: () -> Unit,
    onNavigate: () -> Unit
) {
    val routes = repository.getRoutes()

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            FakeMap(showRoute = true, modifier = Modifier.fillMaxSize())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 34.dp)
            ) {
                SearchPill(
                    text = "Flinders St -> State Library",
                    modifier = Modifier.weight(1f),
                    onClick = onBack
                )
            }
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
                routes.forEach { route ->
                    RouteCard(
                        route = route,
                        onDirections = {
                            if (route.isRecommended) onNavigate() else onWarning()
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun WarningScreen(
    repository: MockSenseNavRepository,
    onBack: () -> Unit,
    onReroute: () -> Unit
) {
    val warning = repository.getWarnings().first()

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            FakeMap(showRoute = true, modifier = Modifier.fillMaxSize())
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
private fun RouteCard(route: RouteOption, onDirections: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${route.title} (${route.sensoryRisk})",
            color = SenseInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("${route.rating} (${route.reviewCount})  Star Star Star Star", color = SenseMuted, fontSize = 13.sp)
        Text("Open - ${route.durationMinutes} min - ${route.roadName}", color = SenseMuted, fontSize = 13.sp)
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
private fun FakeMap(showRoute: Boolean, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFFF4F6F8))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val road = Color(0xFFE1E4EA)
            val park = Color(0xFFBFE8C8)

            drawRect(park, topLeft = Offset(size.width * 0.05f, size.height * 0.05f), size = androidx.compose.ui.geometry.Size(size.width * 0.30f, size.height * 0.16f))
            drawRect(park, topLeft = Offset(size.width * 0.70f, size.height * 0.12f), size = androidx.compose.ui.geometry.Size(size.width * 0.23f, size.height * 0.14f))
            drawRect(park, topLeft = Offset(size.width * 0.08f, size.height * 0.68f), size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.16f))

            for (i in 0..8) {
                val y = size.height * (0.10f + i * 0.10f)
                drawLine(road, Offset(0f, y), Offset(size.width, y + 50f), strokeWidth = 6f)
            }
            for (i in 0..7) {
                val x = size.width * (0.08f + i * 0.13f)
                drawLine(road, Offset(x, 0f), Offset(x - 80f, size.height), strokeWidth = 5f)
            }
            if (showRoute) {
                val route = Color(0xFF173B3F)
                val p1 = Offset(size.width * 0.35f, size.height * 0.28f)
                val p2 = Offset(size.width * 0.43f, size.height * 0.46f)
                val p3 = Offset(size.width * 0.33f, size.height * 0.60f)
                val p4 = Offset(size.width * 0.56f, size.height * 0.72f)
                drawLine(route, p1, p2, strokeWidth = 9f)
                drawLine(route, p2, p3, strokeWidth = 9f)
                drawLine(route, p3, p4, strokeWidth = 9f)
            }
        }
        if (showRoute) {
            Text("Flinders St", modifier = Modifier.padding(start = 118.dp, top = 145.dp), color = SensePink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("State Library", modifier = Modifier.padding(start = 205.dp, top = 286.dp), color = SensePink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
private fun MapBubble(text: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Star $text", color = SenseInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
