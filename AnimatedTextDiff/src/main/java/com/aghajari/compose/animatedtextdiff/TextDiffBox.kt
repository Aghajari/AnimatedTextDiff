package com.aghajari.compose.animatedtextdiff

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box

/**
 * A custom layout similar to [Box], but adds internal vertical padding **inside**
 * the layout's bounds to prevent content clipping during animations.
 *
 * This is particularly useful when using entrance and exit animations such as
 * [slideInVertically] or [slideOutVertically], which may extend beyond the layout bounds.
 * Unlike modifier-based padding which shrinks the content space, this layout expands
 * the measured height and offsets the content accordingly—so animated content isn't clipped.
 *
 * The content is aligned according to [contentAlignment], and vertical space is increased
 * by [topPadding] and [bottomPadding], preserving proper layout behavior.
 *
 * @param topPadding Additional space added to the top of the layout (inside).
 * @param bottomPadding Additional space added to the bottom of the layout (inside).
 * @param modifier Modifier to be applied to the layout.
 * @param contentAlignment The alignment of the content inside the layout.
 * @param content The content to be displayed inside this layout.
 */
@Composable
internal fun TextDiffBox(
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    if (topPadding == 0.dp && bottomPadding == 0.dp) {
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment,
        ) {
            content.invoke()
        }
    } else {
        Layout(
            modifier = modifier,
            content = content,
        ) { measurables, constraints ->
            val placeables = measurables.map { measurable ->
                measurable.measure(constraints)
            }
            val width = placeables.maxOfOrNull { it.width } ?: 0
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val topPx = topPadding.roundToPx()
            val bottomPx = bottomPadding.roundToPx()

            layout(width, height + topPx + bottomPx) {
                placeables.forEach { placeable ->
                    val position = contentAlignment.align(
                        size = IntSize(placeable.width, placeable.height),
                        space = IntSize(width, height),
                        layoutDirection = layoutDirection,
                    )
                    placeable.placeRelative(position.x, position.y + topPx)
                }
            }
        }
    }
}