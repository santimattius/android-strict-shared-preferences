# {{Library Name}}
This is a library description.

# Features

- **Feature 1:** Description of feature 1.
- 
# Installation

You can add this library to your Android project using Gradle. Make sure to include the repository in your project-level `build.gradle` file:

```groovy
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		maven { url 'https://jitpack.io' }
	}
}
```

Then, add the dependency in your `build.gradle` file at the application level:

```groovy
dependencies {
   implementation "module:${version}"
}

```

Replace `version` with the version of the library you want to use.

# Usage

## 1. Introduction

StrictPreferences is an Android library designed to help developers detect and diagnose SharedPreferences access on the main application thread. Accessing SharedPreferences (especially for write operations or complex reads) on the main thread can lead to UI freezes and "Application Not Responding" (ANR) errors, negatively impacting user experience.

This library provides:
*   Automatic detection of SharedPreferences calls on the main thread.
*   Configurable responses to main thread access:
    *   Logging warnings to Logcat.
    *   Triggering `StrictMode.noteSlowCall()` in debug builds.
    *   Emitting detailed events for custom handling or analytics.
*   Easy setup and integration into your Android application.

## 2. Getting Started & Setup

### 2.1. Initializing the Library

The library needs to be initialized early in your application's lifecycle, typically within your `Application.onCreate()` method or through an AndroidX App Startup Initializer that you define.

**Option A: Manual Initialization in `Application.onCreate()` (Recommended for most cases)**

This is the most straightforward way to initialize the library.
```kotlin
// In your Application class
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration // If customizing
import com.santimattius.android.strict.preferences.internal.StrictPreferencesInitializer // Used by StrictPreferences.start

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Basic initialization
        StrictPreferences.start(this)

        // For more advanced configuration (see section 3)
        // You can provide a custom configuration when the library is initialized by App Startup
        // or by directly setting it if manual setup is preferred (less common for initial setup).
        // The library's StrictPreferencesInitializer will attempt to find a
        // StrictPreferencesStartup provider.
    }
}
```
The `StrictPreferences.start(context)` method ensures that the library's internal `StrictPreferencesInitializer` is run. This initializer sets up the necessary hooks for monitoring SharedPreferences access, including an `ActivityLifecycleCallbacks` to wrap Activity contexts.

**Option B: Using a Custom AndroidX App Startup Initializer**

If you prefer to manage initialization solely through App Startup, you can create your own `Initializer` that depends on `StrictPreferencesInitializer` or directly configures the library.

```kotlin
// Example: Your custom App Startup Initializer
import android.content.Context
import androidx.startup.Initializer
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup
import com.santimattius.android.strict.preferences.internal.StrictSharedPreferences // For direct configuration
import android.util.Log // Added for Log.i

// 1. Define your configuration provider (Optional, if customizing)
class MyStrictPreferencesConfigProvider : StrictPreferencesStartup {
    override fun getConfiguration(): StrictPreferencesConfiguration {
        return StrictPreferencesConfiguration(
            isDebug = BuildConfig.DEBUG, // Example: Link to your app's debug state
            emitMainThreadAccessEvents = true
        )
    }
}

// 2. Create an Initializer that sets the configuration
class MyAppStrictPrefsInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Option 2.1: If you have a StrictPreferencesStartup provider,
        // AppInitializer will pick it up automatically if it's discoverable.
        // StrictPreferences.start(context) will ensure initialization.

        // Option 2.2: For very direct control (less common for initial setup)
        // You can get the configuration from your provider and set it.
        // This is generally handled by the library's own initializer if a provider exists.
        val customConfig = MyStrictPreferencesConfigProvider().getConfiguration()
        StrictSharedPreferences.setConfiguration(customConfig) // Accessing internal but public static method

        // Ensure the main library hooks are set up
        StrictPreferences.start(context)

        Log.i("MyAppStrictPrefs", "StrictPreferences configured and started via App Startup.")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // If StrictPreferences had its own discoverable Initializer you'd list it here.
        // For now, StrictPreferences.start() handles its internal initializer.
        return emptyList()
    }
}
```
Remember to add your Initializer to your `AndroidManifest.xml` providers section.

## 3. Configuration

You can customize the behavior of StrictPreferences using the `StrictPreferencesConfiguration` data class.

**`StrictPreferencesConfiguration` fields:**

*   `isDebug: Boolean`:
    *   If `true`, main thread access will trigger `StrictMode.noteSlowCall()`.
    *   If `false` (default), main thread access will log a warning to Logcat.
*   `emitMainThreadAccessEvents: Boolean`:
    *   If `true` (default is `false`), the library will emit `MainThreadAccessEvent` objects when main thread access is detected. These events can be observed using `StrictPreferences.watch()`.

**Providing Configuration:**

The primary way to provide an initial configuration is by creating a class that implements the `StrictPreferencesStartup` interface. The library's `StrictPreferencesInitializer` (run by `StrictPreferences.start()`) will look for an implementation of this interface via App Startup's component discovery mechanism.

**Step 1: Implement `StrictPreferencesStartup`**
```kotlin
package com.example.myapp // Your app's package

import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup
// import com.example.myapp.BuildConfig // Assuming your BuildConfig is here

class MyAppStrictPreferencesConfigurationProvider : StrictPreferencesStartup {
    override fun getConfiguration(): StrictPreferencesConfiguration {
        return StrictPreferencesConfiguration(
            // isDebug = BuildConfig.DEBUG, // Tie to your app's debug status
            isDebug = true, // Example, replace with BuildConfig.DEBUG
            emitMainThreadAccessEvents = true // Enable event emission
        )
    }
}
```
**Step 2: Make it discoverable by App Startup**

You need to declare your `StrictPreferencesStartup` implementation as a metadata provider in your `AndroidManifest.xml` so that the `StrictPreferencesInitializer` can find it.
```xml
<manifest xmlns:tools="http://schemas.android.com/tools" ...>
    <application ...>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <!-- Your StrictPreferencesStartup implementation -->
            <meta-data
                android:name="com.example.myapp.MyAppStrictPreferencesConfigurationProvider"
                android:value="androidx.startup" />

            <!-- The library's own initializer, which will use your provider if found -->
             <meta-data
                android:name="com.santimattius.android.strict.preferences.internal.StrictPreferencesInitializer"
                android:value="androidx.startup" />
        </provider>
    </application>
</manifest>
```
If `StrictPreferencesInitializer` finds your `MyAppStrictPreferencesConfigurationProvider`, it will use the `StrictPreferencesConfiguration` you return. Otherwise, it uses a default configuration.

**Direct Configuration (Advanced/Less Common for Initial Setup):**
While `StrictPreferencesStartup` is preferred for initial configuration, you can also set the configuration directly using:
```kotlin
val customConfig = StrictPreferencesConfiguration(isDebug = true, emitMainThreadAccessEvents = true)
StrictSharedPreferences.setConfiguration(customConfig)
// or for just debug mode:
// StrictSharedPreferences.setDebugMode(true)
```
This is an internal API (`StrictSharedPreferences.Companion`) but is public. It's more useful for dynamic changes or testing rather than initial setup.

## 4. Detecting Violations

### 4.1. Default Behavior

*   **Debug Builds (`isDebug = true` in configuration):**
    When a SharedPreferences operation occurs on the main thread, `StrictMode.noteSlowCall()` is invoked with a message detailing the violation. This can result in log messages, screen flashes, or other penalties depending on your overall StrictMode setup.
*   **Release Builds (`isDebug = false` in configuration):**
    A warning message is logged to Logcat (tag: "StrictSharedPreferences") detailing the method and thread.

### 4.2. Custom Handling with `StrictPreferences.watch()`

If you've enabled `emitMainThreadAccessEvents = true` in your `StrictPreferencesConfiguration`, you can observe detailed events for each main thread access. This is useful for custom logging, analytics, or displaying in-app warnings during development.
```kotlin
// In your Application class, a ViewModel, or a dedicated utility class
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.internal.MainThreadAccessEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log // Added for Log.d

class MyAnrMonitor(private val applicationScope: CoroutineScope) {

    fun startMonitoring() {
        StrictPreferences.watch(applicationScope) { event: MainThreadAccessEvent ->
            // Process the event
            Log.d("MyAnrMonitor", "Main thread SharedPreferences access detected:")
            Log.d("MyAnrMonitor", "  Method: ${event.methodName}")
            Log.d("MyAnrMonitor", "  Timestamp: ${event.timestamp}")
            Log.d("MyAnrMonitor", "  Thread: ${event.threadName}")
            Log.d("MyAnrMonitor", "  Caller: ${event.callerClassName}.${event.callerMethodName}() Line: ${event.callerLineNumber}")

            // Example: Send to analytics, display a developer toast, etc.
            // sendToAnalytics(event)
        }
    }
}

// Usage in Application.onCreate()
// val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main) // Or your preferred scope
// MyAnrMonitor(appScope).startMonitoring()
```
## 5. Understanding the Event: `MainThreadAccessEvent`

When you use `StrictPreferences.watch()`, you receive `MainThreadAccessEvent` objects. This data class contains the following information:

*   `methodName: String`: The name of the SharedPreferences method that was called (e.g., "getString", "edit", "Editor.commit").
*   `timestamp: Long`: The time the access occurred (System.currentTimeMillis()).
*   `threadName: String`: The name of the thread on which the access occurred (should be "main" or similar).
*   `callerClassName: String?`: The fully qualified name of the class that made the SharedPreferences call.
*   `callerMethodName: String?`: The name of the method within the caller class.
*   `callerLineNumber: Int?`: The line number in the caller class where the SharedPreferences operation was invoked.

This detailed information is invaluable for quickly pinpointing the source of main thread SharedPreferences abuse in your codebase.

## 6. How It Works (Briefly)

StrictPreferences works by:
1.  **Context Wrapping**: When initialized (typically via `StrictPreferences.start()` which uses `StrictPreferencesInitializer`), it registers an `Application.ActivityLifecycleCallbacks`. This callback (`OverrideActivityContext`) wraps the base context of each Activity with a `StrictContext`.
2.  **Overriding `getSharedPreferences()`**: The `StrictContext` overrides the `getSharedPreferences()` method. Instead of returning a standard `SharedPreferences` instance, it returns an instance of `StrictSharedPreferences`.
3.  **Intercepting Calls**: `StrictSharedPreferences` is a wrapper around the real `SharedPreferences` instance. It intercepts all method calls (like `getString()`, `edit()`, etc.).
4.  **Thread Check**: Before delegating the call to the actual `SharedPreferences` instance, `StrictSharedPreferences` calls its `checkMainThread()` method. This method determines if the call is on the main thread and then acts according to the configured policies (logs, StrictMode, emits events).
5.  **Editor Wrapping**: The `edit()` method of `StrictSharedPreferences` returns a `StrictEditor` which, in turn, checks `apply()` and `commit()` calls on the main thread.

This mechanism ensures that most SharedPreferences access originating from Activities or the Application context is monitored.

By using StrictPreferences, you can proactively identify and fix potential ANR-inducing SharedPreferences calls, leading to a smoother and more responsive application.

# Contributions

Contributions are welcome! If you want to contribute to this library, please follow these steps:

1. Fork the repository.
2. Create a new branch for your contribution (`git checkout -b feature/new-feature`).
3. Make your changes and ensure you follow the style guides and coding conventions.
4. Commit your changes (`git commit -am 'Add new feature'`).
5. Push your changes to your GitHub repository (`git push origin feature/new-feature`).
6. Create a new pull request and describe your changes in detail.

## Contact

If you have questions, issues, or suggestions regarding this library, feel free to [open a new issue](https://github.com/santimattius/{{repository}}/issues) on GitHub. We are here to help you!
