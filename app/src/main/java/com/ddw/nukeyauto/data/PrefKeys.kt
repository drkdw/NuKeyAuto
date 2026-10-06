package com.ddw.nukeyauto.data

import android.content.SharedPreferences

/**
 * Centralized SharedPreferences keys used across the app.
 */
object PrefKeys {
    const val NUKI_TOKEN = "nuki_token"
    const val NUKI_ID = "nuki_id"
    const val CONFIRM_ACTION = "confirm_action"
    const val OPEN_DELAY_SECONDS = "open_delay_seconds"
    const val DONATION_COUNT = "donation_count"
    const val THEME = "theme"
    const val DYNAMIC_COLORS = "dynamic_colors"

    /** Theme values stored in [THEME]. */
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
}

// Reads that survive a broken EncryptedSharedPreferences: the AndroidKeyStore key can be
// invalidated (restore, reinstall), and every getter then throws instead of returning the default.

fun SharedPreferences.safeString(key: String, default: String): String =
    try { getString(key, default) ?: default } catch (e: Exception) { default }

fun SharedPreferences.safeBoolean(key: String, default: Boolean): Boolean =
    try { getBoolean(key, default) } catch (e: Exception) { default }

fun SharedPreferences.safeInt(key: String, default: Int): Int =
    try { getInt(key, default) } catch (e: Exception) { default }
