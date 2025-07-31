package com.aghajari.compose.animatedtextdiff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Displays text with animated transitions for changes, using a diff algorithm to animate
 * insertions, deletions, and movements of text segments.
 *
 * @param text the text to be displayed
 * @param modifier the [Modifier] to be applied to this layout node
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set,
 *   this will be [LocalContentColor].
 * @param fontSize the size of glyphs to use when painting the text. See [TextStyle.fontSize].
 * @param fontStyle the typeface variant to use when drawing the letters (e.g., italic). See
 *   [TextStyle.fontStyle].
 * @param fontWeight the typeface thickness to use when painting the text (e.g., [FontWeight.Bold]).
 * @param fontFamily the font family to be used when rendering the text. See [TextStyle.fontFamily].
 * @param letterSpacing the amount of space to add between each letter. See
 *   [TextStyle.letterSpacing].
 * @param textDecoration the decorations to paint on the text (e.g., an underline). See
 *   [TextStyle.textDecoration].
 * @param textAlign the alignment of the text within the lines of the paragraph. See
 *   [TextStyle.textAlign].
 * @param lineHeight line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM. See
 *   [TextStyle.lineHeight].
 * @param overflow how visual overflow should be handled.
 * @param softWrap whether the text should break at soft line breaks. If false, the glyphs in the
 *   text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 *   [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *   If the text exceeds the given number of lines, it will be truncated according to [overflow] and
 *   [softWrap]. It is required that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 *   that 1 <= [minLines] <= [maxLines].
 * @param inlineContent a map storing composables that replaces certain ranges of the text, used to
 *   insert composables into text layout. See [InlineTextContent].
 * @param onTextLayout callback that is executed when a new text layout is calculated. A
 *   [TextLayoutResult] object that callback provides contains paragraph information, size of the
 *   text, baselines and other details. The callback can be used to add additional decoration or
 *   functionality to the text. For example, to draw selection around the text.
 * @param style style configuration for the text such as color, font, line height etc.
 * @param diffCleanupStrategy Strategy for cleaning up text diffs.
 * @param diffCoroutineContext Coroutine context for computing the diff.
 * @param diffInsertionBreaker Strategy for breaking inserted text into smaller units for animation.
 * @param diffDeletionBreaker Strategy for breaking deleted text into smaller units for animation.
 * @param onAnimationStart callback that is executed when text changes.
 * @param onAnimationEnd callback that is executed when text transformed.
 * @param enter Animation for text insertion.
 * @param exit Animation for text deletion.
 * @param move AnimationSpec for text movement.
 * @see Text
 */
@Composable
fun AnimatedTextDiff(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current.copy(
        textMotion = TextMotion.Animated,
    ),
    diffCleanupStrategy: DiffCleanupStrategy = DiffCleanupStrategy.Efficiency(),
    diffCoroutineContext: CoroutineContext? = null,
    diffInsertionBreaker: DiffBreaker = DiffWordBreaker,
    diffDeletionBreaker: DiffBreaker = DiffWordBreaker,
    onAnimationStart: (() -> Unit)? = null,
    onAnimationEnd: (() -> Unit)? = null,
    enter: (String, TextRange) -> EnterTransition = { _, _ ->
        fadeIn() + slideInVertically(
            initialOffsetY = { -it },
        )
    },
    exit: (String, TextRange) -> ExitTransition = { _, _ ->
        fadeOut() + slideOutVertically(
            targetOffsetY = { it },
        )
    },
    move: FiniteAnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
    ),
) {
    AnimatedTextDiff(
        text = AnnotatedString(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style,
        diffCleanupStrategy = diffCleanupStrategy,
        diffCoroutineContext = diffCoroutineContext,
        diffInsertionBreaker = diffInsertionBreaker,
        diffDeletionBreaker = diffDeletionBreaker,
        onAnimationStart = onAnimationStart,
        onAnimationEnd = onAnimationEnd,
        enter = { text, range -> enter.invoke(text.text, range) },
        exit = { text, range -> exit.invoke(text.text, range) },
        move = move,
    )
}

/**
 * Displays text with animated transitions for changes, using a diff algorithm to animate
 * insertions, deletions, and movements of text segments.
 *
 * @param text the text to be displayed
 * @param modifier the [Modifier] to be applied to this layout node
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set,
 *   this will be [LocalContentColor].
 * @param fontSize the size of glyphs to use when painting the text. See [TextStyle.fontSize].
 * @param fontStyle the typeface variant to use when drawing the letters (e.g., italic). See
 *   [TextStyle.fontStyle].
 * @param fontWeight the typeface thickness to use when painting the text (e.g., [FontWeight.Bold]).
 * @param fontFamily the font family to be used when rendering the text. See [TextStyle.fontFamily].
 * @param letterSpacing the amount of space to add between each letter. See
 *   [TextStyle.letterSpacing].
 * @param textDecoration the decorations to paint on the text (e.g., an underline). See
 *   [TextStyle.textDecoration].
 * @param textAlign the alignment of the text within the lines of the paragraph. See
 *   [TextStyle.textAlign].
 * @param lineHeight line height for the [Paragraph] in [TextUnit] unit, e.g. SP or EM. See
 *   [TextStyle.lineHeight].
 * @param overflow how visual overflow should be handled.
 * @param softWrap whether the text should break at soft line breaks. If false, the glyphs in the
 *   text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 *   [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *   If the text exceeds the given number of lines, it will be truncated according to [overflow] and
 *   [softWrap]. It is required that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 *   that 1 <= [minLines] <= [maxLines].
 * @param inlineContent a map storing composables that replaces certain ranges of the text, used to
 *   insert composables into text layout. See [InlineTextContent].
 * @param onTextLayout callback that is executed when a new text layout is calculated. A
 *   [TextLayoutResult] object that callback provides contains paragraph information, size of the
 *   text, baselines and other details. The callback can be used to add additional decoration or
 *   functionality to the text. For example, to draw selection around the text.
 * @param style style configuration for the text such as color, font, line height etc.
 * @param diffCleanupStrategy Strategy for cleaning up text diffs.
 * @param diffCoroutineContext Coroutine context for computing the diff.
 * @param diffInsertionBreaker Strategy for breaking inserted text into smaller units for animation.
 * @param diffDeletionBreaker Strategy for breaking deleted text into smaller units for animation.
 * @param onAnimationStart callback that is executed when text changes.
 * @param onAnimationEnd callback that is executed when text transformed.
 * @param enter Animation for text insertion.
 * @param exit Animation for text deletion.
 * @param move AnimationSpec for text movement.
 * @see Text
 */
@Composable
fun AnimatedTextDiff(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current.copy(
        textMotion = TextMotion.Animated,
    ),
    diffCleanupStrategy: DiffCleanupStrategy = DiffCleanupStrategy.Efficiency(),
    diffCoroutineContext: CoroutineContext? = null,
    diffInsertionBreaker: DiffBreaker = DiffWordBreaker,
    diffDeletionBreaker: DiffBreaker = DiffWordBreaker,
    onAnimationStart: (() -> Unit)? = null,
    onAnimationEnd: (() -> Unit)? = null,
    enter: (AnnotatedString, TextRange) -> EnterTransition = { _, _ ->
        fadeIn() + slideInVertically(
            initialOffsetY = { -it },
        )
    },
    exit: (AnnotatedString, TextRange) -> ExitTransition = { _, _ ->
        fadeOut() + slideOutVertically(
            targetOffsetY = { it },
        )
    },
    move: FiniteAnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
    ),
) {
    val currentText = remember { mutableStateOf(text) }
    val newText = remember { mutableStateOf<AnnotatedString?>(null) }

    val diffLayout = remember { mutableStateOf<DiffTextLayoutResult?>(null) }
    val textLayoutA = remember { mutableStateOf<TextLayoutResult?>(null) }
    val textLayoutB = remember { mutableStateOf<TextLayoutResult?>(null) }

    @Composable
    fun StyledText(
        text: AnnotatedString,
        modifier: Modifier = Modifier,
        onTextLayout: (TextLayoutResult) -> Unit = {},
    ) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = inlineContent,
            onTextLayout = onTextLayout,
            style = style,
        )
    }

    Box(modifier = modifier.animateContentSize()) {
        LaunchedEffect(text) {
            if (text != currentText.value || newText.value != null) {
                newText.value?.let {
                    currentText.value = it
                    textLayoutA.value = null
                }
                diffLayout.value = null
                textLayoutB.value = null
                newText.value = text
                onAnimationStart?.invoke()
            }
        }

        if (textLayoutA.value == null ||
            textLayoutB.value == null ||
            diffLayout.value == null
        ) {
            StyledText(text = currentText.value) {
                textLayoutA.value = it
                if (newText.value == null) {
                    onTextLayout?.invoke(it)
                }
            }
        }

        newText.value?.let { textB ->
            StyledText(
                text = textB,
                modifier = Modifier.alpha(0f),
            ) {
                textLayoutB.value = it
                onTextLayout?.invoke(it)
            }

            ComputeDiffLayout(
                diffLayout = diffLayout,
                textLayoutA = textLayoutA.value,
                textLayoutB = textLayoutB.value,
                currentText = currentText.value,
                newText = textB,
                diffCoroutineContext = diffCoroutineContext,
                diffCleanupStrategy = diffCleanupStrategy,
                diffInsertionBreaker = diffInsertionBreaker,
                diffDeletionBreaker = diffDeletionBreaker,
            )

            if (diffLayout.value != null) {
                TextDiffAnimatedContent(
                    diff = requireNotNull(diffLayout.value),
                    enter = enter,
                    exit = exit,
                    move = move,
                    onEnd = {
                        if (newText.value == textB) {
                            diffLayout.value = null
                            currentText.value = textB
                            newText.value = null
                            textLayoutA.value = textLayoutB.value
                            textLayoutB.value = null
                            onAnimationEnd?.invoke()
                        }
                    },
                ) { text, textModifier ->
                    StyledText(
                        text = text,
                        modifier = textModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComputeDiffLayout(
    diffLayout: MutableState<DiffTextLayoutResult?>,
    textLayoutA: TextLayoutResult?,
    textLayoutB: TextLayoutResult?,
    currentText: AnnotatedString,
    newText: AnnotatedString,
    diffCoroutineContext: CoroutineContext?,
    diffCleanupStrategy: DiffCleanupStrategy,
    diffInsertionBreaker: DiffBreaker,
    diffDeletionBreaker: DiffBreaker,
) {
    if (diffLayout.value == null &&
        textLayoutA != null &&
        textLayoutB != null
    ) {
        if (diffCoroutineContext == null) {
            diffLayout.value = remember(currentText, newText) {
                computeDiffTextLayout(
                    textA = currentText,
                    textB = newText,
                    textLayoutA = textLayoutA,
                    textLayoutB = textLayoutB,
                    cleanupStrategy = diffCleanupStrategy,
                    insertionBreaker = diffInsertionBreaker,
                    deletionBreaker = diffDeletionBreaker,
                )
            }
        } else {
            LaunchedEffect(currentText, newText) {
                diffLayout.value = withContext(diffCoroutineContext) {
                    computeDiffTextLayout(
                        textA = currentText,
                        textB = newText,
                        textLayoutA = textLayoutA,
                        textLayoutB = textLayoutB,
                        cleanupStrategy = diffCleanupStrategy,
                        insertionBreaker = diffInsertionBreaker,
                        deletionBreaker = diffDeletionBreaker,
                    )
                }
            }
        }
    }
}

/**
 * Animates text changes based on the provided diff result.
 */
@Composable
private fun TextDiffAnimatedContent(
    diff: DiffTextLayoutResult,
    enter: (AnnotatedString, TextRange) -> EnterTransition,
    exit: (AnnotatedString, TextRange) -> ExitTransition,
    move: FiniteAnimationSpec<Float>,
    onEnd: () -> Unit,
    content: @Composable (AnnotatedString, Modifier) -> Unit,
) {
    val moveAnimation = remember {
        diff.static.takeIf { it.isNotEmpty() }?.let { Animatable(0f) }
    }
    val moveFraction = moveAnimation?.value ?: 1f

    diff.startText?.let { content(it, Modifier) }
    diff.static.fastForEach { boundary ->
        content(
            boundary.text,
            Modifier.graphicsLayer(
                translationX = boundary.fromLeft + (boundary.toLeft - boundary.fromLeft) * moveFraction,
                translationY = boundary.fromTop + (boundary.toTop - boundary.fromTop) * moveFraction,
            ),
        )
    }
    diff.dynamic.fastForEach { boundary ->
        AnimatedVisibility(
            visibleState = boundary.visibleState,
            enter = if (boundary.isExit.not()) {
                enter.invoke(boundary.text, boundary.range)
            } else {
                fadeIn()
            },
            exit = if (boundary.isExit) {
                exit.invoke(boundary.text, boundary.range)
            } else {
                fadeOut()
            },
            modifier = Modifier.graphicsLayer(
                translationX = boundary.left,
                translationY = boundary.top,
            ),
        ) { content(boundary.text, Modifier) }
    }

    LaunchedEffect(diff) {
        diff.dynamic.fastForEach {
            it.runAnimation()
        }
        moveAnimation?.animateTo(
            targetValue = 1f,
            animationSpec = move,
        )
    }

    val isRunning by remember {
        derivedStateOf {
            moveAnimation?.isRunning == true ||
                    diff.dynamic.any { it.isRunning }
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning.not()) {
            onEnd.invoke()
        }
    }
}