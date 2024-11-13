package com.guzzardo.tictacdoh2

import android.content.Context

object PreferencesUtil {
    var PREFS_FILE_NAME = "Joes_Preferences"

    fun firstTimeAskingPermission(context: android.content.Context, permission: String?, isFirstTime: Boolean) {
        val sharedPreference = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        sharedPreference.edit().putBoolean(permission, isFirstTime).apply()
    }

    fun isFirstTimeAskingPermission(context: Context, permission: String?): Boolean {
        return context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE).getBoolean(permission, true)
    }
}