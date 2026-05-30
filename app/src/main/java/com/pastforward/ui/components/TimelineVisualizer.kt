package com.pastforward.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.metrics.performance.PerformanceMetricsState
import com.pastforward.ui.screens.GeneratedImage

@Composable
fun TimelineVisualizer(
    activeStyles: List<String>,
    generatedImages: Map<String, GeneratedImage>,
    cardRotations: List<Float>,
    onRetry: (String) -> Unit,
    onCardClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val view = LocalView.current

    // Set up real-time performance tracking with JankStats during horizontal timeline scroll interactions
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
            val stateHolder = PerformanceMetricsState.getHolderForHierarchy(view)
            if (isScrolling) {
                stateHolder.state?.putState("TimelineVisualizer", "Scrolling")
            } else {
                stateHolder.state?.removeState("TimelineVisualizer")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label/Hint indicator for the film strip reel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DEVELOPING DECADES & DIMENSIONS",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "◄ SWIPE TIMELINE ►",
                color = Color(0xFFFACC15), // Gold indicator
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Horizontal LazyScroll Film Reel strip avoiding raw memory leaks
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(activeStyles) { index, styleName ->
                val rotationVal = cardRotations[index % cardRotations.size]
                val imgState = generatedImages[styleName]

                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PolaroidCard(
                        styleKey = styleName,
                        caption = "$styleName Self-Portrait",
                        status = imgState?.status ?: ImageStatus.PENDING,
                        imageUrl = imgState?.url,
                        error = imgState?.error,
                        rotation = rotationVal,
                        onRetry = { onRetry(styleName) },
                        onCardClick = {
                            imgState?.url?.let { url ->
                                onCardClick(url, "$styleName Self-Portrait", styleName)
                            }
                        }
                    )
                }
            }
        }
    }
}
