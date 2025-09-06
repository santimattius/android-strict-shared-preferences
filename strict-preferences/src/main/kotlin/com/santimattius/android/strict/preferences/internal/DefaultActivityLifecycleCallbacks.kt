package com.santimattius.android.strict.preferences.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * A default implementation of [Application.ActivityLifecycleCallbacks] with empty implementations
 * for all methods. This class can be extended to override only the necessary lifecycle methods.
 */
internal abstract class DefaultActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    /** @see Application.ActivityLifecycleCallbacks.onActivityCreated */
    override fun onActivityCreated(p0: Activity, p1: Bundle?) {}

    /** @see Application.ActivityLifecycleCallbacks.onActivityDestroyed */
    override fun onActivityDestroyed(p0: Activity) {}

    /** @see Application.ActivityLifecycleCallbacks.onActivityPaused */
    override fun onActivityPaused(p0: Activity) {}

    /** @see Application.ActivityLifecycleCallbacks.onActivityResumed */
    override fun onActivityResumed(p0: Activity) {}

    /** @see Application.ActivityLifecycleCallbacks.onActivitySaveInstanceState */
    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}

    /** @see Application.ActivityLifecycleCallbacks.onActivityStarted */
    override fun onActivityStarted(p0: Activity) {}

    /** @see Application.ActivityLifecycleCallbacks.onActivityStopped */
    override fun onActivityStopped(p0: Activity) {}
}