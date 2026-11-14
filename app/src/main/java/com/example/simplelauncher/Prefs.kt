package com.example.simplelauncher

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun get(key: String): List<String> =
        (sp.getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }

    fun set(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString(",")).apply()
    }

    companion object {
        const val TILES = "tiles"
        const val TASKBAR = "taskbar"
    }
}
