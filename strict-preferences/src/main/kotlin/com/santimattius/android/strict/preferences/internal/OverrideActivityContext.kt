package com.santimattius.android.strict.preferences.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import com.santimattius.android.strict.preferences.StrictContext

/**
 * An [android.app.Application.ActivityLifecycleCallbacks] that overrides the base context of an [Activity]
 * with a [StrictContext] before the Activity is created. This ensures that all SharedPreferences
 * access within the Activity goes through [StrictSharedPreferences].
 */
internal class OverrideActivityContext : DefaultActivityLifecycleCallbacks() {

    /**
     * Called before an [Activity] is created.
     * Overrides the base context of the [Activity] with a [StrictContext].
     *
     * @param activity The [Activity] being created.
     * @param savedInstanceState The saved instance state.
     */
    @SuppressLint("PrivateApi")
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            val field = ContextWrapper::class.java.getDeclaredField("mBase")
            field.isAccessible = true
            val base = field.get(activity) as Context
            if (base !is StrictContext) {
                field.set(activity, StrictContext(base))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}