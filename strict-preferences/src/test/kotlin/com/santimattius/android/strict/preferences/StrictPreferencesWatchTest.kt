package com.santimattius.android.strict.preferences

import android.content.SharedPreferences
import com.santimattius.android.strict.preferences.internal.MainThreadAccessEvent
import com.santimattius.android.strict.preferences.internal.PreferencesApplyEvent
import com.santimattius.android.strict.preferences.internal.StrictPreferencesEvent
import com.santimattius.android.strict.preferences.internal.StrictSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class StrictPreferencesWatchTest {
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
    fun `watch receives distinct main thread access events but not apply events`() {
        val mainThreadEvents = mutableListOf<MainThreadAccessEvent>()
        val applyEvents = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watch(scope) { mainThreadEvents += it }
        StrictPreferences.watchApplyEvents(scope) { applyEvents += it }
        val preferences = StrictSharedPreferences.create(preferences(), "account") as StrictSharedPreferences

        emitMainThreadAccessEvent("getString")
        emitMainThreadAccessEvent("getString")
        preferences.edit().apply()

        scope.cancel()
        assertEquals(1, mainThreadEvents.size)
        assertEquals(1, applyEvents.size)
    }

    @Test
    fun `watchApplyEvents receives repeated apply events without deduplicating them`() {
        val events = mutableListOf<PreferencesApplyEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) { events += it }
        val preferences = StrictSharedPreferences.create(preferences(), "account") as SharedPreferences

        preferences.edit().apply()
        preferences.edit().apply()

        scope.cancel()
        assertEquals(2, events.size)
    }

    private fun emitMainThreadAccessEvent(methodName: String) {
        val bus =
            StrictSharedPreferences::class.java
                .getDeclaredField("_strictPreferencesEventBus")
                .apply { isAccessible = true }
                .get(null) as MutableSharedFlow<StrictPreferencesEvent>
        bus.tryEmit(MainThreadAccessEvent(methodName, timestamp = 1L, threadName = "main"))
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
