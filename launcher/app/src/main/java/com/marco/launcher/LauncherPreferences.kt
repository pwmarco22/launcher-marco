package com.marco.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class LauncherPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DOCK_APPS = "dock_apps"
        private const val KEY_WIDGET_IDS = "widget_ids"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_GRID_SIZE = "grid_size"
        private const val KEY_ICON_SIZE = "icon_size"
    }

    fun saveDockApps(packageNames: List<String>) {
        val arr = JSONArray(); packageNames.forEach { arr.put(it) }
        prefs.edit().putString(KEY_DOCK_APPS, arr.toString()).apply()
    }

    fun loadDockApps(): List<String> {
        val raw = prefs.getString(KEY_DOCK_APPS, null) ?: return emptyList()
        return try { val arr = JSONArray(raw); (0 until arr.length()).map { arr.getString(it) } }
        catch (e: Exception) { emptyList() }
    }

    fun saveWidgetIds(ids: List<Int>) {
        val arr = JSONArray(); ids.forEach { arr.put(it) }
        prefs.edit().putString(KEY_WIDGET_IDS, arr.toString()).apply()
    }

    fun loadWidgetIds(): List<Int> {
        val raw = prefs.getString(KEY_WIDGET_IDS, null) ?: return emptyList()
        return try { val arr = JSONArray(raw); (0 until arr.length()).map { arr.getInt(it) } }
        catch (e: Exception) { emptyList() }
    }

    fun saveWallpaperUri(uri: String) = prefs.edit().putString(KEY_WALLPAPER_URI, uri).apply()
    fun loadWallpaperUri(): String? = prefs.getString(KEY_WALLPAPER_URI, null)

    fun saveGridSize(size: Int) = prefs.edit().putInt(KEY_GRID_SIZE, size).apply()
    fun loadGridSize(): Int = prefs.getInt(KEY_GRID_SIZE, 4)

    fun saveIconSize(size: Int) = prefs.edit().putInt(KEY_ICON_SIZE, size).apply()
    fun loadIconSize(): Int = prefs.getInt(KEY_ICON_SIZE, 56)
}
