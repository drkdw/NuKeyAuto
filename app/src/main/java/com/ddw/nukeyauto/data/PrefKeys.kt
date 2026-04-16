package com.ddw.nukeyauto.data

/**
 * Centralized SharedPreferences keys used across the app.
 */
object PrefKeys {
    const val NUKI_TOKEN = "nuki_token"
    const val NUKI_ID = "nuki_id"
    const val CONFIRM_ACTION = "confirm_action"
    const val OPEN_DELAY_SECONDS = "open_delay_seconds"
    const val IS_SUPPORTER = "is_supporter"
    const val DONATION_COUNT = "donation_count"
    const val THEME = "theme"
    const val DYNAMIC_COLORS = "dynamic_colors"

    /** Theme values stored in [THEME]. */
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
}
