package com.santimattius.android.strict.preferences

data class StrictPreferencesConfiguration(
    val isDebug: Boolean = false,
    val isMetricEnabled: Boolean = true,
    val emitMainThreadAccessEvents: Boolean = false,
)
