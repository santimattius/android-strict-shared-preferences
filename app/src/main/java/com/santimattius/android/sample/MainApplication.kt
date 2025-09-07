package com.santimattius.android.sample

import android.app.Application
import android.util.Log
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import com.santimattius.android.strict.preferences.StrictPreferencesStartup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// If need enable strict mode
// class MainApplication : StrictPreferencesApplication(isDebug = true)
class MainApplication : Application(), StrictPreferencesStartup {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        //TODO: Manual start
        //StrictPreferences.start(this)
        StrictPreferences.watch(coroutineScope) {
            Log.d("MainApplication", "$it")
        }
    }

    override fun getConfiguration(): StrictPreferencesConfiguration {
        return super.getConfiguration().copy(emitMainThreadAccessEvents = false)
    }
}