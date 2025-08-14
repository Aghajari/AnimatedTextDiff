/*
 * Diff Match and Patch
 * Copyright 2018 The diff-match-patch Authors.
 * https://github.com/google/diff-match-patch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aghajari.compose.animatedtextdiff

import java.util.*
import java.util.regex.Pattern

/**
 * Functions for diff.
 * Computes the difference between two texts.
 * <a href="https://github.com/google/diff-match-patch">Source</a>
 *
 * @author fraser@google.com (Neil Fraser)
 */
internal class DiffComputer {

    /**
     * Number of seconds to map a diff before giving up (0 for infinity).
     */
    var diffTimeout: Float = 1.0f

    /**
     * Internal class for returning results from diff_linesToChars().
     * Other less paranoid languages just use a three-element array.
     */
    private data class LinesToCharsResult(
        val chars1: String,
        val chars2: String,
        val lineArray: List<String>
    )

    //  DIFF FUNCTIONS

    /**
     * The data structure representing a diff is a Linked list of Diff objects:
     * {Diff(Operation.DELETE, "Hello"), Diff(Operation.INSERT, "Goodbye"),
     * Diff(Operation.EQUAL, " world.")}
     * which means: delete "Hello", add "Goodbye" and keep " world."
     */
    enum class Operation {
        DELETE, INSERT, EQUAL
    }

    /**
     * Find the differences between two texts.
     * Run a faster, slightly less optimal diff.
     * This method allows the 'checklines' of diff_main() to be optional.
     * Most of the time checklines is wanted, so default to true.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @return Linked List of Diff objects.
     */
    fun diffMain(text1: String, text2: String): LinkedList<Diff> {
        return diffMain(text1, text2, true)
    }

    /**
     * Find the differences between two texts.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     *                   line-level diff first to identify the changed areas.
     *                   If true, then run a faster slightly less optimal diff.
     * @return Linked List of Diff objects.
     */
    fun diffMain(text1: String, text2: String, checklines: Boolean): LinkedList<Diff> {
        // Set a deadline by which time the diff must be complete.
        val deadline: Long = if (diffTimeout <= 0) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() + (diffTimeout * 1000).toLong()
        }
        return diffMain(text1, text2, checklines, deadline)
    }

    /**
     * Find the differences between two texts.  Simplifies the problem by
     * stripping any common prefix or suffix off the texts before diffing.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     *                   line-level diff first to identify the changed areas.
     *                   If true, then run a faster slightly less optimal diff.
     * @param deadline   Time when the diff should be complete by.  Used
     *                   internally for recursive calls.  Users should set DiffTimeout instead.
     * @return Linked List of Diff objects.
     */
    private fun diffMain(
        text1: String,
        text2: String,
        checklines: Boolean,
        deadline: Long
    ): LinkedList<Diff> {
        // Check for null inputs.
        if (text1.isEmpty() && text2.isEmpty()) { // Adjusted for equality check below.
            throw IllegalArgumentException("Null inputs. (diff_main)")
        }

        // Check for equality (speedup).
        var diffs: LinkedList<Diff>
        if (text1 == text2) {
            diffs = LinkedList()
            if (text1.isNotEmpty()) {
                diffs.add(Diff(Operation.EQUAL, text1))
            }
            return diffs
        }

        // Trim off common prefix (speedup).
        var commonlength = diffCommonPrefix(text1, text2)
        val commonprefix = text1.substring(0, commonlength)
        var updatedText1 = text1.substring(commonlength)
        var updatedText2 = text2.substring(commonlength)

        // Trim off common suffix (speedup).
        commonlength = diffCommonSuffix(updatedText1, updatedText2)
        val commonsuffix = updatedText1.substring(updatedText1.length - commonlength)
        updatedText1 = updatedText1.substring(0, updatedText1.length - commonlength)
        updatedText2 = updatedText2.substring(0, updatedText2.length - commonlength)

        // Compute the diff on the middle block.
        diffs = diffCompute(updatedText1, updatedText2, checklines, deadline)

        // Restore the prefix and suffix.
        if (commonprefix.isNotEmpty()) {
            diffs.addFirst(Diff(Operation.EQUAL, commonprefix))
        }
        if (commonsuffix.isNotEmpty()) {
            diffs.addLast(Diff(Operation.EQUAL, commonsuffix))
        }

        diffCleanupMerge(diffs)
        return diffs
    }

    /**
     * Find the differences between two texts.  Assumes that the texts do not
     * have any common prefix or suffix.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     *                   line-level diff first to identify the changed areas.
     *                   If true, then run a faster slightly less optimal diff.
     * @param deadline   Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private fun diffCompute(
        text1: String,
        text2: String,
        checklines: Boolean,
        deadline: Long
    ): LinkedList<Diff> {
        val diffs = LinkedList<Diff>()

        if (text1.isEmpty()) {
            // Just add some text (speedup).
            diffs.add(Diff(Operation.INSERT, text2))
            return diffs
        }

        if (text2.isEmpty()) {
            // Just delete some text (speedup).
            diffs.add(Diff(Operation.DELETE, text1))
            return diffs
        }

        val longtext = if (text1.length > text2.length) text1 else text2
        val shorttext = if (text1.length > text2.length) text2 else text1
        val i = longtext.indexOf(shorttext)
        if (i != -1) {
            // Shorter text is inside the longer text (speedup).
            val op = if (text1.length > text2.length) Operation.DELETE else Operation.INSERT
            diffs.add(Diff(op, longtext.substring(0, i)))
            diffs.add(Diff(Operation.EQUAL, shorttext))
            diffs.add(Diff(op, longtext.substring(i + shorttext.length)))
            return diffs
        }

        if (shorttext.length == 1) {
            // Single character string.
            // After the previous speedup, the character can't be an equality.
            diffs.add(Diff(Operation.DELETE, text1))
            diffs.add(Diff(Operation.INSERT, text2))
            return diffs
        }

        // Check to see if the problem can be split in two.
        val hm = diffHalfMatch(text1, text2)
        if (hm != null) {
            // A half-match was found, sort out the return data.
            val text1A = hm[0]
            val text1B = hm[1]
            val text2A = hm[2]
            val text2B = hm[3]
            val midCommon = hm[4]
            // Send both pairs off for separate processing.
            val diffsA = diffMain(text1A, text2A, checklines, deadline)
            val diffsB = diffMain(text1B, text2B, checklines, deadline)
            // Merge the results.
            diffs.addAll(diffsA)
            diffs.add(Diff(Operation.EQUAL, midCommon))
            diffs.addAll(diffsB)
            return diffs
        }

        return if (checklines && text1.length > 100 && text2.length > 100) {
            diffLineMode(text1, text2, deadline)
        } else {
            diffBisect(text1, text2, deadline)
        }
    }

    /**
     * Do a quick line-level diff on both strings, then rediff the parts for
     * greater accuracy.
     * This speedup can produce non-minimal diffs.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param deadline Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private fun diffLineMode(text1: String, text2: String, deadline: Long): LinkedList<Diff> {
        // Scan the text on a line-by-line basis first.
        val a = diffLinesToChars(text1, text2)
        var updatedText1 = a.chars1
        var updatedText2 = a.chars2
        val linearray = a.lineArray

        var diffs = diffMain(updatedText1, updatedText2, false, deadline)

        // Convert the diff back to original text.
        diffCharsToLines(diffs, linearray)
        // Eliminate freak matches (e.g. blank lines)
        diffCleanupSemantic(diffs)

        // Rediff any replacement blocks, this time character-by-character.
        // Add a dummy entry at the end.
        diffs.add(Diff(Operation.EQUAL, ""))
        var countDelete = 0
        var countInsert = 0
        var textDelete = ""
        var textInsert = ""
        val pointer = diffs.listIterator()
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        while (thisDiff != null) {
            when (thisDiff.operation) {
                Operation.INSERT -> {
                    countInsert++
                    textInsert += thisDiff.text
                }
                Operation.DELETE -> {
                    countDelete++
                    textDelete += thisDiff.text
                }
                Operation.EQUAL -> {
                    // Upon reaching an equality, check for prior redundancies.
                    if (countDelete >= 1 && countInsert >= 1) {
                        // Delete the offending records and add the merged ones.
                        pointer.previous()
                        repeat(countDelete + countInsert) {
                            pointer.previous()
                            pointer.remove()
                        }
                        val subDiffs = diffMain(textDelete, textInsert, false, deadline)
                        for (subDiff in subDiffs) {
                            pointer.add(subDiff)
                        }
                    }
                    countInsert = 0
                    countDelete = 0
                    textDelete = ""
                    textInsert = ""
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
        diffs.removeLast()  // Remove the dummy entry at the end.

        return diffs
    }

    /**
     * Find the 'middle snake' of a diff, split the problem in two
     * and return the recursively constructed diff.
     * See Myers 1986 paper: An O(ND) Difference Algorithm and Its Variations.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    private fun diffBisect(text1: String, text2: String, deadline: Long): LinkedList<Diff> {
        // Cache the text lengths to prevent multiple calls.
        val text1Length = text1.length
        val text2Length = text2.length
        val maxD = (text1Length + text2Length + 1) / 2
        val vOffset = maxD
        val vLength = 2 * maxD
        val v1 = IntArray(vLength) { -1 }
        val v2 = IntArray(vLength) { -1 }
        v1[vOffset + 1] = 0
        v2[vOffset + 1] = 0
        val delta = text1Length - text2Length
        // If the total number of characters is odd, then the front path will
        // collide with the reverse path.
        val front = (delta % 2 != 0)
        // Offsets for start and end of k loop.
        // Prevents mapping of space beyond the grid.
        var k1start = 0
        var k1end = 0
        var k2start = 0
        var k2end = 0
        for (d in 0 until maxD) {
            // Bail out if deadline is reached.
            if (System.currentTimeMillis() > deadline) {
                break
            }

            // Walk the front path one step.
            var k1 = -d + k1start
            while (k1 <= d - k1end) {
                val k1Offset = vOffset + k1
                var x1 = if (k1 == -d || (k1 != d && v1[k1Offset - 1] < v1[k1Offset + 1])) {
                    v1[k1Offset + 1]
                } else {
                    v1[k1Offset - 1] + 1
                }
                var y1 = x1 - k1
                while (x1 < text1Length && y1 < text2Length && text1[x1] == text2[y1]) {
                    x1++
                    y1++
                }
                v1[k1Offset] = x1
                if (x1 > text1Length) {
                    // Ran off the right of the graph.
                    k1end += 2
                } else if (y1 > text2Length) {
                    // Ran off the bottom of the graph.
                    k1start += 2
                } else if (front) {
                    val k2Offset = vOffset + delta - k1
                    if (k2Offset >= 0 && k2Offset < vLength && v2[k2Offset] != -1) {
                        // Mirror x2 onto top-left coordinate system.
                        val x2 = text1Length - v2[k2Offset]
                        if (x1 >= x2) {
                            // Overlap detected.
                            return diffBisectSplit(text1, text2, x1, y1, deadline)
                        }
                    }
                }
                k1 += 2
            }

            // Walk the reverse path one step.
            var k2 = -d + k2start
            while (k2 <= d - k2end) {
                val k2Offset = vOffset + k2
                var x2 = if (k2 == -d || (k2 != d && v2[k2Offset - 1] < v2[k2Offset + 1])) {
                    v2[k2Offset + 1]
                } else {
                    v2[k2Offset - 1] + 1
                }
                var y2 = x2 - k2
                while (x2 < text1Length && y2 < text2Length &&
                    text1[text1Length - x2 - 1] == text2[text2Length - y2 - 1]
                ) {
                    x2++
                    y2++
                }
                v2[k2Offset] = x2
                if (x2 > text1Length) {
                    // Ran off the left of the graph.
                    k2end += 2
                } else if (y2 > text2Length) {
                    // Ran off the top of the graph.
                    k2start += 2
                } else if (!front) {
                    val k1Offset = vOffset + delta - k2
                    if (k1Offset >= 0 && k1Offset < vLength && v1[k1Offset] != -1) {
                        val x1 = v1[k1Offset]
                        val y1 = vOffset + x1 - k1Offset
                        // Mirror x2 onto top-left coordinate system.
                        x2 = text1Length - x2
                        if (x1 >= x2) {
                            // Overlap detected.
                            return diffBisectSplit(text1, text2, x1, y1, deadline)
                        }
                    }
                }
                k2 += 2
            }
        }
        // Diff took too long and hit the deadline or
        // number of diffs equals number of characters, no commonality at all.
        val diffs = LinkedList<Diff>()
        diffs.add(Diff(Operation.DELETE, text1))
        diffs.add(Diff(Operation.INSERT, text2))
        return diffs
    }

    /**
     * Given the location of the 'middle snake', split the diff in two parts
     * and recurse.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param x        Index of split point in text1.
     * @param y        Index of split point in text2.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    private fun diffBisectSplit(
        text1: String,
        text2: String,
        x: Int,
        y: Int,
        deadline: Long
    ): LinkedList<Diff> {
        val text1a = text1.substring(0, x)
        val text2a = text2.substring(0, y)
        val text1b = text1.substring(x)
        val text2b = text2.substring(y)

        // Compute both diffs serially.
        val diffs = diffMain(text1a, text2a, false, deadline)
        val diffsb = diffMain(text1b, text2b, false, deadline)

        diffs.addAll(diffsb)
        return diffs
    }

    /**
     * Split two texts into a list of strings.  Reduce the texts to a string of
     * hashes where each Unicode character represents one line.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return An object containing the encoded text1, the encoded text2 and
     * the List of unique strings.  The zeroth element of the List of
     * unique strings is intentionally blank.
     */
    private fun diffLinesToChars(text1: String, text2: String): LinesToCharsResult {
        val lineArray: MutableList<String> = ArrayList()
        val lineHash: MutableMap<String, Int> = HashMap()
        // e.g. linearray[4] == "Hello\n"
        // e.g. linehash.get("Hello\n") == 4

        // "\x00" is a valid character, but various debuggers don't like it.
        // So we'll insert a junk entry to avoid generating a null character.
        lineArray.add("")

        // Allocate 2/3rds of the space for text1, the rest for text2.
        val chars1 = diffLinesToCharsMunge(text1, lineArray, lineHash, 40000)
        val chars2 = diffLinesToCharsMunge(text2, lineArray, lineHash, 65535)
        return LinesToCharsResult(chars1, chars2, lineArray)
    }

    /**
     * Split a text into a list of strings.  Reduce the texts to a string of
     * hashes where each Unicode character represents one line.
     *
     * @param text      String to encode.
     * @param lineArray List of unique strings.
     * @param lineHash  Map of strings to indices.
     * @param maxLines  Maximum length of lineArray.
     * @return Encoded string.
     */
    private fun diffLinesToCharsMunge(
        text: String,
        lineArray: MutableList<String>,
        lineHash: MutableMap<String, Int>,
        maxLines: Int
    ): String {
        var lineStart = 0
        var lineEnd = -1
        var line: String
        val chars = StringBuilder()
        // Walk the text, pulling out a substring for each line.
        // text.split('\n') would would temporarily double our memory footprint.
        // Modifying text would create many large strings to garbage collect.
        while (lineEnd < text.length - 1) {
            lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd == -1) {
                lineEnd = text.length - 1
            }
            line = text.substring(lineStart, lineEnd + 1)

            if (lineHash.containsKey(line)) {
                chars.append(lineHash[line]!!.toChar())
            } else {
                if (lineArray.size == maxLines) {
                    // Bail out at 65535 because
                    // char 65536 would be 0 in Java.
                    line = text.substring(lineStart)
                    lineEnd = text.length
                }
                lineArray.add(line)
                lineHash[line] = lineArray.size - 1
                chars.append((lineArray.size - 1).toChar())
            }
            lineStart = lineEnd + 1
        }
        return chars.toString()
    }

    /**
     * Rehydrate the text in a diff from a string of line hashes to real lines of
     * text.
     *
     * @param diffs     List of Diff objects.
     * @param lineArray List of unique strings.
     */
    private fun diffCharsToLines(diffs: List<Diff>, lineArray: List<String>) {
        val text = StringBuilder()
        for (diff in diffs) {
            text.clear()
            for (j in diff.text.indices) {
                text.append(lineArray[diff.text[j].code])
            }
            diff.text = text.toString()
        }
    }

    /**
     * Determine the common prefix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the start of each string.
     */
    fun diffCommonPrefix(text1: String, text2: String): Int {
        // Performance analysis: https://neil.fraser.name/news/2007/10/09/
        val n = minOf(text1.length, text2.length)
        for (i in 0 until n) {
            if (text1[i] != text2[i]) {
                return i
            }
        }
        return n
    }

    /**
     * Determine the common suffix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of each string.
     */
    fun diffCommonSuffix(text1: String, text2: String): Int {
        // Performance analysis: https://neil.fraser.name/news/2007/10/09/
        val text1Length = text1.length
        val text2Length = text2.length
        val n = minOf(text1Length, text2Length)
        for (i in 1..n) {
            if (text1[text1Length - i] != text2[text2Length - i]) {
                return i - 1
            }
        }
        return n
    }

    /**
     * Determine if the suffix of one string is the prefix of another.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of the first
     * string and the start of the second string.
     */
    private fun diffCommonOverlap(text1: String, text2: String): Int {
        // Cache the text lengths to prevent multiple calls.
        var text1Length = text1.length
        var text2Length = text2.length
        // Eliminate the null case.
        if (text1Length == 0 || text2Length == 0) {
            return 0
        }
        // Truncate the longer string.
        var updatedText1 = text1
        var updatedText2 = text2
        if (text1Length > text2Length) {
            updatedText1 = text1.substring(text1Length - text2Length)
        } else if (text1Length < text2Length) {
            updatedText2 = text2.substring(0, text1Length)
        }
        text1Length = updatedText1.length
        text2Length = updatedText2.length
        // Quick check for the worst case.
        if (updatedText1 == updatedText2) {
            return minOf(text1Length, text2Length)
        }

        // Start by looking for a single character match
        // and increase length until no match is found.
        // Performance analysis: https://neil.fraser.name/news/2010/11/04/
        var best = 0
        var length = 1
        while (true) {
            val pattern = updatedText1.substring(text1Length - length)
            val found = updatedText2.indexOf(pattern)
            if (found == -1) {
                return best
            }
            length += found
            if (found == 0 || updatedText1.substring(text1Length - length) == updatedText2.substring(
                    0,
                    length
                )
            ) {
                best = length
                length++
            }
        }
    }

    /**
     * Do the two texts share a substring which is at least half the length of
     * the longer text?
     * This speedup can produce non-minimal diffs.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return Five element String array, containing the prefix of text1, the
     * suffix of text1, the prefix of text2, the suffix of text2 and the
     * common middle.  Or null if there was no match.
     */
    private fun diffHalfMatch(text1: String, text2: String): Array<String>? {
        if (diffTimeout <= 0) {
            // Don't risk returning a non-optimal diff if we have unlimited time.
            return null
        }
        val longtext = if (text1.length > text2.length) text1 else text2
        val shorttext = if (text1.length > text2.length) text2 else text1
        if (longtext.length < 4 || shorttext.length * 2 < longtext.length) {
            return null // Pointless.
        }

        // First check if the second quarter is the seed for a half-match.
        val hm1 = diffHalfMatchI(longtext, shorttext, (longtext.length + 3) / 4)
        // Check again based on the third quarter.
        val hm2 = diffHalfMatchI(longtext, shorttext, (longtext.length + 1) / 2)
        val hm: Array<String>? = when {
            hm1 == null && hm2 == null -> return null
            hm2 == null -> hm1
            hm1 == null -> hm2
            else -> // Both matched.  Select the longest.
                if (hm1[4].length > hm2[4].length) hm1 else hm2
        }

        // A half-match was found, sort out the return data.
        return if (text1.length > text2.length) {
            hm
        } else {
            arrayOf(
                hm?.get(2).orEmpty(),
                hm?.get(3).orEmpty(),
                hm?.get(0).orEmpty(),
                hm?.get(1).orEmpty(),
                hm?.get(4).orEmpty(),
            )
        }
    }

    /**
     * Does a substring of shorttext exist within longtext such that the
     * substring is at least half the length of longtext?
     *
     * @param longtext  Longer string.
     * @param shorttext Shorter string.
     * @param i         Start index of quarter length substring within longtext.
     * @return Five element String array, containing the prefix of longtext, the
     * suffix of longtext, the prefix of shorttext, the suffix of shorttext
     * and the common middle.  Or null if there was no match.
     */
    private fun diffHalfMatchI(longtext: String, shorttext: String, i: Int): Array<String>? {
        // Start with a 1/4 length substring at position i as a seed.
        val seed = longtext.substring(i, i + longtext.length / 4)
        var j = -1
        var bestCommon = ""
        var bestLongtextA = ""
        var bestLongtextB = ""
        var bestShorttextA = ""
        var bestShorttextB = ""
        while (shorttext.indexOf(seed, j + 1).also { j = it } != -1) {
            val prefixLength = diffCommonPrefix(longtext.substring(i), shorttext.substring(j))
            val suffixLength = diffCommonSuffix(longtext.substring(0, i), shorttext.substring(0, j))
            if (bestCommon.length < suffixLength + prefixLength) {
                bestCommon = shorttext.substring(j - suffixLength, j) + shorttext.substring(
                    j,
                    j + prefixLength
                )
                bestLongtextA = longtext.substring(0, i - suffixLength)
                bestLongtextB = longtext.substring(i + prefixLength)
                bestShorttextA = shorttext.substring(0, j - suffixLength)
                bestShorttextB = shorttext.substring(j + prefixLength)
            }
        }
        return if (bestCommon.length * 2 >= longtext.length) {
            arrayOf(bestLongtextA, bestLongtextB, bestShorttextA, bestShorttextB, bestCommon)
        } else {
            null
        }
    }

    /**
     * Reduce the number of edits by eliminating semantically trivial equalities.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupSemantic(diffs: LinkedList<Diff>) {
        if (diffs.isEmpty()) {
            return
        }
        var changes = false
        val equalities: Deque<Diff> = ArrayDeque() // Double-ended queue of qualities.
        var lastEquality: String? = null // Always equal to equalities.peek().text
        val pointer = diffs.listIterator()
        // Number of characters that changed prior to the equality.
        var lengthInsertions1 = 0
        var lengthDeletions1 = 0
        // Number of characters that changed after the equality.
        var lengthInsertions2 = 0
        var lengthDeletions2 = 0
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // Equality found.
                equalities.push(thisDiff)
                lengthInsertions1 = lengthInsertions2
                lengthDeletions1 = lengthDeletions2
                lengthInsertions2 = 0
                lengthDeletions2 = 0
                lastEquality = thisDiff.text
            } else {
                // An insertion or deletion.
                if (thisDiff.operation == Operation.INSERT) {
                    lengthInsertions2 += thisDiff.text.length
                } else {
                    lengthDeletions2 += thisDiff.text.length
                }
                // Eliminate an equality that is smaller or equal to the edits on both
                // sides of it.
                if (lastEquality != null && (lastEquality.length <= maxOf(
                        lengthInsertions1,
                        lengthDeletions1
                    )) &&
                    (lastEquality.length <= maxOf(lengthInsertions2, lengthDeletions2))
                ) {
                    // Walk back to offending equality.
                    while (thisDiff != equalities.peek()) {
                        thisDiff = pointer.previous()
                    }
                    pointer.next()

                    // Replace equality with a delete.
                    pointer.set(Diff(Operation.DELETE, lastEquality))
                    // Insert a corresponding an insert.
                    pointer.add(Diff(Operation.INSERT, lastEquality))

                    equalities.pop() // Throw away the equality we just deleted.
                    if (!equalities.isEmpty()) {
                        // Throw away the previous equality (it needs to be reevaluated).
                        equalities.pop()
                    }
                    if (equalities.isEmpty()) {
                        // There are no previous equalities, walk back to the start.
                        while (pointer.hasPrevious()) {
                            pointer.previous()
                        }
                    } else {
                        // There is a safe equality we can fall back to.
                        thisDiff = equalities.peek()
                        while (thisDiff != pointer.previous()) {
                            // Intentionally empty loop.
                        }
                    }

                    lengthInsertions1 = 0 // Reset the counters.
                    lengthInsertions2 = 0
                    lengthDeletions1 = 0
                    lengthDeletions2 = 0
                    lastEquality = null
                    changes = true
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }

        // Normalize the diff.
        if (changes) {
            diffCleanupMerge(diffs)
        }
        diffCleanupSemanticLossless(diffs)

        // Find any overlaps between deletions and insertions.
        // e.g: <del>abcxxx</del><ins>xxxdef</ins>
        //   -> <del>abc</del>xxx<ins>def</ins>
        // e.g: <del>xxxabc</del><ins>defxxx</ins>
        //   -> <ins>def</ins>xxx<del>abc</del>
        // Only extract an overlap if it is as big as the edit ahead or behind it.
        val pointer2 = diffs.listIterator()
        var prevDiff: Diff? = null
        var thisDiff2: Diff? = null
        if (pointer2.hasNext()) {
            prevDiff = pointer2.next()
            if (pointer2.hasNext()) {
                thisDiff2 = pointer2.next()
            }
        }
        while (thisDiff2 != null) {
            if (prevDiff!!.operation == Operation.DELETE && thisDiff2.operation == Operation.INSERT) {
                val deletion = prevDiff.text
                val insertion = thisDiff2.text
                val overlapLength1 = diffCommonOverlap(deletion, insertion)
                val overlapLength2 = diffCommonOverlap(insertion, deletion)
                if (overlapLength1 >= overlapLength2) {
                    if (overlapLength1 >= deletion.length / 2.0 || overlapLength1 >= insertion.length / 2.0) {
                        // Overlap found. Insert an equality and trim the surrounding edits.
                        pointer2.previous()
                        pointer2.add(Diff(Operation.EQUAL, insertion.substring(0, overlapLength1)))
                        prevDiff.text = deletion.substring(0, deletion.length - overlapLength1)
                        thisDiff2.text = insertion.substring(overlapLength1)
                        // pointer.add inserts the element before the cursor, so there is
                        // no need to step past the new element.
                    }
                } else {
                    if (overlapLength2 >= deletion.length / 2.0 || overlapLength2 >= insertion.length / 2.0) {
                        // Reverse overlap found.
                        // Insert an equality and swap and trim the surrounding edits.
                        pointer2.previous()
                        pointer2.add(Diff(Operation.EQUAL, deletion.substring(0, overlapLength2)))
                        prevDiff.operation = Operation.INSERT
                        prevDiff.text = insertion.substring(0, insertion.length - overlapLength2)
                        thisDiff2.operation = Operation.DELETE
                        thisDiff2.text = deletion.substring(overlapLength2)
                        // pointer.add inserts the element before the cursor, so there is
                        // no need to step past the new element.
                    }
                }
                thisDiff2 = if (pointer2.hasNext()) pointer2.next() else null
            }
            prevDiff = thisDiff2
            thisDiff2 = if (pointer2.hasNext()) pointer2.next() else null
        }
    }

    /**
     * Look for single edits surrounded on both sides by equalities
     * which can be shifted sideways to align the edit to a word boundary.
     * e.g: The c<ins>at c</ins>ame. -> The <ins>cat </ins>came.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupSemanticLossless(diffs: LinkedList<Diff>) {
        // Create a new iterator at the start.
        val pointer = diffs.listIterator()
        var prevDiff = if (pointer.hasNext()) pointer.next() else null
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        var nextDiff = if (pointer.hasNext()) pointer.next() else null
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff!!.operation == Operation.EQUAL && nextDiff.operation == Operation.EQUAL) {
                // This is a single edit surrounded by equalities.
                var equality1 = prevDiff.text
                var edit = thisDiff!!.text
                var equality2 = nextDiff.text

                // First, shift the edit as far left as possible.
                val commonOffset = diffCommonSuffix(equality1, edit)
                if (commonOffset != 0) {
                    val commonString = edit.substring(edit.length - commonOffset)
                    equality1 = equality1.substring(0, equality1.length - commonOffset)
                    edit = commonString + edit.substring(0, edit.length - commonOffset)
                    equality2 = commonString + equality2
                }

                // Second, step character by character right, looking for the best fit.
                var bestEquality1 = equality1
                var bestEdit = edit
                var bestEquality2 = equality2
                var bestScore =
                    diffCleanupSemanticScore(equality1, edit) + diffCleanupSemanticScore(
                        edit,
                        equality2
                    )
                while (edit.isNotEmpty() && equality2.isNotEmpty() && edit[0] == equality2[0]) {
                    equality1 += edit[0]
                    edit = edit.substring(1) + equality2[0]
                    equality2 = equality2.substring(1)
                    val score =
                        diffCleanupSemanticScore(equality1, edit) + diffCleanupSemanticScore(
                            edit,
                            equality2
                        )
                    // The >= encourages trailing rather than leading whitespace on edits.
                    if (score >= bestScore) {
                        bestScore = score
                        bestEquality1 = equality1
                        bestEdit = edit
                        bestEquality2 = equality2
                    }
                }

                if (prevDiff.text != bestEquality1) {
                    // We have an improvement, save it back to the diff.
                    if (bestEquality1.isNotEmpty()) {
                        prevDiff.text = bestEquality1
                    } else {
                        pointer.previous() // Walk past nextDiff.
                        pointer.previous() // Walk past thisDiff.
                        pointer.previous() // Walk past prevDiff.
                        pointer.remove() // Delete prevDiff.
                        pointer.next() // Walk past thisDiff.
                        pointer.next() // Walk past nextDiff.
                    }
                    thisDiff.text = bestEdit
                    if (bestEquality2.isNotEmpty()) {
                        nextDiff.text = bestEquality2
                    } else {
                        pointer.remove() // Delete nextDiff.
                        nextDiff = thisDiff
                        thisDiff = prevDiff
                    }
                }
            }
            prevDiff = thisDiff
            thisDiff = nextDiff
            nextDiff = if (pointer.hasNext()) pointer.next() else null
        }
    }

    /**
     * Given two strings, compute a score representing whether the internal
     * boundary falls on logical boundaries.
     * Scores range from 6 (best) to 0 (worst).
     *
     * @param one First string.
     * @param two Second string.
     * @return The score.
     */
    private fun diffCleanupSemanticScore(one: String, two: String): Int {
        if (one.isEmpty() || two.isEmpty()) {
            // Edges are the best.
            return 6
        }

        // Each port of this function behaves slightly differently due to
        // subtle differences in each language's definition of things like
        // 'whitespace'.  Since this function's purpose is largely cosmetic,
        // the choice has been made to use each language's native features
        // rather than force total conformity.
        val char1 = one[one.length - 1]
        val char2 = two[0]
        val nonAlphaNumeric1 = !char1.isLetterOrDigit()
        val nonAlphaNumeric2 = !char2.isLetterOrDigit()
        val whitespace1 = nonAlphaNumeric1 && char1.isWhitespace()
        val whitespace2 = nonAlphaNumeric2 && char2.isWhitespace()
        val lineBreak1 = whitespace1 && Character.getType(char1) == Character.CONTROL.toInt()
        val lineBreak2 = whitespace2 && Character.getType(char2) == Character.CONTROL.toInt()
        val blankLine1 = lineBreak1 && blankLineEnd.matcher(one).find()
        val blankLine2 = lineBreak2 && blankLineStart.matcher(two).find()

        if (blankLine1 || blankLine2) {
            // Five points for blank lines.
            return 5
        } else if (lineBreak1 || lineBreak2) {
            // Four points for line breaks.
            return 4
        } else if (nonAlphaNumeric1 && !whitespace1 && whitespace2) {
            // Three points for end of sentences.
            return 3
        } else if (whitespace1 || whitespace2) {
            // Two points for whitespace.
            return 2
        } else if (nonAlphaNumeric1 || nonAlphaNumeric2) {
            // One point for non-alphanumeric.
            return 1
        }
        return 0
    }

    // Define some regex patterns for matching boundaries.
    private val blankLineEnd = Pattern.compile("\\n\\r?\\n\\Z", Pattern.DOTALL)
    private val blankLineStart = Pattern.compile("\\A\\r?\\n\\r?\\n", Pattern.DOTALL)

    /**
     * Reduce the number of edits by eliminating operationally trivial equalities.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupEfficiency(diffs: LinkedList<Diff>, diffEditCost: Int) {
        if (diffs.isEmpty()) {
            return
        }
        var changes = false
        val equalities: Deque<Diff> = ArrayDeque() // Double-ended queue of equalities.
        var lastEquality: String? = null // Always equal to equalities.peek().text
        val pointer = diffs.listIterator()
        // Is there an insertion operation before the last equality.
        var preIns = false
        // Is there a deletion operation before the last equality.
        var preDel = false
        // Is there an insertion operation after the last equality.
        var postIns = false
        // Is there a deletion operation after the last equality.
        var postDel = false
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        var safeDiff = thisDiff // The last Diff that is known to be unsplittable.
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // Equality found.
                if (thisDiff.text.length < diffEditCost && (postIns || postDel)) {
                    // Candidate found.
                    equalities.push(thisDiff)
                    preIns = postIns
                    preDel = postDel
                    lastEquality = thisDiff.text
                } else {
                    // Not a candidate, and can never become one.
                    equalities.clear()
                    lastEquality = null
                    safeDiff = thisDiff
                }
                postIns = false
                postDel = false
            } else {
                // An insertion or deletion.
                if (thisDiff.operation == Operation.DELETE) {
                    postDel = true
                } else {
                    postIns = true
                }
                /*
                 * Five types to be split:
                 * <ins>A</ins><del>B</del>XY<ins>C</ins><del>D</del>
                 * <ins>A</ins>X<ins>C</ins><del>D</del>
                 * <ins>A</ins><del>B</del>X<ins>C</ins>
                 * <ins>A</del>X<ins>C</ins><del>D</del>
                 * <ins>A</ins><del>B</del>X<del>C</del>
                 */
                if (lastEquality != null && ((preIns && preDel && postIns && postDel) ||
                            ((lastEquality.length < diffEditCost / 2) &&
                                    ((if (preIns) 1 else 0) + (if (preDel) 1 else 0) +
                                            (if (postIns) 1 else 0) + (if (postDel) 1 else 0)) == 3))
                ) {
                    // Walk back to offending equality.
                    while (thisDiff != equalities.peek()) {
                        thisDiff = pointer.previous()
                    }
                    pointer.next()

                    // Replace equality with a delete.
                    pointer.set(Diff(Operation.DELETE, lastEquality))
                    // Insert a corresponding an insert.
                    pointer.add(Diff(Operation.INSERT, lastEquality).also { thisDiff = it })

                    equalities.pop() // Throw away the equality we just deleted.
                    lastEquality = null
                    if (preIns && preDel) {
                        // No changes made which could affect previous entry, keep going.
                        postIns = true
                        postDel = true
                        equalities.clear()
                        safeDiff = thisDiff
                    } else {
                        if (!equalities.isEmpty()) {
                            // Throw away the previous equality (it needs to be reevaluated).
                            equalities.pop()
                        }
                        thisDiff = if (equalities.isEmpty()) {
                            // There are no previous questionable equalities,
                            // walk back to the last known safe diff.
                            safeDiff
                        } else {
                            // There is an equality we can fall back to.
                            equalities.peek()
                        }
                        while (thisDiff != pointer.previous()) {
                            // Intentionally empty loop.
                        }
                        postIns = false
                        postDel = false
                    }

                    changes = true
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }

        if (changes) {
            diffCleanupMerge(diffs)
        }
    }

    /**
     * Reorder and merge like edit sections.  Merge equalities.
     * Any edit section can move as long as it doesn't cross an equality.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupMerge(diffs: LinkedList<Diff>) {
        diffs.add(Diff(Operation.EQUAL, "")) // Add a dummy entry at the end.
        val pointer = diffs.listIterator()
        var countDelete = 0
        var countInsert = 0
        var textDelete = ""
        var textInsert = ""
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        var prevEqual: Diff? = null
        var commonlength: Int
        while (thisDiff != null) {
            when (thisDiff.operation) {
                Operation.INSERT -> {
                    countInsert++
                    textInsert += thisDiff.text
                    prevEqual = null
                }
                Operation.DELETE -> {
                    countDelete++
                    textDelete += thisDiff.text
                    prevEqual = null
                }
                Operation.EQUAL -> {
                    if (countDelete + countInsert > 1) {
                        val bothTypes = countDelete != 0 && countInsert != 0
                        // Delete the offending records.
                        pointer.previous() // Reverse direction.
                        repeat(countDelete) {
                            pointer.previous()
                            pointer.remove()
                            countDelete--
                        }
                        repeat(countInsert) {
                            pointer.previous()
                            pointer.remove()
                            countInsert--
                        }
                        if (bothTypes) {
                            // Factor out any common prefixies.
                            commonlength = diffCommonPrefix(textInsert, textDelete)
                            if (commonlength != 0) {
                                if (pointer.hasPrevious()) {
                                    thisDiff = pointer.previous()
                                    if (thisDiff.operation != Operation.EQUAL) {
                                        throw AssertionError("Previous diff should have been an equality.")
                                    }
                                    thisDiff.text += textInsert.substring(0, commonlength)
                                    pointer.next()
                                } else {
                                    pointer.add(
                                        Diff(
                                            Operation.EQUAL,
                                            textInsert.substring(0, commonlength)
                                        )
                                    )
                                }
                                textInsert = textInsert.substring(commonlength)
                                textDelete = textDelete.substring(commonlength)
                            }
                            // Factor out any common suffixies.
                            commonlength = diffCommonSuffix(textInsert, textDelete)
                            if (commonlength != 0) {
                                thisDiff = pointer.next()
                                thisDiff.text =
                                    textInsert.substring(textInsert.length - commonlength) + thisDiff.text
                                textInsert =
                                    textInsert.substring(0, textInsert.length - commonlength)
                                textDelete =
                                    textDelete.substring(0, textDelete.length - commonlength)
                                pointer.previous()
                            }
                        }
                        // Insert the merged records.
                        if (textDelete.isNotEmpty()) {
                            pointer.add(Diff(Operation.DELETE, textDelete))
                        }
                        if (textInsert.isNotEmpty()) {
                            pointer.add(Diff(Operation.INSERT, textInsert))
                        }
                        // Step forward to the equality.
                        thisDiff = if (pointer.hasNext()) pointer.next() else null
                    } else if (prevEqual != null) {
                        // Merge this equality with the previous one.
                        prevEqual.text += thisDiff.text
                        pointer.remove()
                        thisDiff = pointer.previous()
                        pointer.next() // Forward direction
                    }
                    countInsert = 0
                    countDelete = 0
                    textDelete = ""
                    textInsert = ""
                    prevEqual = thisDiff
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }

        if (diffs.peekLast()?.text?.isEmpty() == true) {
            diffs.removeLast() // Remove the dummy entry at the end.
        }

        /*
         * Second pass: look for single edits surrounded on both sides by equalities
         * which can be shifted sideways to eliminate an equality.
         * e.g: A<ins>BA</ins>C -> <ins>AB</ins>AC
         */
        var changes = false
        // Create a new iterator at the start.
        // (As opposed to walking the current one back.)
        val pointer2 = diffs.listIterator()
        var prevDiff = if (pointer2.hasNext()) pointer2.next() else null
        var thisDiff2 = if (pointer2.hasNext()) pointer2.next() else null
        var nextDiff = if (pointer2.hasNext()) pointer2.next() else null
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff!!.operation == Operation.EQUAL && nextDiff.operation == Operation.EQUAL) {
                // This is a single edit surrounded by equalities.
                if (thisDiff2!!.text.endsWith(prevDiff.text)) {
                    // Shift the edit over the previous equality.
                    thisDiff2.text = prevDiff.text + thisDiff2.text.substring(
                        0,
                        thisDiff2.text.length - prevDiff.text.length
                    )
                    nextDiff.text = prevDiff.text + nextDiff.text
                    pointer2.previous() // Walk past nextDiff.
                    pointer2.previous() // Walk past thisDiff.
                    pointer2.previous() // Walk past prevDiff.
                    pointer2.remove() // Delete prevDiff.
                    pointer2.next() // Walk past thisDiff.
                    thisDiff2 = pointer2.next() // Walk past nextDiff.
                    nextDiff = if (pointer2.hasNext()) pointer2.next() else null
                    changes = true
                } else if (thisDiff2.text.startsWith(nextDiff.text)) {
                    // Shift the edit over the next equality.
                    prevDiff.text += nextDiff.text
                    thisDiff2.text = thisDiff2.text.substring(nextDiff.text.length) + nextDiff.text
                    pointer2.remove() // Delete nextDiff.
                    nextDiff = if (pointer2.hasNext()) pointer2.next() else null
                    changes = true
                }
            }
            prevDiff = thisDiff2
            thisDiff2 = nextDiff
            nextDiff = if (pointer2.hasNext()) pointer2.next() else null
        }
        // If shifts were made, the diff needs reordering and another shift sweep.
        if (changes) {
            diffCleanupMerge(diffs)
        }
    }

    /**
     * Class representing one diff operation.
     */
    internal data class Diff(
        /**
         * One of: INSERT, DELETE or EQUAL.
         */
        var operation: Operation,
        /**
         * The text associated with this diff operation.
         */
        var text: String
    ) {

        override fun toString(): String {
            val prettyText = text.replace('\n', '\u00b6')
            return "Diff($operation,\"$prettyText\")"
        }
    }
}