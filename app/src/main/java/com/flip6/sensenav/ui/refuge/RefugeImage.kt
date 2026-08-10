package com.flip6.sensenav.ui.refuge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flip6.sensenav.data.WIKIMEDIA_USER_AGENT
import com.flip6.sensenav.data.WikimediaImageRepository
import com.flip6.sensenav.model.LandmarkImage
import com.flip6.sensenav.model.Refuge

/**
 * Shared image lookup, so the on-device cache is reused by every screen and a
 * refuge already resolved on the home list costs nothing on the map.
 */
val LocalRefugeImages = staticCompositionLocalOf<WikimediaImageRepository> {
    error("No WikimediaImageRepository provided")
}

/**
 * Resolves this refuge's photo, or null while loading and when Wikimedia has
 * nothing that confidently matches it. Lookups are keyed and cached per refuge,
 * so scrolling a card out and back does not refetch.
 */
@Composable
fun rememberRefugeImage(refuge: Refuge): LandmarkImage? {
    val repository = LocalRefugeImages.current
    var image by remember(refuge.id) { mutableStateOf<LandmarkImage?>(null) }

    LaunchedEffect(refuge.id) {
        image = runCatching { repository.imageFor(refuge) }.getOrNull()
    }
    return image
}

/**
 * A refuge's picture: the Wikimedia photo when there is one, over category
 * artwork that shows through while it loads and stays put when there is none.
 */
@Composable
fun RefugeImage(
    refuge: Refuge,
    image: LandmarkImage?,
    modifier: Modifier = Modifier
) {
    val art = remember(refuge.id) {
        RefugeArt.from(refuge.category, refuge.subtitle, refuge.imageLabel, refuge.name)
    }

    Box(modifier = modifier) {
        RefugeArtwork(art = art, modifier = Modifier.fillMaxSize())

        if (image != null) {
            val context = LocalContext.current
            AsyncImage(
                // Coil has its own HTTP client, so the Wikimedia user agent has
                // to be set here too - without it the CDN answers 403 and the
                // card silently falls back to artwork.
                model = remember(image.url) {
                    ImageRequest.Builder(context)
                        .data(image.url)
                        .setHeader("User-Agent", WIKIMEDIA_USER_AGENT)
                        .crossfade(true)
                        .build()
                },
                // Named for screen readers - this app's users are the last people
                // who should meet an unlabelled image.
                contentDescription = "Photograph of ${refuge.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
