package com.aikipunk.betteraudiobookplayer.testutil

import android.content.SharedPreferences

class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    inner class Editor : SharedPreferences.Editor {
        private val writes = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String?, value: String?) = apply { if (key != null) writes[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) writes[key] = values?.toSet() }
        override fun putInt(key: String?, value: Int) = apply { if (key != null) writes[key] = value }
        override fun putLong(key: String?, value: Long) = apply { if (key != null) writes[key] = value }
        override fun putFloat(key: String?, value: Float) = apply { if (key != null) writes[key] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) writes[key] = value }
        override fun remove(key: String?) = apply { if (key != null) removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            removals.forEach(values::remove)
            values.putAll(writes)
        }
    }
}
