package com.santimattius.android.strict.preferences.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [CommitConcurrencyTracker] purely as a process-global in-flight commit registry.
 *
 * Every test uses a unique file name because the tracker is a process-global singleton
 * (see design.md open question: no reset hook is exposed on purpose).
 */
class CommitConcurrencyTrackerTest {

    @Test
    fun `first enter on a fresh file name returns false`() {
        val fileName = uniqueFileName()

        val result = CommitConcurrencyTracker.enter(fileName)

        assertFalse(result)
    }

    @Test
    fun `nested enter on the same file name returns true`() {
        val fileName = uniqueFileName()

        val first = CommitConcurrencyTracker.enter(fileName)
        val second = CommitConcurrencyTracker.enter(fileName)

        assertFalse(first)
        assertTrue(second)
    }

    @Test
    fun `exit restores the counter so a later enter is first again`() {
        val fileName = uniqueFileName()
        CommitConcurrencyTracker.enter(fileName) // first in flight -> false
        CommitConcurrencyTracker.enter(fileName) // nested -> true
        CommitConcurrencyTracker.exit(fileName)
        CommitConcurrencyTracker.exit(fileName)

        val result = CommitConcurrencyTracker.enter(fileName)

        assertFalse(result)
    }

    @Test
    fun `distinct file names are tracked independently`() {
        val fileNameA = uniqueFileName()
        val fileNameB = uniqueFileName()
        CommitConcurrencyTracker.enter(fileNameA) // occupies A's counter

        val resultOnB = CommitConcurrencyTracker.enter(fileNameB)

        assertFalse(resultOnB)
    }

    @Test
    fun `null file name always returns false and creates no entry`() {
        val first = CommitConcurrencyTracker.enter(null)
        val second = CommitConcurrencyTracker.enter(null)

        assertFalse(first)
        assertFalse(second)
    }

    private fun uniqueFileName(): String = "commit-tracker-test-${System.nanoTime()}"
}
