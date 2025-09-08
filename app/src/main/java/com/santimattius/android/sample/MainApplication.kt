package com.santimattius.android.sample

import android.util.Log
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesApplication
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup
import com.santimattius.android.strict.preferences.internal.MainThreadAccessEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// If need enable strict mode
 class MainApplication : StrictPreferencesApplication(isDebug = true){
//class MainApplication : Application(), StrictPreferencesStartup {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val anrMonitor = MyAnrMonitor(coroutineScope)

    override fun onCreate() {
        super.onCreate()
        //TODO: Manual start
        val entryPoint = CustomConfiguration()
        StrictPreferences.start(this, entryPoint.getConfiguration())

        anrMonitor.startMonitoring()
    }

    override fun getConfiguration(): StrictPreferencesConfiguration {
        return super.getConfiguration().copy(emitMainThreadAccessEvents = false)
    }
}

class CustomConfiguration : StrictPreferencesStartup{
    override fun getConfiguration(): StrictPreferencesConfiguration {
        return super.getConfiguration().copy(emitMainThreadAccessEvents = true, isDebug = true)
    }
}

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