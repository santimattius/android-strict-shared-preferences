package com.santimattius.android.strict.preferences

data class StrictPreferencesConfiguration(
    val isDebug: Boolean,
    val isMetricEnabled: Boolean = true,
    val emitMainThreadAccessEvents: Boolean = false,
)
