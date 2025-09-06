package com.santimattius.android.strict.preferences

interface StrictPreferencesStartup {

    fun getConfiguration(): StrictPreferencesConfiguration = StrictPreferencesConfiguration(false)
}