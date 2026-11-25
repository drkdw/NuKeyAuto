package com.ddw.nukeyauto.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InMemoryPrefsTest {
    @Test
    fun `stores, removes and clears values`() {
        val prefs = InMemoryPrefs()
        prefs.edit().putString("token", "t").putInt("delay", 5).apply()
        assertEquals("t", prefs.getString("token", ""))
        assertEquals(5, prefs.getInt("delay", 0))

        prefs.edit().remove("token").apply()
        assertEquals("", prefs.getString("token", ""))

        prefs.edit().clear().putBoolean("confirm", false).commit()
        assertFalse(prefs.contains("delay"))
        assertEquals(false, prefs.getBoolean("confirm", true))
    }
}
