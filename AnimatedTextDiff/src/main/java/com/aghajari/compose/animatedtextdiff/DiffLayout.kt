package com.aghajari.compose.animatedtextdiff

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

/**
 * Computes the diff between two texts and their layouts,
 * producing a result for animation.
 */
internal fun computeDiffTextLayout(
    textA: AnnotatedString,
    textB: AnnotatedString,
    textLayoutA: TextLayoutResult,
    textLayoutB: TextLayoutResult,
    cleanupStrategy: DiffCleanupStrategy,
): DiffTextLayoutResult {
    var indexA = 0
    var indexB = 0
    var isFirstItem = true
    var startText: AnnotatedString? = null
    val dynamic = mutableListOf<TextBoundary>()
    val static = mutableListOf<MoveTextBoundary>()

    computeDiff(
        textA = textA.text,
        textB = textB.text,
        cleanupStrategy = cleanupStrategy
    ).forEach {
        val len = it.text.length
        when (it.operation) {
            DiffComputer.Operation.DELETE -> {
                newTextBoundary(textLayoutA, indexA, dynamic, textA, len, true)
                indexA += len
            }
            DiffComputer.Operation.INSERT -> {
                newTextBoundary(textLayoutB, indexB, dynamic, textB, len, false)
                indexB += len
            }
            DiffComputer.Operation.EQUAL -> {
                if (isFirstItem) {
                    startText = textA.subSequence(0, len)
                } else {
                    newMoveTextBoundary(
                        textLayoutA = textLayoutA,
                        textLayoutB = textLayoutB,
                        indexA = indexA,
                        indexB = indexB,
                        len = len,
                        textA = textA,
                        out = static,
                    )
                }
                indexA += len
                indexB += len
            }
        }
        isFirstItem = false
    }

    return DiffTextLayoutResult(
        startText = startText,
        dynamic = dynamic,
        static = static,
    )
}

private fun computeDiff(
    textA: String,
    textB: String,
    cleanupStrategy: DiffCleanupStrategy,
): LinkedList<DiffComputer.Diff> {
    val diffImpl = DiffComputer()
    return diffImpl.diff_main(textA, textB).also {
        when (cleanupStrategy) {
            DiffCleanupStrategy.Semantic -> {
                diffImpl.diff_cleanupSemantic(it)
            }
            is DiffCleanupStrategy.Efficiency -> {
                diffImpl.diff_cleanupEfficiency(it, cleanupStrategy.editCost)
            }
            DiffCleanupStrategy.None -> {}
        }
    }
}

private fun newTextBoundary(
    textLayout: TextLayoutResult,
    index: Int,
    out: MutableList<TextBoundary>,
    text: AnnotatedString,
    len: Int,
    isExit: Boolean,
) {
    var consumed = 0
    while (consumed < len) {
        val word = textLayout.getWordBoundary(index + consumed)
        val startOffset = max(index + consumed, word.start)
        val endOffset = min(index + len, max(startOffset + 1, word.end))
        val str = text.subSequence(startOffset, endOffset)
        if (str.isEmpty()) {
            consumed += max(1, endOffset - startOffset)
            continue
        }

        val line = textLayout.getLineForOffset(index + consumed)
        val box = textLayout.getBoundingBox(index + consumed)
        out.add(
            TextBoundary(
                range = TextRange(start = startOffset, end = endOffset),
                text = str,
                left = box.left,
                top = textLayout.getLineTop(line),
                isExit = isExit,
            ),
        )
        consumed += endOffset - startOffset
    }
}

private fun newMoveTextBoundary(
    textLayoutA: TextLayoutResult,
    textLayoutB: TextLayoutResult,
    indexA: Int,
    indexB: Int,
    len: Int,
    textA: AnnotatedString,
    out: MutableList<MoveTextBoundary>,
) {
    var consumed = 0
    while (consumed < len) {
        val word = textLayoutA.getWordBoundary(indexA + consumed)
        val startOffset = max(indexA + consumed, word.start)
        val endOffset = min(indexA + len, max(startOffset + 1, word.end))
        val str = textA.subSequence(startOffset, endOffset)
        if (str.isEmpty()) {
            consumed += max(1, endOffset - startOffset)
            continue
        }

        val fromLine = textLayoutA.getLineForOffset(indexA + consumed)
        val toLine = textLayoutB.getLineForOffset(indexB + consumed)
        val boxA = textLayoutA.getBoundingBox(indexA + consumed)
        val boxB = textLayoutB.getBoundingBox(indexB + consumed)
        out.add(
            MoveTextBoundary(
                range = TextRange(start = startOffset, end = endOffset),
                text = str,
                fromLeft = boxA.left,
                fromTop = textLayoutA.getLineTop(fromLine),
                toLeft = boxB.left,
                toTop = textLayoutB.getLineTop(toLine),
            ),
        )
        consumed += endOffset - startOffset
    }
}

internal data class DiffTextLayoutResult(
    val startText: AnnotatedString?,
    val dynamic: List<TextBoundary>,
    val static: List<MoveTextBoundary>,
)

internal data class TextBoundary(
    val range: TextRange,
    val text: AnnotatedString,
    val left: Float,
    val top: Float,
    val isExit: Boolean,
) {
    val visibleState = MutableTransitionState(isExit)

    fun runAnimation() {
        visibleState.targetState = isExit.not()
    }
}

internal data class MoveTextBoundary(
    val range: TextRange,
    val text: AnnotatedString,
    val fromLeft: Float,
    val fromTop: Float,
    val toLeft: Float = fromLeft,
    val toTop: Float = fromTop,
)