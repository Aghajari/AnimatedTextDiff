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
}