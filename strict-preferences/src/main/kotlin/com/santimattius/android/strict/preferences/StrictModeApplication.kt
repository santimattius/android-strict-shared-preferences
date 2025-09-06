package com.santimattius.android.strict.preferences

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.util.Log

/**
 * Base [Application] class that enables [StrictMode] and initializes [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences].
 * Extend this class to automatically enable strict mode and use [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences]
 * throughout your application.
 *
 * @property isDebug Whether the application is in debug mode. This controls [StrictMode] behavior
 * and [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences] logging.
 */
abstract class StrictModeApplication(
    private val isDebug: Boolean = false
) : Application(), StrictPreferencesStartup {

    /**
     * Called when the application is starting, before any other application objects have been created.
     * Initializes [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences] and enables [StrictMode] if in debug mode.
     */
    override fun onCreate() {
        super.onCreate()
        enableStrictMode()

    }

    override fun getConfiguration(): StrictPreferencesConfiguration {
        return super.getConfiguration().copy(isDebug = isDebug)
    }

    private fun enableStrictMode() {
        if (isDebug) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder().detectAll()
                        .penaltyListener({ runnable -> runnable.run() }) { violation ->
                            // Custom handling of the violation
                            val message = "StrictMode violation: ${violation.stackTraceToString()}"
                            Log.w("StrictMode", message)
                        }.build()
                )
            } else {
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
                )
            }
        }
    }

    /**
     * Attaches the base context to the application, wrapping it with [StrictContext].
     *
     * @param base The new base context for this wrapper.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(StrictContext(base))
    }
}
