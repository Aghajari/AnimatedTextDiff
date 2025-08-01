package com.aghajari.compose.animatedtextdiff

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.ResolvedTextDirection
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
    insertionBreaker: DiffBreaker,
    deletionBreaker: DiffBreaker,
): DiffTextLayoutResult {
    var indexA = 0
    var indexB = 0
    var isFirstItem = canOptimizeFirstEqual(textLayoutA, textLayoutB)
    var startText: AnnotatedString? = null
    val dynamic = mutableListOf<TextBoundary>()
    val static = mutableListOf<MoveTextBoundary>()

    computeDiff(
        textA = textA.text,
        textB = textB.text,
        cleanupStrategy = cleanupStrategy,
    ) { operation, len ->
        when (operation) {
            DiffComputer.Operation.DELETE -> {
                newTextBoundary(textLayoutA, indexA, dynamic, textA, len, deletionBreaker, true)
                indexA += len
            }
            DiffComputer.Operation.INSERT -> {
                newTextBoundary(textLayoutB, indexB, dynamic, textB, len, insertionBreaker, false)
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
    action: (DiffComputer.Operation, Int) -> Unit,
) {
    val diffImpl = DiffComputer()
    diffImpl.diff_main(textA, textB).also {
        when (cleanupStrategy) {
            DiffCleanupStrategy.Semantic -> {
                diffImpl.diff_cleanupSemantic(it)
            }
            is DiffCleanupStrategy.Efficiency -> {
                diffImpl.diff_cleanupEfficiency(it, cleanupStrategy.editCost)
            }
            DiffCleanupStrategy.None -> {}
            is DiffCleanupStrategy.WordSemantic -> {
                if (cleanupStrategy.editCost < 0) {
                    diffImpl.diff_cleanupSemantic(it)
                } else if (cleanupStrategy.editCost > 0) {
                    diffImpl.diff_cleanupEfficiency(it, cleanupStrategy.editCost)
                }
                diffCleanupWord(it, cleanupStrategy.wordSplit)
            }
        }
    }.forEach {
        val len = it.text.length
        if (len > 0) {
            action.invoke(it.operation, len)
        }
    }
}

private fun diffCleanupWord(
    diffs: LinkedList<DiffComputer.Diff>,
    wordSplit: (Char) -> Boolean,
) {
    val iterator = diffs.listIterator()
    var prev: DiffComputer.Diff? = null

    while (iterator.hasNext()) {
        val current = iterator.next()

        if (current.operation == DiffComputer.Operation.DELETE ||
            current.operation == DiffComputer.Operation.INSERT
        ) {
            val currentOp = current.operation
            val currentText = current.text

            if (!iterator.hasNext()) {
                return
            }

            val next = iterator.next()
            if ((next.operation == DiffComputer.Operation.INSERT || next.operation == DiffComputer.Operation.DELETE) &&
                next.operation != currentOp &&
                prev?.operation == DiffComputer.Operation.EQUAL
            ) {
                val equalText = prev.text
                val deleteText =
                    if (currentOp == DiffComputer.Operation.DELETE) currentText else next.text
                val insertText =
                    if (currentOp == DiffComputer.Operation.INSERT) currentText else next.text

                val commonPrefix = equalText.takeLastWhile(wordSplit)

                val afterNext = if (iterator.hasNext()) {
                    iterator.next()
                } else {
                    null
                }

                val commonSuffix = afterNext
                    ?.takeIf { it.operation == DiffComputer.Operation.EQUAL }
                    ?.text?.takeWhile(wordSplit)
                    ?: ""

                prev.text = equalText.dropLast(commonPrefix.length)

                val newDelete = commonPrefix + deleteText + commonSuffix
                val newInsert = commonPrefix + insertText + commonSuffix

                if (currentOp == DiffComputer.Operation.DELETE) {
                    current.text = newDelete
                    next.text = newInsert
                } else {
                    current.text = newInsert
                    next.text = newDelete
                }

                if (commonSuffix.isNotEmpty() && afterNext != null) {
                    afterNext.text = afterNext.text.drop(commonSuffix.length)
                    if (afterNext.text.isEmpty()) {
                        iterator.remove()
                    } else {
                        iterator.previous()
                    }
                }

                iterator.previous()
                prev = current
                continue
            } else {
                iterator.previous()
            }
        }

        prev = current
    }
}

private fun newTextBoundary(
    textLayout: TextLayoutResult,
    index: Int,
    out: MutableList<TextBoundary>,
    text: AnnotatedString,
    len: Int,
    breaker: DiffBreaker,
    isExit: Boolean,
) {
    var consumed = 0
    while (consumed < len) {
        val word = textLayout.getWordBoundary(index + consumed)
        val startOffset = max(index + consumed, word.start)
        val endOffset = min(index + len, max(startOffset + 1, word.end))
        val str = text.subSequence(startOffset, endOffset)
        if (str.isBlank()) {
            consumed += max(1, endOffset - startOffset)
            continue
        }

        val segments = breaker.breakSegment(
            textSegment = str,
            textLayout = textLayout,
            startIndex = startOffset,
        )

        var segmentIndex = index + consumed
        var segmentStartOffset = startOffset
        segments.forEach { segment ->
            val line = textLayout.getLineForOffset(segmentIndex)
            val box = getBoundingBox(textLayout, segment, segmentIndex)
            out.add(
                TextBoundary(
                    range = TextRange(
                        start = segmentStartOffset,
                        end = segmentStartOffset + segment.length,
                    ),
                    text = segment,
                    left = box.left,
                    top = textLayout.getLineTop(line),
                    isExit = isExit,
                ),
            )
            segmentStartOffset += segment.length
            segmentIndex += segment.length
        }
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
        if (str.isBlank()) {
            consumed += max(1, endOffset - startOffset)
            continue
        }

        val fromLine = textLayoutA.getLineForOffset(indexA + consumed)
        val toLine = textLayoutB.getLineForOffset(indexB + consumed)
        val boxA = getBoundingBox(textLayoutA, str, indexA + consumed)
        val boxB = getBoundingBox(textLayoutB, str, indexB + consumed)

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

private fun canOptimizeFirstEqual(
    textLayoutA: TextLayoutResult,
    textLayoutB: TextLayoutResult,
): Boolean {
    return textLayoutA.getBidiRunDirection(0) == ResolvedTextDirection.Ltr &&
            textLayoutB.getBidiRunDirection(0) == ResolvedTextDirection.Ltr
}

private fun getBoundingBox(
    textLayout: TextLayoutResult,
    subtext: AnnotatedString,
    offset: Int,
): Rect {
    val dir = textLayout.getBidiRunDirection(offset)
    return if (dir == ResolvedTextDirection.Rtl) {
        textLayout.getBoundingBox(offset + subtext.length - 1)
    } else {
        textLayout.getBoundingBox(offset)
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
    val isRunning: Boolean
        get() = visibleState.isIdle.not()

    fun runAnimation() {
        visibleState.targetState = isExit.not()
    }
}

internal data class MoveTextBoundary(
    val range: TextRange,
    val text: AnnotatedString,
    val fromLeft: Float,
    val fromTop: Float,
    val toLeft: Float,
    val toTop: Float,
)