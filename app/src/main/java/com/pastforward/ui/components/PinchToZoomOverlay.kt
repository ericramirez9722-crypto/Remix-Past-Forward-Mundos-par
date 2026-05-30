package com.pastforward.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PinchToZoomOverlay(
    imageUrl: String,
    caption: String,
    styleKey: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Re-use and delegate to our robust, feature-rich FullScreenImageViewer which handles
    // dual-touch transform gestures, panning, double tap boundaries and file providers.
    FullScreenImageViewer(
        imageUrl = imageUrl,
        caption = caption,
        styleKey = styleKey,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
