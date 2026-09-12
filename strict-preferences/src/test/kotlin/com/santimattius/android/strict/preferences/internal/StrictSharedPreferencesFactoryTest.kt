package com.santimattius.android.strict.preferences.internal

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class StrictSharedPreferencesFactoryTest {
    @Test
    fun `create with a file name retains that name on the wrapper`() {
        val wrapper = StrictSharedPreferences.create(delegate(), "account-settings") as StrictSharedPreferences

        assertEquals("account-settings", wrapper.fileName)
    }

    @Test
    fun `legacy create path retains no file name`() {
        val wrapper = StrictSharedPreferences.create(delegate()) as StrictSharedPreferences

        assertEquals(null, wrapper.fileName)
    }

    private fun delegate(): SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, _, _ -> error("Delegate should not be called by factory tests") } as SharedPreferences
}
