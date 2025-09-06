package com.santimattius.android.strict.preferences.internal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * A [android.content.ContextWrapper] that overrides [getSharedPreferences] to return [StrictSharedPreferences]
 * instances. This ensures that all SharedPreferences access through this context is monitored.
 *
 * It uses a [java.util.concurrent.ConcurrentHashMap] to cache [StrictSharedPreferences] instances for performance.
 *
 * @param base The base context.
 */
internal class StrictContext(base: Context) : ContextWrapper(base) {
    private val cache = ConcurrentHashMap<String, SharedPreferences>()

    /**
     * Returns a [StrictSharedPreferences] instance for the given [name] and [mode].
     * The instances are cached to improve performance.
     *
     * @param name The name of the preferences file.
     * @param mode The operating mode.
     * @return A [StrictSharedPreferences] instance.
     */
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        val key = name ?: "default"
        return cache.getOrPut(key) {
            StrictSharedPreferences.create(super.getSharedPreferences(name, mode))
        }
    }
}