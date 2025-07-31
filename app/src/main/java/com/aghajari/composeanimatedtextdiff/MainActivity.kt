package com.aghajari.composeanimatedtextdiff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aghajari.compose.animatedtextdiff.AnimatedTextDiff
import com.aghajari.composeanimatedtextdiff.ui.theme.ComposeAnimatedTextDiffTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Screen()
        }
    }
}

@Composable
fun Screen() {
    ComposeAnimatedTextDiffTheme {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            val text = remember { mutableStateOf(makeText("World")) }
            AnimatedTextDiff(
                text = text.value,
                fontSize = 24.sp,
            )

            LaunchedEffect(Unit) {
                delay(1000)
                text.value = makeText("Amir")
            }
        }
    }
}

fun makeText(name: String) = buildAnnotatedString {
    append("\nHello, ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(name)
    }
    append("!")
    append("\nWelcome to AnimatedTextDiff.")
}

@Preview(
    showBackground = true,
    heightDp = 400
)
@Composable
fun Preview() {
    Screen()
}