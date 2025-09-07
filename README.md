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
   implementation "com.github.santimattius:android-strict-shared-preferences:${version}"
   // or implementation "your-group:strict-preferences:${version}" if hosted elsewhere
}

```

Replace `version` with the latest version of the library.

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

There are two primary ways to initialize the StrictPreferences library:

### Method 1: Automatic Initialization via AndroidX App Startup (Recommended)

This method leverages AndroidX App Startup for automatic initialization and configuration.

**Step 1: Implement `StrictPreferencesStartup`**

Your `Application` class (or another class discoverable by App Startup) needs to implement the `StrictPreferencesStartup` interface to provide the desired configuration.

```kotlin
package com.example.myapp

import android.app.Application
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup
// import com.example.myapp.BuildConfig // Assuming your BuildConfig is here

class MyApplication : Application(), StrictPreferencesStartup {

    override fun onCreate() {
        super.onCreate()
        // No need to call StrictPreferences.start() here, App Startup handles it.
    }

    override fun getConfiguration(): StrictPreferencesConfiguration {
        return StrictPreferencesConfiguration(
            isDebug = BuildConfig.DEBUG, // Tie to your app's debug status
            emitMainThreadAccessEvents = true // Example: Enable event emission
        )
    }
}
```

**Step 2: Ensure Library's Initializer is in Manifest**

The StrictPreferences library should include its `StrictPreferencesInitializer` in its `AndroidManifest.xml`, which will be merged into your app's manifest. This initializer will automatically find your `StrictPreferencesStartup` implementation.

If you need to ensure it's present or manage its discovery, your merged `AndroidManifest.xml` should effectively include:
```xml
<manifest xmlns:tools="http://schemas.android.com/tools" ...>
    <application ...>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">

            <!-- Your Application class implementing StrictPreferencesStartup -->
            <meta-data
                android:name="com.example.myapp.MyApplication"
                android:value="androidx.startup" />

            <!-- The library's own initializer, which will use your provider if found -->
            <!-- Ensure this is present if not automatically merged or if overriding discovery -->
            <meta-data
                android:name="com.santimattius.android.strict.preferences.internal.StrictPreferencesInitializer"
                android:value="androidx.startup" />
        </provider>
    </application>
</manifest>
```
*(Note: The exact meta-data name for your `Application` class might vary if you use a different class for `StrictPreferencesStartup`)*

With this setup, the library initializes automatically using your provided configuration.

### Method 2: Manual Initialization

If you prefer to control initialization explicitly or cannot use App Startup's auto-initialization.

**Step 1: Remove or Disable Automatic Initialization**

You *must* prevent the library's `StrictPreferencesInitializer` from running automatically. You can do this by adding a `tools:node="remove"` attribute to its entry in your app's `AndroidManifest.xml`:

```xml
<manifest xmlns:tools="http://schemas.android.com/tools" ...>
    <application ...>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">

            <!-- Remove the library's default initializer to prevent auto-startup -->
            <meta-data
                android:name="com.santimattius.android.strict.preferences.internal.StrictPreferencesInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
    </application>
</manifest>
```

**Step 2: Implement `StrictPreferencesStartup` and Call `StrictPreferences.start()`**

Your `Application` class should implement `StrictPreferencesStartup` and explicitly call `StrictPreferences.start()` in its `onCreate()` method.

```kotlin
package com.example.myapp

import android.app.Application
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup
// import com.example.myapp.BuildConfig

class MyApplication : Application(), StrictPreferencesStartup {

    override fun onCreate() {
        super.onCreate()

        // Manual initialization
        StrictPreferences.start(this)
    }

    override fun getConfiguration(): StrictPreferencesConfiguration {
        // Provide your custom configuration here
        return StrictPreferencesConfiguration(
            isDebug = BuildConfig.DEBUG,
            emitMainThreadAccessEvents = true
        )
    }
}
```
In this manual setup, `StrictPreferences.start(this)` invokes `StrictPreferencesInitializer`, which then attempts to get the configuration from the passed `Context` (your `Application` instance) if it implements `StrictPreferencesStartup`.

## 3. Configuration Details

The `StrictPreferencesConfiguration` data class allows you to customize the library's behavior:

*   `isDebug: Boolean`:
    *   Default: `false`.
    *   If `true`, main thread access will trigger `StrictMode.noteSlowCall()` with details.
    *   If `false`, main thread access will log a warning to Logcat.
*   `emitMainThreadAccessEvents: Boolean`:
    *   Default: `false`.
    *   If `true`, the library emits `MainThreadAccessEvent` objects when main thread access is detected. These events can be observed using `StrictPreferences.watch()`.

**Example `StrictPreferencesStartup` Implementation:**
```kotlin
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup

class MyAppStrictConfig : StrictPreferencesStartup {
    override fun getConfiguration(): StrictPreferencesConfiguration {
        return StrictPreferencesConfiguration(
            isDebug = true, // Enable StrictMode penalties during development
            emitMainThreadAccessEvents = true // Enable event emission for custom logging
        )
    }
}
```
## 4. Detecting Violations

### 4.1. Default Behavior (Based on Configuration)

*   **If `isDebug = true`**: When a SharedPreferences operation occurs on the main thread, `StrictMode.noteSlowCall()` is invoked. This can result in log messages, screen flashes, or other penalties depending on your global StrictMode setup.
*   **If `isDebug = false`**: A warning message is logged to Logcat (tag: "StrictSharedPreferences") detailing the method and thread.

### 4.2. Custom Handling with `StrictPreferences.watch()`

If you've enabled `emitMainThreadAccessEvents = true` in `StrictPreferencesConfiguration`, you can observe detailed events for each main thread access. This is useful for custom logging, analytics, or displaying in-app warnings during development.

```kotlin
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.internal.MainThreadAccessEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log

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

// Example usage, typically in your Application class or a dedicated utility:
// val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
// val anrMonitor = MyAnrMonitor(appScope)
// anrMonitor.startMonitoring() // Start when appropriate
```

## 5. Understanding the Event: `MainThreadAccessEvent`

When you use `StrictPreferences.watch()`, you receive `MainThreadAccessEvent` objects. This data class contains:

*   `methodName: String`: The name of the SharedPreferences method called (e.g., "getString", "edit", "Editor.commit").
*   `timestamp: Long`: Time of access (System.currentTimeMillis()).
*   `threadName: String`: Name of the thread (should be "main" or similar).
*   `callerClassName: String?`: Fully qualified name of the class that made the call.
*   `callerMethodName: String?`: Name of the method within the caller class.
*   `callerLineNumber: Int?`: Line number in the caller class.

This detailed information helps pinpoint the source of main thread SharedPreferences abuse.

## 6. How It Works (Briefly)

1.  **Initialization**: `StrictPreferencesInitializer` (run automatically by App Startup or manually via `StrictPreferences.start()`) sets up the library.
    *   It fetches the `StrictPreferencesConfiguration` from your `StrictPreferencesStartup` implementation.
    *   It registers an `Application.ActivityLifecycleCallbacks` (`OverrideActivityContext`).
2.  **Context Wrapping**: `OverrideActivityContext` wraps the base context of each Activity with a `StrictContext`.
3.  **Overriding `getSharedPreferences()`**: `StrictContext` overrides `getSharedPreferences()` to return an instance of `StrictSharedPreferences`.
4.  **Intercepting Calls**: `StrictSharedPreferences` (and its internal `StrictEditor`) wraps the real `SharedPreferences` instance. It intercepts all method calls.
5.  **Thread Check**: Before delegating, `StrictSharedPreferences` calls `checkMainThread()`. This method checks if the call is on the main thread and acts based on the `StrictPreferencesConfiguration` (logs, `StrictMode.noteSlowCall`, emits events).

This ensures most SharedPreferences access from Activities or the Application context is monitored.

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
