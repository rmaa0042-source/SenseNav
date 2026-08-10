package com.flip6.sensenav.ui.refuge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The drawn stand-in for a refuge with no photograph.
 *
 * Deliberately illustrative rather than photographic: it says "a church" without
 * pretending to be a picture of *this* church, which is the honest thing to show
 * when Wikimedia has nothing we can confidently attach to this landmark.
 */
enum class RefugeArt {
    Worship,
    Park,
    Library,
    Museum,
    Water,
    Generic;

    companion object {
        /**
         * Picks artwork from the dataset's own theme and sub-theme wording -
         * "Place of Worship", "Leisure/Recreation", "Library" and so on.
         */
        fun from(vararg descriptors: String?): RefugeArt {
            val text = descriptors.filterNotNull().joinToString(" ").lowercase()
            return when {
                listOf("worship", "church", "cathedral", "chapel", "synagogue", "mosque", "temple")
                    .any { it in text } -> Worship
                listOf("park", "garden", "recreation", "leisure", "reserve", "green")
                    .any { it in text } -> Park
                listOf("library", "book", "study", "education", "school", "university")
                    .any { it in text } -> Library
                listOf("museum", "gallery", "art", "heritage", "assembly", "memorial", "theatre")
                    .any { it in text } -> Museum
                listOf("river", "bridge", "wharf", "water", "bay", "dock")
                    .any { it in text } -> Water
                else -> Generic
            }
        }
    }
}

private val ArtBackground = listOf(Color(0xFFB9D3F5), Color(0xFF7A94C5), Color(0xFF365486))
private val ArtInk = Color(0xFFF2F6FD)
private val ArtInkSoft = Color(0x66FFFFFF)

@Composable
fun RefugeArtwork(art: RefugeArt, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Brush.linearGradient(ArtBackground))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Everything is drawn against the shorter edge so one illustration
            // reads correctly in both the tall card and the small square thumb.
            val unit = minOf(size.width, size.height)
            val centre = Offset(size.width / 2f, size.height / 2f)
            when (art) {
                RefugeArt.Worship -> drawWorship(centre, unit)
                RefugeArt.Park -> drawPark(centre, unit)
                RefugeArt.Library -> drawLibrary(centre, unit)
                RefugeArt.Museum -> drawMuseum(centre, unit)
                RefugeArt.Water -> drawWater(centre, unit)
                RefugeArt.Generic -> drawGeneric(centre, unit)
            }
        }
    }
}

private fun DrawScope.drawWorship(centre: Offset, unit: Float) {
    val bodyWidth = unit * 0.40f
    val bodyHeight = unit * 0.34f
    val bodyTop = centre.y + unit * 0.02f - bodyHeight / 2f

    // Spire.
    val roof = Path().apply {
        moveTo(centre.x, centre.y - unit * 0.40f)
        lineTo(centre.x + bodyWidth / 2f, bodyTop)
        lineTo(centre.x - bodyWidth / 2f, bodyTop)
        close()
    }
    drawPath(roof, ArtInk)
    drawRect(
        color = ArtInk,
        topLeft = Offset(centre.x - bodyWidth / 2f, bodyTop),
        size = Size(bodyWidth, bodyHeight)
    )

    // Cross above the spire, and an arched door below it.
    val crossArm = unit * 0.055f
    drawRect(
        color = ArtInk,
        topLeft = Offset(centre.x - unit * 0.014f, centre.y - unit * 0.56f),
        size = Size(unit * 0.028f, unit * 0.14f)
    )
    drawRect(
        color = ArtInk,
        topLeft = Offset(centre.x - crossArm, centre.y - unit * 0.515f),
        size = Size(crossArm * 2f, unit * 0.028f)
    )
    drawCircle(
        color = ArtBackground.last(),
        radius = unit * 0.055f,
        center = Offset(centre.x, bodyTop + bodyHeight - unit * 0.075f)
    )
}

private fun DrawScope.drawPark(centre: Offset, unit: Float) {
    // Canopy as three overlapping circles, over a short trunk.
    drawCircle(ArtInk, unit * 0.15f, Offset(centre.x, centre.y - unit * 0.20f))
    drawCircle(ArtInk, unit * 0.12f, Offset(centre.x - unit * 0.17f, centre.y - unit * 0.08f))
    drawCircle(ArtInk, unit * 0.12f, Offset(centre.x + unit * 0.17f, centre.y - unit * 0.08f))
    drawRect(
        color = ArtInk,
        topLeft = Offset(centre.x - unit * 0.028f, centre.y - unit * 0.02f),
        size = Size(unit * 0.056f, unit * 0.30f)
    )
    drawRect(
        color = ArtInkSoft,
        topLeft = Offset(centre.x - unit * 0.34f, centre.y + unit * 0.28f),
        size = Size(unit * 0.68f, unit * 0.035f)
    )
}

private fun DrawScope.drawLibrary(centre: Offset, unit: Float) {
    val height = unit * 0.30f
    val half = unit * 0.21f

    // An open book: two leaves meeting at a spine.
    listOf(-1f, 1f).forEach { side ->
        val path = Path().apply {
            moveTo(centre.x, centre.y - height / 2f)
            lineTo(centre.x + side * half, centre.y - height / 2f + unit * 0.05f)
            lineTo(centre.x + side * half, centre.y + height / 2f)
            lineTo(centre.x, centre.y + height / 2f - unit * 0.045f)
            close()
        }
        drawPath(path, ArtInk)
    }
    drawRect(
        color = ArtBackground.last(),
        topLeft = Offset(centre.x - unit * 0.012f, centre.y - height / 2f),
        size = Size(unit * 0.024f, height)
    )
}

private fun DrawScope.drawMuseum(centre: Offset, unit: Float) {
    val width = unit * 0.46f
    val pediment = Path().apply {
        moveTo(centre.x, centre.y - unit * 0.30f)
        lineTo(centre.x + width / 2f, centre.y - unit * 0.16f)
        lineTo(centre.x - width / 2f, centre.y - unit * 0.16f)
        close()
    }
    drawPath(pediment, ArtInk)

    // Three columns on a plinth.
    val columnWidth = unit * 0.055f
    listOf(-1f, 0f, 1f).forEach { slot ->
        drawRect(
            color = ArtInk,
            topLeft = Offset(centre.x + slot * unit * 0.145f - columnWidth / 2f, centre.y - unit * 0.13f),
            size = Size(columnWidth, unit * 0.28f)
        )
    }
    drawRect(
        color = ArtInk,
        topLeft = Offset(centre.x - width / 2f, centre.y + unit * 0.16f),
        size = Size(width, unit * 0.045f)
    )
}

private fun DrawScope.drawWater(centre: Offset, unit: Float) {
    // Three stacked ripples.
    listOf(-1f, 0f, 1f).forEachIndexed { index, row ->
        val y = centre.y + row * unit * 0.14f
        val path = Path().apply {
            moveTo(centre.x - unit * 0.30f, y)
            cubicTo(
                centre.x - unit * 0.15f, y - unit * 0.09f,
                centre.x + unit * 0.15f, y + unit * 0.09f,
                centre.x + unit * 0.30f, y
            )
        }
        drawPath(
            path = path,
            color = if (index == 1) ArtInk else ArtInkSoft,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = unit * 0.045f)
        )
    }
}

private fun DrawScope.drawGeneric(centre: Offset, unit: Float) {
    // A map pin, matching the marker language used on the map screen.
    val radius = unit * 0.16f
    val top = Offset(centre.x, centre.y - unit * 0.10f)
    drawCircle(ArtInk, radius, top)
    val tail = Path().apply {
        moveTo(centre.x - radius * 0.72f, top.y + radius * 0.70f)
        lineTo(centre.x, centre.y + unit * 0.30f)
        lineTo(centre.x + radius * 0.72f, top.y + radius * 0.70f)
        close()
    }
    drawPath(tail, ArtInk)
    drawCircle(ArtBackground.last(), radius * 0.42f, top)
}
