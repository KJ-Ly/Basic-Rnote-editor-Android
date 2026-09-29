package io.github.kjly.brna.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kjly.brna.ui.theme.BrnaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Pictures are drawn one at a time: a page with an imported PDF on it is a big SVG to
 * draw, and several at once would only compete for memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val thumbnailDispatcher = Dispatchers.Default.limitedParallelism(1)

/** How far a page may be from square before its thumbnail is clamped, as in PageThumbnails. */
private const val MAX_ASPECT = 4f

/**
 * Every page of the note as a small picture; tapping one goes there. The pages are the
 * ones a page export would make, so for a note with an imported PDF they are the PDF's
 * pages, with the notes written beside them.
 */
@Composable
fun PageOverviewDialog(
    pages: List<Rect>,
    /** The page in view now, highlighted and scrolled to. */
    currentPage: Int?,
    /** Draws one page; called off the main thread. */
    renderThumbnail: (Rect) -> Bitmap?,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BrnaColors.PanelDialogSurface,
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (pages.size == 1) "1 page" else "${pages.size} pages",
                        color = BrnaColors.TextPrimaryOnPanel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close", color = BrnaColors.Accent) }
                }
                if (pages.isEmpty()) {
                    Text(
                        "This note is an infinite canvas: it has no pages. " +
                            "Choose a page size in Page Settings to split it into pages.",
                        color = BrnaColors.TextSecondaryOnPanel,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        state = rememberLazyGridState(initialFirstVisibleItemIndex = currentPage ?: 0),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        itemsIndexed(pages) { index, page ->
                            PageThumbnail(index, page, index == currentPage, renderThumbnail) {
                                onPageSelected(index)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail(
    index: Int,
    page: Rect,
    isCurrent: Boolean,
    renderThumbnail: (Rect) -> Bitmap?,
    onClick: () -> Unit
) {
    var image by remember(page) { mutableStateOf<ImageBitmap?>(null) }
    // Scrolled out of view before its turn, a page's render is cancelled with it.
    LaunchedEffect(page) {
        image = withContext(thumbnailDispatcher) { renderThumbnail(page) }?.asImageBitmap()
    }
    val w = page.width.coerceAtLeast(1f)
    val aspect = w / page.height.coerceAtLeast(1f).coerceAtMost(w * MAX_ASPECT)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .background(Color(0xFF2A2A33))
                .border(
                    width = if (isCurrent) 3.dp else 1.dp,
                    color = if (isCurrent) BrnaColors.Accent else Color.Gray
                )
        ) {
            val bitmap = image
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp))
            }
        }
        Text(
            "${index + 1}",
            color = if (isCurrent) BrnaColors.Accent else BrnaColors.TextSecondaryOnPanel,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
