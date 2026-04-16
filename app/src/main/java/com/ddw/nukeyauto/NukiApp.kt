package com.ddw.nukeyauto

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.ddw.nukeyauto.data.PrefKeys
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NukiApp : Application() {

    @Inject lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        applyStoredTheme()
        // Android 12+ Material You dynamic color — no-op on older versions.
        if (prefs.getBoolean(PrefKeys.DYNAMIC_COLORS, true)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }

    /**
     * Apply the user's stored theme preference (light/dark/system).
     * Falls back to system default if the pref cannot be read.
     */
    private fun applyStoredTheme() {
        val theme = try {
            prefs.getString(PrefKeys.THEME, PrefKeys.THEME_SYSTEM) ?: PrefKeys.THEME_SYSTEM
        } catch (e: Exception) {
            PrefKeys.THEME_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(themeToNightMode(theme))
    }

    companion object {
        /**
         * Map a stored theme value to the AppCompat night-mode constant.
         */
        fun themeToNightMode(theme: String): Int {
            return when (theme) {
                PrefKeys.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                PrefKeys.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
    }
}
