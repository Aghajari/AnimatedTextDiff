package com.aghajari.compose.animatedtextdiff

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
private fun PreviewBox(
    modifier: Modifier = Modifier,
    initialText: AnnotatedString = AnnotatedString(""),
    content: @Composable (MutableState<AnnotatedString>) -> Unit,
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .background(Color.White)
            .padding(16.dp)
    ) {
        val text = remember { mutableStateOf(initialText) }
        content.invoke(text)
    }
}

private fun makeText(
    start: String = "Hello ",
    bold: String = "",
    end: String = "!\nWelcome to AnimatedTextDiff",
): AnnotatedString = buildAnnotatedString {
    append(start)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(bold)
    }
    append(end)
}

@Preview
@Composable
private fun PreviewHelloWorld() {
    PreviewBox(initialText = makeText(bold = "World")) { text ->
        AnimatedTextDiff(text = text.value)

        LaunchedEffect(Unit) {
            delay(1000)
            text.value = makeText(bold = "Amir")
            delay(1000)
            text.value = makeText(bold = "World")
        }
    }
}

@Preview
@Composable
private fun PreviewScaleAnimation() {
    PreviewBox(initialText = makeText(bold = "World")) { text ->
        AnimatedTextDiff(
            text = text.value,
            enter = { textToAnimate, range ->
                scaleIn() + fadeIn(animationSpec = tween(500))
            },
            exit = { textToAnimate, range ->
                scaleOut() + fadeOut(animationSpec = tween(500))
            },
        )

        LaunchedEffect(Unit) {
            delay(1000)
            text.value = makeText(bold = "Amir")
            delay(1000)
            text.value = makeText(bold = "World")
        }
    }
}

@Preview
@Composable
private fun PreviewEnterTransition() {
    val initialText = makeText(
        start = "Hello< ",
        end = " > Test",
    )
    PreviewBox(initialText = initialText) { text ->
        AnimatedTextDiff(
            text = text.value,
            enter = { textToAnimate, range ->
                fadeIn(
                    animationSpec = tween<Float>(
                        durationMillis = 500,
                        delayMillis = 400,
                    ),
                )
            },
            minLines = 2,
        )

        LaunchedEffect(Unit) {
            delay(1000)
            text.value = makeText(
                start = "Hello< ",
                bold = "World",
                end = " > Test",
            )
            delay(1200)
            text.value = makeText(
                start = "Hello< ",
                bold = "World",
                end = " This\nLine 2 > Test",
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSequence() {
    val initialText = AnnotatedString("Hello, ")

    PreviewBox(initialText = initialText) { text ->
        var wordIndex = remember { mutableIntStateOf(0) }

        AnimatedTextDiff(
            text = text.value,
            onAnimationStart = {
                wordIndex.intValue = 0
            },
            enter = { textToAnimate, range ->
                val animationSpec = tween<Float>(
                    durationMillis = 500,
                    delayMillis = wordIndex.intValue++ * 100,
                )
                fadeIn(animationSpec) + scaleIn(animationSpec)
            },
            exit = { textToAnimate, range ->
                val animationSpec = tween<Float>(
                    durationMillis = 500,
                    delayMillis = 1000,
                )
                fadeOut(animationSpec) + scaleOut(animationSpec)
            },
        )

        LaunchedEffect(Unit) {
            delay(1000)
            text.value = AnnotatedString("Hello, How are you‌?")
            delay(2000)
            text.value = initialText
        }
    }
}

@Preview
@Composable
private fun PreviewCharacterByCharacterSequence() {
    val initialText = AnnotatedString("Hello, ")

    PreviewBox(initialText = initialText) { text ->
        var charIndex = remember { mutableIntStateOf(0) }

        AnimatedTextDiff(
            text = text.value,
            diffInsertionBreaker = DiffCharacterBreaker(),
            onAnimationStart = {
                charIndex.intValue = 0
            },
            enter = { textToAnimate, range ->
                val animationSpec = tween<Float>(
                    durationMillis = 500,
                    delayMillis = charIndex.intValue++ * 100,
                )
                fadeIn(animationSpec) + scaleIn(animationSpec)
            },
            exit = { textToAnimate, range ->
                val animationSpec = tween<Float>(
                    durationMillis = 500,
                    delayMillis = 1000,
                )
                fadeOut(animationSpec) + scaleOut(animationSpec)
            },
        )

        LaunchedEffect(Unit) {
            delay(1000)
            text.value = AnnotatedString("Hello, How are you‌?")
            delay(3000)
            text.value = initialText
        }
    }
}

@Preview
@Composable
private fun PreviewCounter() {
    val initialText = AnnotatedString("Hello, ")

    PreviewBox(initialText = initialText) { text ->
        var counter = remember { mutableIntStateOf(1) }

        AnimatedTextDiff(
            text = "\n${counter.intValue}\n",
            diffCleanupStrategy = DiffCleanupStrategy.None,
        )

        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                counter.intValue++
            }
        }
    }
}