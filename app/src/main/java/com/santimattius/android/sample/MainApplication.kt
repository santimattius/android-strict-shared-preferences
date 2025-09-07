package com.santimattius.android.sample

import android.util.Log
import com.santimattius.android.strict.preferences.StrictPreferences
import com.santimattius.android.strict.preferences.StrictPreferencesApplication
import com.santimattius.android.strict.preferences.StrictPreferencesConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainApplication : StrictPreferencesApplication(isDebug = true) {

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