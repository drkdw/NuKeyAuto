package com.ddw.nukeyauto

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.safeBoolean
import com.ddw.nukeyauto.data.safeString
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NukiApp : Application() {

    @Inject lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        applyStoredTheme()
        // Android 12+ Material You dynamic color, no-op on older versions. The precondition
        // is checked for every new activity, so the setting applies without an app restart.
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> prefs.safeBoolean(PrefKeys.DYNAMIC_COLORS, PrefKeys.DYNAMIC_COLORS_DEFAULT) }
                .build()
        )
    }

    /**
     * Apply the user's stored theme preference (light/dark/system).
     * Falls back to system default if the pref cannot be read.
     */
    private fun applyStoredTheme() {
        val theme = prefs.safeString(PrefKeys.THEME, PrefKeys.THEME_SYSTEM)
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
