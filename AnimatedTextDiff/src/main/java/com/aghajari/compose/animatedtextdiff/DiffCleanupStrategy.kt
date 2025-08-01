package com.aghajari.compose.animatedtextdiff

/**
 * Strategy for cleaning up diffs.
 */
sealed class DiffCleanupStrategy {
    /**
     * No clean up.
     */
    data object None : DiffCleanupStrategy()

    /**
     * Reduce the number of edits by eliminating semantically trivial equalities.
     */
    data object Semantic : DiffCleanupStrategy()

    /**
     * Reduce the number of edits by eliminating operationally trivial equalities.
     */
    data class Efficiency(
        val editCost: Int = 4,
    ) : DiffCleanupStrategy()

    /**
     * A cleanup strategy for computing diffs at the **word level**, enhancing semantic clarity and
     * layout correctness—especially in RTL texts.
     *
     * This strategy is ideal in scenarios where replacing entire words yields better visual or
     * logical results than character-by-character changes.
     *
     * ### Example:
     * When comparing `"Exit"` with `"Test"`:
     * - Default semantic/efficiency cleanup might result in: `"Exi" + "Tes"`.
     * - `WordSemantic` forces a full replacement: `"Exit" + "Test"`, which is more intuitive
     *   and layout-safe, particularly in RTL contexts.
     *
     * ### `editCost` Behavior:
     * - A **negative** `editCost` (e.g., `-1`) applies semantic cleanup before applying word-level cleanup.
     * - A **positive** value (e.g., `4`) applies efficiency cleanup instead, with the given `editCost`.
     * - A **zero** value skips both semantic and efficiency cleanups, applying only the word-level logic.
     *
     * @param editCost Determines whether to run semantic or efficiency cleanup before word-level diffing.
     * @param wordSplit A function to define what constitutes a "word boundary" (default: `Char.isLetter()`).
     */
    data class WordSemantic(
        val editCost: Int = 4,
        val wordSplit: (Char) -> Boolean = { it.isLetter() },
    ) : DiffCleanupStrategy()
}