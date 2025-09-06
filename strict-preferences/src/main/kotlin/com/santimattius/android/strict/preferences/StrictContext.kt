package com.santimattius.android.strict.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.santimattius.android.strict.preferences.internal.StrictSharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * A [ContextWrapper] that overrides [getSharedPreferences] to return [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences]
 * instances. This ensures that all SharedPreferences access through this context is monitored.
 *
 * It uses a [ConcurrentHashMap] to cache [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences] instances for performance.
 *
 * @param base The base context.
 */
internal class StrictContext(base: Context) : ContextWrapper(base) {
    private val cache = ConcurrentHashMap<String, SharedPreferences>()

    /**
     * Returns a [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences] instance for the given [name] and [mode].
     * The instances are cached to improve performance.
     *
     * @param name The name of the preferences file.
     * @param mode The operating mode.
     * @return A [com.santimattius.android.strict.preferences.internal.StrictSharedPreferences] instance.
     */
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        val key = name ?: "default"
        return cache.getOrPut(key) {
            StrictSharedPreferences.create(super.getSharedPreferences(name, mode))
        }
    }
}
