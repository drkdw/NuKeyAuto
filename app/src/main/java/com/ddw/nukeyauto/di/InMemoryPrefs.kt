package com.ddw.nukeyauto.di

import android.content.SharedPreferences

/**
 * Last-resort SharedPreferences that only lives in memory. Used when the encrypted store
 * cannot be opened at all, so the app still starts instead of crashing on every launch.
 * Nothing is written to disk: the user re-enters credentials after a restart.
 */
class InMemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = synchronized(values) { values.toMap() }
    override fun getString(key: String, defValue: String?) = get(key) as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) = get(key) as Set<String>? ?: defValues
    override fun getInt(key: String, defValue: Int) = get(key) as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = get(key) as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = get(key) as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = get(key) as Boolean? ?: defValue
    override fun contains(key: String) = synchronized(values) { key in values }
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }

    private fun get(key: String) = synchronized(values) { values[key] }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            synchronized(values) {
                if (clear) values.clear()
                changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            }
            changes.keys.forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@InMemoryPrefs, key) } }
            return true
        }
    }
}
