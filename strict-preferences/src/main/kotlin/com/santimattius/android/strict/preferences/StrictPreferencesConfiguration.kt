package com.santimattius.android.strict.preferences

data class StrictPreferencesConfiguration(
    val isDebug: Boolean,
    val isMetricEnabled: Boolean = true,
    val setupMode: SetupMode = SetupMode.AUTOMATIC,
    val overrideMode: OverrideMode = OverrideMode.ALL,
    val emitMainThreadAccessEvents: Boolean = false // Added new flag
)

enum class OverrideMode {
    ACTIVITIES,
    APPLICATION,
    ALL
}

enum class SetupMode {
    MANUAL,
    AUTOMATIC
}
