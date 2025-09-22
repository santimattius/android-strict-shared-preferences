package com.santimattius.android.strict.preferences.internal

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.startup.Initializer
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup

/**
 * Initializes [StrictSharedPreferences] using [androidx.startup.Initializer].
 *
 * This initializer performs the following actions:
 * 1. If the application context implements [StrictPreferencesStartup] and manual initialization is not enabled,
 * it retrieves the [StrictPreferencesConfiguration] from the application and sets it for [StrictSharedPreferences].
 * 2. If the application context is an [Application], it registers an [OverrideActivityContext] as an activity lifecycle callback.
 * This ensures that activities use the overridden context provided by this library.
 * 3. Retrieves the current [StrictPreferencesConfiguration].
 * 4. Sets up [StrictMode] policies based on the retrieved configuration.
 * 5. If `preferencesManagerOverrides` is enabled in the configuration, it overrides the default
 * `SharedPreferences` instance in `androidx.preference.PreferenceManager`.
 * 6. Notifies [StrictPreferences] that the startup initialization is complete.
 *
 * This class is intended to be used with the AndroidX Startup library to automatically initialize
 * the StrictPreferences library when the application starts.
 */
class StrictPreferencesInitializer : Initializer<Unit> {

    /**
     * Initializes [StrictSharedPreferences] in the application.
     *
     * @param context The application context.
     */
    override fun create(context: Context) {
        if (context is StrictPreferencesStartup && !StrictPreferences.isManual) {
            val configuration = context.getConfiguration()
            StrictSharedPreferences.setConfiguration(configuration)
        }
        if (context is Application) {
            context.registerActivityLifecycleCallbacks(OverrideActivityContext())
        }
        val configuration = StrictSharedPreferences.getConfiguration()
        setupStrictMode(configuration)
        if (configuration.preferencesManagerOverrides) {
            overridePreferenceManager(context)
        }
        StrictPreferences.startupInit()
    }

    private fun setupStrictMode(configuration: StrictPreferencesConfiguration) {
        val threadPolicy = configuration.getThreadPolicy()
        if (threadPolicy != null) {
            StrictMode.setThreadPolicy(threadPolicy)
        }
        val vmPolicy = configuration.getVmPolicy()
        if (vmPolicy != null) {
            StrictMode.setVmPolicy(vmPolicy)
        }
    }

    /**
     * @return A list of dependencies for this initializer. None in this case.
     */
    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }

    // Replace default SharedPreferences from PreferenceManager
    private fun overridePreferenceManager(context: Context) {
        try {
            val prefs = StrictSharedPreferences.create(
                context.getSharedPreferences("default", Context.MODE_PRIVATE)
            )
            val prefManagerClass = Class.forName("androidx.preference.PreferenceManager")
            val field = prefManagerClass.getDeclaredField("sSharedPreferences")
            field.isAccessible = true
            field.set(null, prefs)
        } catch (e: Exception) {
            Log.w(LIB_TAG, "Error overriding PreferenceManager", e)
        }
    }
}