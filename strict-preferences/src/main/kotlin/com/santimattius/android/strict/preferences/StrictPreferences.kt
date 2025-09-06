package com.santimattius.android.strict.preferences

import android.content.Context
import androidx.startup.AppInitializer

object StrictPreferences {

    fun start(
        context: Context,
    ) {
        val appContext = context.applicationContext
        AppInitializer.getInstance(appContext)
            .initializeComponent(StrictPreferencesInitializer::class.java)
    }

    fun watch(on: () -> Unit) {
        //TODO: listen for changes
    }
}