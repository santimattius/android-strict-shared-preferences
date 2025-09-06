package com.santimattius.android.strict.preferences.internal

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.internal.StrictSharedPreferences.Companion.setDebugMode

/**
 * A [android.content.SharedPreferences] wrapper that enforces strict mode on main thread access.
 * It logs a warning or throws an [IllegalStateException] if SharedPreferences is accessed on the main thread.
 * The behavior in debug mode can be configured using [setDebugMode].
 *
 * @property delegate The underlying [android.content.SharedPreferences] instance.
 */
internal class StrictSharedPreferences private constructor(
    private val delegate: SharedPreferences
) : SharedPreferences {

    /**
     * @see SharedPreferences.getAll
     */
    override fun getAll(): MutableMap<String, *> {
        checkMainThread("getAll")
        return delegate.all
    }

    /**
     * @see SharedPreferences.getString
     */
    override fun getString(key: String?, defValue: String?): String? {
        checkMainThread("getString")
        return delegate.getString(key, defValue)
    }

    /**
     * @see SharedPreferences.getStringSet
     */
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        checkMainThread("getStringSet")
        return delegate.getStringSet(key, defValues)
    }

    /**
     * @see SharedPreferences.getInt
     */
    override fun getInt(key: String?, defValue: Int): Int {
        checkMainThread("getInt")
        return delegate.getInt(key, defValue)
    }

    /**
     * @see SharedPreferences.getLong
     */
    override fun getLong(key: String?, defValue: Long): Long {
        checkMainThread("getLong")
        return delegate.getLong(key, defValue)
    }

    /**
     * @see SharedPreferences.getFloat
     */
    override fun getFloat(key: String?, defValue: Float): Float {
        checkMainThread("getFloat")
        return delegate.getFloat(key, defValue)
    }

    /**
     * @see SharedPreferences.getBoolean
     */
    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        checkMainThread("getBoolean")
        return delegate.getBoolean(key, defValue)
    }

    /**
     * @see SharedPreferences.contains
     */
    override fun contains(key: String?): Boolean {
        checkMainThread("contains")
        return delegate.contains(key)
    }

    /**
     * @see SharedPreferences.edit
     */
    override fun edit(): SharedPreferences.Editor {
        checkMainThread("edit")
        return delegate.edit()
    }

    /**
     * @see SharedPreferences.registerOnSharedPreferenceChangeListener
     */
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * @see SharedPreferences.unregisterOnSharedPreferenceChangeListener
     */
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun checkMainThread(method: String) {
        //TODO: get line of code from stacktrace
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (isDebug) {
                StrictMode.noteSlowCall("${TAG}: ⚠️ $method() on MAIN thread")
                //throw IllegalStateException("SharedPreferences.$method() on MAIN thread")
            } else {
                Log.w(TAG, "⚠️ $method() on MAIN thread")
            }
        }
    }


    companion object Companion {
        private const val TAG = "StrictSharedPreferences"
        private var isDebug = false
        private var configuration = StrictPreferencesConfiguration(isDebug)

        /**
         * Sets the debug mode for [StrictSharedPreferences].
         * In debug mode, accessing SharedPreferences on the main thread will trigger a [StrictMode.noteSlowCall].
         * Otherwise, it will log a warning.
         *
         * @param debug True to enable debug mode, false otherwise.
         */
        fun setDebugMode(debug: Boolean) {
            isDebug = debug
            configuration = configuration.copy(isDebug = debug)
        }

        fun setConfiguration(configuration: StrictPreferencesConfiguration) {
            this.configuration = configuration
        }

        /**
         * Returns a [StrictSharedPreferences] instance for the given [name] and [mode].
         *
         * @param context The context to use.
         * @param name The name of the preferences file.
         * @param mode The operating mode.
         * @return A [StrictSharedPreferences] instance.
         */
        fun getInstance(context: Context, name: String, mode: Int): SharedPreferences {
            return create(context.getSharedPreferences(name, mode))
        }

        /**
         * Creates a [StrictSharedPreferences] instance that wraps the given [delegate].
         *
         * @param delegate The [SharedPreferences] instance to wrap.
         * @return A [StrictSharedPreferences] instance.
         */
        fun create(delegate: SharedPreferences): SharedPreferences {
            return StrictSharedPreferences(delegate)
        }
    }
}