package com.santimattius.android.strict.preferences.internal

import android.content.SharedPreferences
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class StrictSharedPreferencesTest {
    @Before
    fun setUp() {
        LifecycleStageTracker.resetForTesting()
    }

    @After
    fun tearDown() {
        StrictSharedPreferences.setConfiguration(StrictPreferencesConfiguration())
        LifecycleStageTracker.resetForTesting()
    }

    @Test
    fun `apply emits one attributed event when apply diagnostics are enabled`() {
        StrictSharedPreferences.setConfiguration(
            StrictPreferencesConfiguration(emitPreferencesApplyEvents = true),
        )
        val events = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) { events += it }

        (StrictSharedPreferences.create(preferences(), "account") as SharedPreferences)
            .edit()
            .apply()

        scope.cancel()
        assertEquals(1, events.size)
        assertEquals("account", events.single().fileName)
        assertEquals(LifecycleStage.STARTUP, events.single().lifecycleStage)
        assertEquals(Thread.currentThread().name, events.single().threadName)
    }

    @Test
    fun `apply emits no event when apply diagnostics are disabled`() {
        StrictSharedPreferences.setConfiguration(
            StrictPreferencesConfiguration(emitPreferencesApplyEvents = false),
        )
        val events = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) { events += it }

        (StrictSharedPreferences.create(preferences(), "account") as SharedPreferences)
            .edit()
            .apply()

        scope.cancel()
        assertTrue(events.isEmpty())
    }

    private fun preferences(): SharedPreferences {
        val editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "apply" -> Unit
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
