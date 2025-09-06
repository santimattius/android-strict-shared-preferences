package com.santimattius.android.strict.preferences.internal

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.santimattius.android.strict.preferences.SetupMode
import com.santimattius.android.strict.preferences.StrictPreferencesApplication
import com.santimattius.android.strict.preferences.StrictPreferencesStartup

/**
 * Initializes [StrictSharedPreferences] by registering an activity lifecycle callback
 * to override the context in activities and optionally overriding the default SharedPreferences
 * instance in [androidx.preference.PreferenceManager] if the application implements [com.santimattius.android.strict.preferences.StrictPreferencesApplication].
 */
class StrictPreferencesInitializer : Initializer<Unit> {

    /**
     * Initializes [StrictSharedPreferences] in the application.
     *
     * @param context The application context.
     */
    override fun create(context: Context) {
        if (context is StrictPreferencesStartup) {
            val configuration = context.getConfiguration()
            if (configuration.setupMode == SetupMode.MANUAL) return
            StrictSharedPreferences.setConfiguration(configuration)
            if (context is Application) {
                context.registerActivityLifecycleCallbacks(OverrideActivityContext())
            }
            if (context is StrictPreferencesApplication) {
                overridePreferenceManager(context)
            }
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
            Log.w("StrictPreferencesInitializer", "Error overriding PreferenceManager", e)
        }
    }
}