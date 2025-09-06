package com.santimattius.android.strict.preferences

/**
 * Interface for components that provide the initial [StrictPreferencesConfiguration].
 * This allows for custom configuration of the StrictPreferences library during startup.
 * Implementations of this interface are typically used with AndroidX App Startup Initializers.
 */
interface StrictPreferencesStartup {

    /**
     * Provides the [StrictPreferencesConfiguration] to be used by the StrictPreferences library.
     * This method is called during the library's initialization process.
     *
     * @return The [StrictPreferencesConfiguration] to apply. Defaults to a configuration with
     *         `isDebug` set to `false` and `emitMainThreadAccessEvents` to `false` if not overridden.
     */
    fun getConfiguration(): StrictPreferencesConfiguration = StrictPreferencesConfiguration(isDebug = false)
}