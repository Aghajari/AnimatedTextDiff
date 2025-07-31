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
private fun PreviewEnterTransition2() {
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