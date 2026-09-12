package com.santimattius.android.strict.preferences.internal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CommitConcurrencyInstrumentedTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        StrictSharedPreferences.setConfiguration(
            StrictPreferencesConfiguration(emitPreferencesApplyEvents = true),
        )
    }

    @After
    fun tearDown() {
        StrictSharedPreferences.setConfiguration(StrictPreferencesConfiguration())
    }

    @Test
    fun overlappingCommitsOnOneInstanceEmitExactlyOneEventForTheSecondCaller() {
        val fileName = uniqueFileName("same-instance")
        val firstCommitEntered = CountDownLatch(1)
        val allowCommitsToFinish = CountDownLatch(1)
        val preferences =
            StrictSharedPreferences.create(
                blockingPreferences(firstCommitEntered, allowCommitsToFinish),
                fileName,
            )

        assertOneSecondCallerEvent(fileName, preferences, preferences, firstCommitEntered, allowCommitsToFinish)
    }

    @Test
    fun overlappingCommitsThroughTwoStrictContextsWithTheSameFileNameEmitExactlyOneEvent() {
        val fileName = uniqueFileName("two-contexts")
        val firstCommitEntered = CountDownLatch(1)
        val allowCommitsToFinish = CountDownLatch(1)
        val base = BlockingPreferencesContext(appContext, firstCommitEntered, allowCommitsToFinish)
        val firstContext = StrictContext(base)
        val secondContext = StrictContext(base)
        val firstPreferences = firstContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        val secondPreferences = secondContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

        assertOneSecondCallerEvent(
            fileName,
            firstPreferences,
            secondPreferences,
            firstCommitEntered,
            allowCommitsToFinish,
        )
    }

    @Test
    fun overlappingCommitsForDistinctFileNamesEmitNoEvents() {
        val commitsEntered = CountDownLatch(2)
        val allowCommitsToFinish = CountDownLatch(1)
        val firstPreferences =
            StrictSharedPreferences.create(
                blockingPreferences(commitsEntered, allowCommitsToFinish),
                uniqueFileName("first"),
            )
        val secondPreferences =
            StrictSharedPreferences.create(
                blockingPreferences(commitsEntered, allowCommitsToFinish),
                uniqueFileName("second"),
            )
        val eventSeen = CountDownLatch(1)
        val events = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) {
            synchronized(events) { events += it }
            eventSeen.countDown()
        }
        val failures = AtomicReference<Throwable?>()
        val first = commitOnThread("first-distinct-commit", firstPreferences, failures)
        val second = commitOnThread("second-distinct-commit", secondPreferences, failures)

        try {
            first.start()
            second.start()
            assertTrue("Both commits must be in flight", commitsEntered.await(5, TimeUnit.SECONDS))
            assertFalse("Distinct preference files must not be considered concurrent", eventSeen.await(250, TimeUnit.MILLISECONDS))
        } finally {
            allowCommitsToFinish.countDown()
            first.join(5_000)
            second.join(5_000)
            scope.cancel()
        }

        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertNoThreadFailure(failures)
        assertTrue(events.isEmpty())
    }

    private fun assertOneSecondCallerEvent(
        fileName: String,
        firstPreferences: SharedPreferences,
        secondPreferences: SharedPreferences,
        firstCommitEntered: CountDownLatch,
        allowCommitsToFinish: CountDownLatch,
    ) {
        val eventSeen = CountDownLatch(1)
        val events = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) {
            synchronized(events) { events += it }
            eventSeen.countDown()
        }
        val failures = AtomicReference<Throwable?>()
        val first = commitOnThread("first-same-file-commit", firstPreferences, failures)
        val second = commitOnThread("second-same-file-commit", secondPreferences, failures)

        try {
            first.start()
            assertTrue("The first commit must enter its delegate", firstCommitEntered.await(5, TimeUnit.SECONDS))
            second.start()
            assertTrue("The non-first commit must emit before it returns", eventSeen.await(5, TimeUnit.SECONDS))
        } finally {
            allowCommitsToFinish.countDown()
            first.join(5_000)
            second.join(5_000)
            scope.cancel()
        }

        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertNoThreadFailure(failures)
        assertEquals(1, events.size)
        assertEquals(fileName, events.single().fileName)
        assertEquals("second-same-file-commit", events.single().threadName)
    }

    private fun commitOnThread(
        name: String,
        preferences: SharedPreferences,
        failures: AtomicReference<Throwable?>,
    ): Thread =
        Thread({
            try {
                preferences.edit().commit()
            } catch (failure: Throwable) {
                failures.compareAndSet(null, failure)
            }
        }, name)

    private fun blockingPreferences(
        commitEntered: CountDownLatch,
        allowCommitToFinish: CountDownLatch,
    ): SharedPreferences {
        val editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "commit" -> {
                        commitEntered.countDown()
                        check(allowCommitToFinish.await(5, TimeUnit.SECONDS)) {
                            "Timed out waiting for the test to release commit()"
                        }
                        true
                    }

                    "apply" -> {
                        Unit
                    }

                    else -> {
                        error("Unexpected editor method: ${method.name}")
                    }
                }
            } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }

    private fun assertNoThreadFailure(failures: AtomicReference<Throwable?>) {
        assertNull("A commit thread failed", failures.get())
    }

    private fun uniqueFileName(prefix: String): String = "$prefix-${System.nanoTime()}"

    private inner class BlockingPreferencesContext(
        base: Context,
        private val commitEntered: CountDownLatch,
        private val allowCommitsToFinish: CountDownLatch,
    ) : ContextWrapper(base) {
        override fun getSharedPreferences(
            name: String?,
            mode: Int,
        ): SharedPreferences = blockingPreferences(commitEntered, allowCommitsToFinish)
    }
}
