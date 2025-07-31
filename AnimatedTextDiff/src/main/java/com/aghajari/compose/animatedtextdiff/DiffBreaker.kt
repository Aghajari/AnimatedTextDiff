package com.aghajari.compose.animatedtextdiff

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import java.text.BreakIterator
import java.text.StringCharacterIterator
import java.util.Locale

/**
 * Interface for breaking text segments into smaller units for animation.
 */
interface DiffBreaker {

    /**
     * Breaks a text segment into a list of AnnotatedString units for animation.
     *
     * @param textSegment The text segment to break.
     * @param textLayout The layout result for the text.
     * @param startIndex The starting index of the segment in the text.
     * @return A list of pairs containing the broken AnnotatedString and its starting offset.
     */
    fun breakSegment(
        textSegment: AnnotatedString,
        textLayout: TextLayoutResult,
        startIndex: Int,
    ): List<AnnotatedString>
}

class DiffCharacterBreaker(
    locale: Locale = Locale.getDefault()
) : DiffBreaker {

    private val breakIterator = BreakIterator.getCharacterInstance(locale)

    override fun breakSegment(
        textSegment: AnnotatedString,
        textLayout: TextLayoutResult,
        startIndex: Int
    ): List<AnnotatedString> {
        breakIterator.text = StringCharacterIterator(textSegment.text)
        val out = mutableListOf<AnnotatedString>()

        var currentIndex = 0
        var nextIndex = breakIterator.next()
        while (nextIndex != BreakIterator.DONE) {
            out.add(textSegment.subSequence(currentIndex, nextIndex))
            currentIndex = nextIndex
            nextIndex = breakIterator.next()
        }

        return out
    }
}

object DiffWordBreaker : DiffBreaker {
    override fun breakSegment(
        textSegment: AnnotatedString,
        textLayout: TextLayoutResult,
        startIndex: Int
    ): List<AnnotatedString> {
        return listOf(textSegment)
    }
}