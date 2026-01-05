package com.ipn.rastreo.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    companion object {
        const val THEME_IPN = "theme_ipn"
        const val THEME_ESCOM = "theme_escom"
        const val KEY_SELECTED_THEME = "selected_theme"
    }

    fun saveTheme(theme: String) {
        prefs.edit().putString(KEY_SELECTED_THEME, theme).apply()
    }

    fun getSavedTheme(): String {
        return prefs.getString(KEY_SELECTED_THEME, THEME_IPN) ?: THEME_IPN
    }

    fun applyTheme(theme: String) {
        when(theme) {
            THEME_IPN -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_ESCOM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}