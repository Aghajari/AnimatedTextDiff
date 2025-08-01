# AnimatedTextDiff

A Jetpack Compose library for animating text changes with smooth transitions, powered by the [diff-match-patch](https://github.com/google/diff-match-patch). `AnimatedTextDiff` computes differences between two text states and applies animations to insertions, deletions, and movements, creating a visually engaging experience for text updates in your app.

<img width=500 src="./demo/test.gif"/>

<img width=500 src="./demo/test2.gif"/>

<img width=500 src="./demo/test3.gif"/>

## Features
- **Word-Level Diffing**: Animates text changes at the word level for precise and smooth transitions.
- **Customizable Animations**: Supports custom animations for insertions, deletions, and movements using Compose's animation APIs.
- **Threading Options**: Compute diffs on the main thread or in the background for large texts.
- **Cleanup Strategies**: Choose between `None`, `Semantic`, or `Efficiency` cleanup for diff optimization.
- **Rich Text Support**: Works with `AnnotatedString` for styled text, including bold, italic, and colors.

## Usage
### Simple HelloWorld Example

Below is a basic example demonstrating how to use `AnimatedTextDiff` to animate text changes when clicking a text area:

```kotlin
@Composable
fun HelloWorldDemo() {
    val textMaker: (String) -> AnnotatedString = { name ->
        buildAnnotatedString {
            append("Hello ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(name)
            }
            append("!\nWelcome to AnimatedTextDiff.")
        }
    }

    val text = remember { mutableStateOf(textMaker("World")) }
    AnimatedTextDiff(
        text = text.value,
        modifier = Modifier.clickable {
            text.value = textMaker("Amir")
        },
    )
}
```

This example animates the transition from "Hello World!" to "Hello Amir!", with "World" fading out and "Amir" fading in, while the rest of the text remains stable.

## Diff Cleanup Strategy
The `diffCleanupStrategy` controls how the diff algorithm optimizes changes:

- **`None`**: No cleanup, resulting in fine-grained diffs. For example, changing "World" to "Amir" might:
  - Move the character 'r' (shared between both).
  - Delete "Wo" and "ld".
  - Insert "Ami".
- **`Semantic`**: Removes entire words for cleaner diffs. For "World" to "Amir":
  - Deletes the whole word "World".
  - Inserts the whole word "Amir".
- **`Efficiency`**: Balances semantic cleanup with performance, reducing trivial edits.
- **`WordSemantic`**:  Performs cleanup at the **word level**, ideal for preserving layout and visual clarity, especially in RTL languages. For example, changing "Exit" to "Test":
  - Instead of showing a partial replacement like "Exi" with "Tes", it replaces the whole word "Exit" with "Test".
  - Helps prevent layout issues in right-aligned or bidirectional text.

## Customizing Animations
You can customize the animations for insertions, deletions, and movements:

- **Insertion (`enter`)**: Default is `fadeIn() + slideInVertically`. Customize with any `EnterTransition`.
- **Deletion (`exit`)**: Default is `fadeOut() + slideOutVertically`. Customize with any `ExitTransition`.
- **Movement (`move`)**: Default is a spring animation with medium-low stiffness. Use any `FiniteAnimationSpec`.

Example with custom animations:
```kotlin
AnimatedTextDiff(
    text = text.value,
    enter = { textToAnimate, range ->
        scaleIn() + fadeIn(animationSpec = tween(500))
    },
    exit = { textToAnimate, range ->
        scaleOut() + fadeOut(animationSpec = tween(500))
    },
    move = spring(stiffness = Spring.StiffnessHigh),
)
```

## DiffBreaker
`DiffBreaker` allows you to customize how inserted and deleted text segments are split into smaller units for animation in `AnimatedTextDiff`. The minimum unit for diffing is word-by-word, meaning movement animations (for unchanged text) are always applied at the word level. However, you can use `diffInsertionBreaker` and `diffDeletionBreaker` to control the granularity of insertion and deletion animations, such as animating character-by-character for insertions or word-by-word for deletions.

The following example demonstrates animating text changes with `DiffCharacterBreaker` for insertions (e.g., animating "World" as "W", "o", "r", "l", "d"):

Word-By-Word breaker:

<img width=500 src="./demo/test4.gif"/>

Character-By-Character breaker:

<img width=500 src="./demo/test5.gif"/>

```kotlin
@Preview
@Composable
fun CharacterByCharacterSequenceDemo() {
    val text = remember { mutableStateOf("Hello, ") }
    var charIndex = 0

    AnimatedTextDiff(
        modifier = Modifier.fillMaxWidth(),
        text = text.value,
        diffInsertionBreaker = DiffCharacterBreaker(),
        onAnimationStart = { charIndex = 0 },
        enter = { textToAnimate, range ->
            val animationSpec = tween<Float>(
                durationMillis = 500,
                delayMillis = charIndex++ * 100,
            )
            fadeIn(animationSpec) + scaleIn(animationSpec)
        },
    )

    LaunchedEffect(Unit) {
        delay(1000)
        text.value = "Hello, How are you?"
    }
}
```

You can create custom `DiffBreaker` implementations to split text differently, such as by pairs of characters or syllables.
