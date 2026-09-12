package com.santimattius.android.strict.preferences.internal

import android.content.SharedPreferences
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class StrictEditorCommitTest {
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
    fun `single commit emits no apply event`() {
        val events = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) { events += it }

        StrictSharedPreferences.create(preferences { true }, "single").edit().commit()

        scope.cancel()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `concurrent commit emits before the delegate commit returns`() {
        val fileName = "concurrent"
        val events = mutableListOf<PreferencesApplyEvent>()
        var eventsWhenDelegateCommitRuns = -1
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) { events += it }
        assertFalse(CommitConcurrencyTracker.enter(fileName))

        try {
            StrictSharedPreferences
                .create(
                    preferences {
                        eventsWhenDelegateCommitRuns = events.size
                        true
                    },
                    fileName,
                ).edit()
                .commit()

            assertEquals(1, eventsWhenDelegateCommitRuns)
            assertEquals(1, events.size)
        } finally {
            CommitConcurrencyTracker.exit(fileName)
            scope.cancel()
        }
    }

    private fun preferences(onCommit: () -> Boolean): SharedPreferences {
        val editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "apply" -> Unit
                    "commit" -> onCommit()
                    else -> error("Unexpected editor method: ${method.name}")
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
}
