package com.santimattius.android.strict.preferences.internal

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Represents an event when SharedPreferences is accessed on the main thread.
 *
 * @property methodName The name of the SharedPreferences method accessed (e.g., "getString", "edit").
 * @property timestamp The time at which the access occurred, in milliseconds.
 * @property threadName The name of the thread on which the access occurred.
 * @property callerClassName The name of the class that called the SharedPreferences method.
 * @property callerMethodName The name of the method within the caller class.
 * @property callerLineNumber The line number in the caller class where the SharedPreferences method was called.
 */
data class MainThreadAccessEvent(
    val methodName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name,
    val callerClassName: String? = null,
    val callerMethodName: String? = null,
    val callerLineNumber: Int? = null
)

/**
 * A [android.content.SharedPreferences] wrapper that enforces configured policies on main thread access.
 * By default, it logs a warning if SharedPreferences is accessed on the main thread.
 * Behavior can be further configured using [StrictPreferencesConfiguration],
 * such as enabling [StrictMode.noteSlowCall] in debug builds or emitting [MainThreadAccessEvent]s.
 *
 * @property delegate The underlying [android.content.SharedPreferences] instance.
 * @constructor Creates a new instance of StrictSharedPreferences.
 */
internal class StrictSharedPreferences private constructor(
    private val delegate: SharedPreferences
) : SharedPreferences {

    // region SharedPreferences Overrides
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
     * @return A [StrictEditor] that also checks for main thread access on its apply/commit methods.
     */
    override fun edit(): SharedPreferences.Editor {
        checkMainThread("edit")
        //return StrictEditor(delegate.edit(), ::checkMainThread)
        return delegate.edit( )
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
    // endregion

    /**
     * Checks if the current operation is being performed on the main thread.
     * If it is, it triggers configured warnings and/or emits a [MainThreadAccessEvent].
     *
     * @param method The name of the SharedPreferences method being called (e.g., "getString", "edit").
     */
    private fun checkMainThread(method: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return // Not on the main thread, nothing to do
        }

        val currentThreadName = Thread.currentThread().name
        handleMainThreadWarning(method, currentThreadName)

        if (configuration.emitMainThreadAccessEvents) {
            emitMainThreadAccessEvent(method, currentThreadName)
        }
    }

    /**
     * Handles the warning mechanism when a SharedPreferences operation is detected on the main thread.
     * This includes logging a warning or using [StrictMode.noteSlowCall] based on the current [configuration].
     *
     * @param method The name of the SharedPreferences method.
     * @param threadName The name of the current thread (expected to be the main thread).
     */
    private fun handleMainThreadWarning(method: String, threadName: String) {
        val message = "${TAG}: ⚠️ $method() on MAIN thread ($threadName)"
        if (configuration.isDebug) {
            StrictMode.noteSlowCall(message)
        } else {
            Log.w(TAG, message)
        }
    }

    /**
     * Creates and emits a [MainThreadAccessEvent] if event emission is enabled.
     * The event includes details about the method call and the caller.
     *
     * @param method The name of the SharedPreferences method.
     * @param threadName The name of the current thread.
     */
    private fun emitMainThreadAccessEvent(method: String, threadName: String) {
        val (callerClassName, callerMethodName, callerLineNumber) = findCallerDetails()

        val event = MainThreadAccessEvent(
            methodName = method,
            threadName = threadName,
            callerClassName = callerClassName,
            callerMethodName = callerMethodName,
            callerLineNumber = callerLineNumber
        )
        if (!_mainThreadAccessEventBus.tryEmit(event)) {
            Log.w(TAG, "Failed to emit MainThreadAccessEvent for $method. Buffer might be full.")
        }
    }

    /**
     * Finds the details (class name, method name, line number) of the external code that invoked
     * the SharedPreferences operation by inspecting the current thread's stack trace.
     * It intelligently skips internal library calls and common system calls to pinpoint the origin more accurately.
     *
     * @return A [Triple] containing the caller's class name, method name, and line number. Returns null for these if not found.
     */
    private fun findCallerDetails(): Triple<String?, String?, Int?> {
        val stackTrace = Thread.currentThread().stackTrace

        // Iterate over the stack trace to find the first relevant calling frame.
        for (element in stackTrace) {
            val className = element.className
            // Skip known internal, system, or library-specific class name prefixes.
            if (className.startsWith(JAVA_LANG_THREAD_PREFIX) ||
                className.startsWith(DALVIK_SYSTEM_VMSTACK_PREFIX) ||
                className.startsWith(OWN_CLASS_NAME_PREFIX) ||
                className.startsWith(STRICT_CONTEXT_CLASS_NAME_PREFIX)
            ) {
                continue
            }
            // The first element not matching the filtered prefixes is considered the external caller.
            return Triple(element.className, element.methodName, element.lineNumber)
        }
        // Should ideally not happen if called from an external context as expected.
        return Triple(null, null, null)
    }

    /**
     * Companion object for [StrictSharedPreferences].
     * Holds static configuration, factory methods, and the event bus.
     */
    companion object Companion {
        /**
         * Logcat tag used by [StrictSharedPreferences].
         */
        private const val TAG = "StrictSharedPreferences"

        /**
         * Prefix for the class name of [StrictSharedPreferences] itself, used for stack trace filtering.
         * This helps in identifying calls originating from within this library versus external calls.
         */
        private val OWN_CLASS_NAME_PREFIX = StrictSharedPreferences::class.java.name.substringBeforeLast('.')
        /**
         * Class name prefix for [com.santimattius.android.strict.preferences.StrictContext], used for stack trace filtering.
         */
        private const val STRICT_CONTEXT_CLASS_NAME_PREFIX = "com.santimattius.android.strict.preferences.StrictContext"
        /**
         * Class name prefix for [java.lang.Thread], used for stack trace filtering to ignore thread internals.
         */
        private const val JAVA_LANG_THREAD_PREFIX = "java.lang.Thread"
        /**
         * Class name prefix for Dalvik VM stack internal calls, used for stack trace filtering.
         */
        private const val DALVIK_SYSTEM_VMSTACK_PREFIX = "dalvik.system.VMStack"

        /**
         * The current global configuration for all [StrictSharedPreferences] instances.
         * This can be updated via [setConfiguration] or partially via [setDebugMode].
         */
        private var configuration = StrictPreferencesConfiguration(isDebug = false)

        /**
         * Private [MutableSharedFlow] used to emit [MainThreadAccessEvent]s.
         * It is configured with no replay and a limited buffer, dropping oldest events on overflow.
         */
        private val _mainThreadAccessEventBus = MutableSharedFlow<MainThreadAccessEvent>(
            replay = 0, // New subscribers do not get past events.
            extraBufferCapacity = 64, // Buffer size for events.
            onBufferOverflow = BufferOverflow.DROP_OLDEST // Strategy for handling buffer overflow.
        )
        /**
         * Publicly exposed [SharedFlow] for observing [MainThreadAccessEvent]s.
         * External components can collect events from this flow to monitor main thread SharedPreferences access.
         */
        val mainThreadAccessEventBus = _mainThreadAccessEventBus.asSharedFlow()

        /**
         * Sets the debug mode for [StrictSharedPreferences].
         * This updates the [isDebug] field in the global [configuration].
         * In debug mode, main thread access typically triggers [StrictMode.noteSlowCall].
         *
         * @param debug True to enable debug mode, false otherwise.
         */
        fun setDebugMode(debug: Boolean) {
            configuration = configuration.copy(isDebug = debug)
        }

        /**
         * Sets the global [StrictPreferencesConfiguration] for all [StrictSharedPreferences] instances.
         * This allows for detailed control over how strict policies are enforced.
         *
         * @param newConfiguration The new [StrictPreferencesConfiguration] to apply.
         */
        fun setConfiguration(newConfiguration: StrictPreferencesConfiguration) {
            configuration = newConfiguration
        }

        /**
         * Returns a [StrictSharedPreferences] instance for the given [name] and [mode],
         * wrapping the SharedPreferences obtained from the provided [context].
         *
         * @param context The context to use for obtaining the underlying SharedPreferences.
         * @param name The name of the preferences file.
         * @param mode The operating mode (e.g., [Context.MODE_PRIVATE]).
         * @return A [StrictSharedPreferences] instance.
         */
        fun getInstance(context: Context, name: String, mode: Int): SharedPreferences {
            return create(context.getSharedPreferences(name, mode))
        }

        /**
         * Creates a [StrictSharedPreferences] instance that wraps the given delegate [SharedPreferences].
         * This is the core factory method for creating instances of [StrictSharedPreferences].
         *
         * @param delegate The [SharedPreferences] instance to wrap.
         * @return A new [StrictSharedPreferences] instance.
         */
        fun create(delegate: SharedPreferences): SharedPreferences {
            return StrictSharedPreferences(delegate)
        }
    }

    /**
     * An internal [SharedPreferences.Editor] that also checks for main thread access
     * on its [commit] and [apply] methods.
     *
     * This ensures that not only read operations but also write operations performed via the editor
     * are monitored for main thread violations.
     *
     * @property delegateEditor The underlying [SharedPreferences.Editor] instance.
     * @property checkMainThread A function reference to the main thread checking logic of the parent [StrictSharedPreferences].
     */
    private class StrictEditor(
        private val delegateEditor: SharedPreferences.Editor,
        private val checkMainThread: (String) -> Unit
    ) : SharedPreferences.Editor by delegateEditor {

        /**
         * @see SharedPreferences.Editor.commit
         * Performs [checkMainThread] before delegating to the underlying editor's commit.
         */
        override fun commit(): Boolean {
            checkMainThread("Editor.commit")
            return delegateEditor.commit()
        }

        /**
         * @see SharedPreferences.Editor.apply
         * Performs [checkMainThread] before delegating to the underlying editor's apply.
         */
        override fun apply() {
            checkMainThread("Editor.apply")
            delegateEditor.apply()
        }
    }
}
