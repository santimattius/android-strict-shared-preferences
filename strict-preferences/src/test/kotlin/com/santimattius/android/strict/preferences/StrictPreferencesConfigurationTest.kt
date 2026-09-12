package com.santimattius.android.strict.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictPreferencesConfigurationTest {
    @Test
    fun `apply event emission is disabled by default`() {
        val configuration = StrictPreferencesConfiguration()

        assertFalse(configuration.emitPreferencesApplyEvents)
    }

    @Test
    fun `withPreferencesApplyEvents enables apply event emission`() {
        val configuration = StrictPreferencesConfiguration()

        val enabled = configuration.withPreferencesApplyEvents()

        assertTrue(enabled.emitPreferencesApplyEvents)
    }

    @Test
    fun `existing positional constructor arguments retain their positions`() {
        val configuration = StrictPreferencesConfiguration(true, true, true, null, null)

        assertTrue(configuration.isDebug)
        assertTrue(configuration.emitMainThreadAccessEvents)
        assertTrue(configuration.preferencesManagerOverrides)
        assertFalse(configuration.emitPreferencesApplyEvents)
    }
}
