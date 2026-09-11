package com.santimattius.android.strict.preferences

import android.os.StrictMode

/**
 * Configuration for StrictPreferences.
 *
 * @property isDebug Whether to enable debug mode. In debug mode, StrictMode violations will crash the app.
 * @property isMetricEnabled Whether to enable metrics collection.
 * @property emitMainThreadAccessEvents Whether to emit events when SharedPreferences are accessed on the main thread.
 * @property preferencesManagerOverrides Whether to override the default SharedPreferencesManager.
 * @property threadPolicy Custom StrictMode.ThreadPolicy to use. If null, a default policy will be used.
 * @property vmPolicy Custom StrictMode.VmPolicy to use. If null, a default policy will be used.
 * @property emitPreferencesApplyEvents Whether to emit diagnostic breadcrumbs for preferences apply calls.
 */
data class StrictPreferencesConfiguration(
    val isDebug: Boolean = false,
    val emitMainThreadAccessEvents: Boolean = false,
    val preferencesManagerOverrides: Boolean = false,
    private val threadPolicy: StrictMode.ThreadPolicy? = null,
    private val vmPolicy: StrictMode.VmPolicy? = null,
    val emitPreferencesApplyEvents: Boolean = false,
) {
    /**
     * Enables or disables debug mode.
     *
     * In debug mode, StrictMode violations will crash the app. This is useful for identifying
     * and fixing StrictMode violations during development.
     *
     * @param isDebug Whether to enable debug mode.
     * @return A new StrictPreferencesConfiguration instance with the specified debug mode.
     */
    fun withDebug(isDebug: Boolean): StrictPreferencesConfiguration = copy(isDebug = isDebug)

    /**
     * Enables or disables the emission of events when SharedPreferences are accessed on the main thread.
     *
     * @param emitMainThreadAccessEvents True to emit events, false otherwise.
     * @return A new StrictPreferencesConfiguration instance with the specified setting.
     */
    fun withMainThreadAccessEvents(emitMainThreadAccessEvents: Boolean): StrictPreferencesConfiguration =
        copy(emitMainThreadAccessEvents = emitMainThreadAccessEvents)

    /**
     * Enables diagnostic breadcrumb emission for preferences apply calls.
     *
     * @return A new StrictPreferencesConfiguration with apply event emission enabled.
     */
    fun withPreferencesApplyEvents(): StrictPreferencesConfiguration = copy(emitPreferencesApplyEvents = true)

    /**
     * Sets whether to override the default SharedPreferencesManager.
     *
     * @param preferencesManagerOverrides True to override the default SharedPreferencesManager, false otherwise.
     * @return A new StrictPreferencesConfiguration instance with the specified override setting.
     */
    fun withPreferencesManagerOverrides(preferencesManagerOverrides: Boolean): StrictPreferencesConfiguration =
        copy(preferencesManagerOverrides = preferencesManagerOverrides)

    /**
     * Sets a custom StrictMode.ThreadPolicy.
     *
     * @param policy The custom StrictMode.ThreadPolicy to use.
     * @return A new StrictPreferencesConfiguration instance with the specified thread policy.
     */
    fun withThreadPolicy(policy: StrictMode.ThreadPolicy): StrictPreferencesConfiguration = copy(threadPolicy = policy)

    /**
     * Sets a custom StrictMode.VmPolicy to be used.
     *
     * @param policy The custom StrictMode.VmPolicy.
     * @return A new StrictPreferencesConfiguration instance with the updated VmPolicy.
     */
    fun withVmPolicy(policy: StrictMode.VmPolicy): StrictPreferencesConfiguration = copy(vmPolicy = policy)

    /**
     * Retrieves the custom StrictMode.ThreadPolicy if one has been set.
     *
     * @return The StrictMode.ThreadPolicy, or null if no custom policy is set.
     */
    fun getThreadPolicy(): StrictMode.ThreadPolicy? = threadPolicy

    /**
     * Retrieves the custom StrictMode.VmPolicy if one has been set.
     *
     * @return The StrictMode.VmPolicy, or null if no custom policy is set.
     */
    fun getVmPolicy(): StrictMode.VmPolicy? = vmPolicy
}
