package xyz.tosdev.yetasimplelyicsapp.data.preferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualPreferencesInstrumentedTest {
    private val preferenceKey = "keep_screen_awake"
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var hadStoredValue = false
    private var storedValue = false

    @Before
    fun removeOnlyKeepScreenPreference() {
        val preferences = context.getSharedPreferences("visual_preferences", Context.MODE_PRIVATE)
        hadStoredValue = preferences.contains(preferenceKey)
        storedValue = preferences.getBoolean(preferenceKey, false)
        preferences.edit()
            .remove(preferenceKey)
            .commit()
    }

    @After
    fun restoreKeepScreenPreference() {
        val editor = context.getSharedPreferences("visual_preferences", Context.MODE_PRIVATE)
            .edit()
        if (hadStoredValue) editor.putBoolean(preferenceKey, storedValue)
        else editor.remove(preferenceKey)
        editor.commit()
    }

    @Test
    fun keepScreenAwakeDefaultsOnAndPersistsAnExplicitOffValue() {
        val preferences = VisualPreferences(context)

        assertTrue(preferences.load().keepScreenAwake)

        preferences.save(preferences.load().copy(keepScreenAwake = false))

        assertFalse(VisualPreferences(context).load().keepScreenAwake)
    }
}
