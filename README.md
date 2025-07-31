# AnimatedTextDiff

A Jetpack Compose library for animating text changes with smooth transitions, powered by the [diff-match-patch](https://github.com/google/diff-match-patch). `AnimatedTextDiff` computes differences between two text states and applies animations to insertions, deletions, and movements, creating a visually engaging experience for text updates in your app.

<img width=500 src="./demo/test.gif"/>

<img width=500 src="./demo/test2.gif"/>

<img width=500 src="./demo/test3.gif"/>

<img width=500 src="./demo/test4.gif"/>

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

- **`Cleanup.None`**: No cleanup, resulting in fine-grained diffs. For example, changing "World" to "Amir" might:
  - Move the character 'r' (shared between both).
  - Delete "Wo" and "ld".
  - Insert "Ami".
- **`Cleanup.Semantic`**: Removes entire words for cleaner diffs. For "World" to "Amir":
  - Deletes the whole word "World".
  - Inserts the whole word "Amir".
- **`Cleanup.Efficiency`**: Balances semantic cleanup with performance, reducing trivial edits.

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
