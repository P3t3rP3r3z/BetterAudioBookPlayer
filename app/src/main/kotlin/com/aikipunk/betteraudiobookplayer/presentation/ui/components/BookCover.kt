package com.aikipunk.betteraudiobookplayer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aikipunk.betteraudiobookplayer.domain.model.Audiobook

/**
 * A book's cover art, or a generated title card when there is none (or it fails to load).
 * Decorative: callers describe the book themselves.
 */
@Composable
fun BookCover(
    audiobook: Audiobook,
    modifier: Modifier = Modifier,
) {
    val path = audiobook.coverArtPath
    var failed by remember(path) { mutableStateOf(false) }
    if (path == null || failed) {
        GeneratedCover(title = audiobook.displayTitle, author = audiobook.author, modifier = modifier)
    } else {
        AsyncImage(
            model = path,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier,
        )
    }
}

/** A gradient card with the title and author; the colours are stable per title. */
@Composable
fun GeneratedCover(
    title: String,
    author: String?,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = GENERATED_PALETTE[Math.floorMod(title.hashCode(), GENERATED_PALETTE.size)]
    val density = LocalDensity.current
    // Text scales with the cover; it's skipped for the single frame before the size is known
    var width by remember { mutableStateOf<Dp?>(null) }
    Box(
        modifier = modifier
            .background(Brush.linearGradient(listOf(Color(top), Color(bottom))))
            .onSizeChanged { width = with(density) { it.width.toDp() } }
            .clearAndSetSemantics { },
    ) {
        width?.let { GeneratedCoverText(title = title, author = author, width = it) }
    }
}

@Composable
private fun GeneratedCoverText(title: String, author: String?, width: Dp) {
    val scale = width.value
    val compact = scale < 100
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding((scale * 0.09f).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(
                modifier = Modifier
                    .width((scale * 0.18f).dp)
                    .height(if (compact) 2.dp else 3.dp)
                    .background(GENERATED_ACCENT),
            )
            Spacer(modifier = Modifier.height((scale * 0.06f).dp))
            Text(
                text = title,
                color = GENERATED_TEXT,
                fontWeight = FontWeight.Bold,
                fontSize = (scale * 0.1f).coerceIn(9f, 26f).sp,
                lineHeight = (scale * 0.12f).coerceIn(11f, 30f).sp,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!author.isNullOrBlank() && !compact) {
            Text(
                text = author,
                color = GENERATED_TEXT.copy(alpha = 0.8f),
                fontSize = (scale * 0.065f).coerceIn(9f, 16f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Dark enough that the light text stays above 4.5:1 contrast on every stop
private val GENERATED_PALETTE = listOf(
    0xFF8C3B2E to 0xFF3E1A14,
    0xFF2E5E6E to 0xFF132A33,
    0xFF5B4B8A to 0xFF241D3B,
    0xFF3F6B45 to 0xFF18301C,
    0xFF7A5A22 to 0xFF3B2C10,
    0xFF6E2E4F to 0xFF2D1220,
    0xFF34495E to 0xFF141D26,
)
private val GENERATED_TEXT = Color(0xFFF7F1EC)
private val GENERATED_ACCENT = Color(0xFFF3B464)

@Preview(widthDp = 160, heightDp = 160)
@Composable
private fun PreviewGeneratedCover() {
    GeneratedCover(title = "The Hobbit, or There and Back Again", author = "J.R.R. Tolkien", modifier = Modifier.fillMaxSize())
}
