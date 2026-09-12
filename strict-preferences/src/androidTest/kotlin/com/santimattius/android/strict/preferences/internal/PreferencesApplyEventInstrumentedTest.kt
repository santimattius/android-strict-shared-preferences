package com.santimattius.android.strict.preferences.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PreferencesApplyEventInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

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
    fun applyEmitsExactlyOneEventWithTheNamedPreferencesFile() {
        val fileName = uniqueFileName()
        val events =
            withCollectedEvents(expectEvent = true) {
                try {
                    val preferences =
                        StrictSharedPreferences.create(
                            context.getSharedPreferences(fileName, Context.MODE_PRIVATE),
                            fileName,
                        )
                    preferences.edit().putString("value", "written").apply()
                } finally {
                    clearPreferences(fileName)
                }
            }

        assertEquals(1, events.size)
        assertEquals(fileName, events.single().fileName)
    }

    @Test
    fun applyEmitsNoEventWhileTheFeatureFlagIsDisabled() {
        StrictSharedPreferences.setConfiguration(
            StrictPreferencesConfiguration(emitPreferencesApplyEvents = false),
        )
        val fileName = uniqueFileName()
        val events =
            withCollectedEvents(expectEvent = false) {
                try {
                    val preferences =
                        StrictSharedPreferences.create(
                            context.getSharedPreferences(fileName, Context.MODE_PRIVATE),
                            fileName,
                        )
                    preferences.edit().putString("value", "written").apply()
                } finally {
                    clearPreferences(fileName)
                }
            }

        assertTrue(events.isEmpty())
    }

    @Test
    fun legacyFactoryPathEmitsAnEventWithoutFileNameAttribution() {
        val fileName = uniqueFileName()
        val events =
            withCollectedEvents(expectEvent = true) {
                try {
                    val preferences =
                        StrictSharedPreferences.create(
                            context.getSharedPreferences(fileName, Context.MODE_PRIVATE),
                        )
                    preferences.edit().putString("value", "written").apply()
                } finally {
                    clearPreferences(fileName)
                }
            }

        assertEquals(1, events.size)
        assertEquals(null, events.single().fileName)
    }

    private fun withCollectedEvents(
        expectEvent: Boolean,
        block: () -> Unit,
    ): List<PreferencesApplyEvent> {
        val events = mutableListOf<PreferencesApplyEvent>()
        val eventDelivered = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        StrictPreferences.watchApplyEvents(scope) {
            events += it
            eventDelivered.countDown()
        }
        awaitEventCollector()
        try {
            block()
            if (expectEvent) {
                assertTrue("Expected apply event", eventDelivered.await(5, TimeUnit.SECONDS))
            } else {
                assertFalse("Unexpected apply event", eventDelivered.await(250, TimeUnit.MILLISECONDS))
            }
            return events
        } finally {
            scope.cancel()
        }
    }

    private fun awaitEventCollector() {
        runBlocking {
            eventBus().subscriptionCount.first { it > 0 }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun eventBus(): MutableSharedFlow<StrictPreferencesEvent> =
        StrictSharedPreferences::class.java
            .getDeclaredField("_strictPreferencesEventBus")
            .apply { isAccessible = true }
            .get(null) as MutableSharedFlow<StrictPreferencesEvent>

    private fun clearPreferences(fileName: String) {
        context
            .getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun uniqueFileName(): String = "apply-event-${System.nanoTime()}"
}
