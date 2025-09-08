package com.santimattius.android.strict.preferences

import android.content.Context
import android.util.Log
import androidx.startup.AppInitializer
import com.santimattius.android.strict.preferences.internal.LIB_TAG
import com.santimattius.android.strict.preferences.internal.MainThreadAccessEvent
import com.santimattius.android.strict.preferences.internal.StrictPreferencesInitializer
import com.santimattius.android.strict.preferences.internal.StrictSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Main entry point for interacting with the StrictPreferences library.
 * Provides methods to initialize the library and observe main thread access events.
 */
object StrictPreferences {

    private var _isStarted = false
    private var _isManual = false
    internal val isManual: Boolean
        get() = _isManual

    internal fun startupInit() {
        _isStarted = true
    }

    /**
     * Initializes the StrictPreferences library using AndroidX App Startup.
     * This method ensures that [StrictPreferencesInitializer] is run, which sets up
     * the necessary hooks for monitoring SharedPreferences access.
     *
     * It is recommended to call this method early in the application lifecycle, typically
     * in `Application.onCreate()` or via an automatic App Startup initializer.
     *
     * @param context The application context.
     */
    fun start(
        context: Context,
        configuration: StrictPreferencesConfiguration
    ) {
        if (_isStarted) {
            Log.w(LIB_TAG, "StrictPreferences is already started")
            return
        }
        _isManual = true
        StrictSharedPreferences.setConfiguration(configuration)
        val appContext = context.applicationContext
        AppInitializer.getInstance(appContext)
            .initializeComponent(StrictPreferencesInitializer::class.java)
    }

    /**
     * Observes the [MainThreadAccessEvent]s emitted by [StrictSharedPreferences].
     * This allows external components to react to SharedPreferences access on the main thread.
     *
     * The events are collected from [StrictSharedPreferences.mainThreadAccessEventBus],
     * filtered for distinct events, and then processed by the provided [onEvent] lambda
     * within the given [coroutineScope].
     *
     * @param coroutineScope The [CoroutineScope] in which to collect the events.
     * @param onEvent A suspend function that will be invoked with each unique [MainThreadAccessEvent].
     */
    fun watch(coroutineScope: CoroutineScope, onEvent: suspend (MainThreadAccessEvent) -> Unit) {
        StrictSharedPreferences
            .mainThreadAccessEventBus
            .distinctUntilChanged() // Avoid processing the same event multiple times if emitted rapidly
            .onEach { onEvent(it) }
            .launchIn(coroutineScope)
    }
}
