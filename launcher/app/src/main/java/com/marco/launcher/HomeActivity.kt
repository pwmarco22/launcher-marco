package com.marco.launcher

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.marco.launcher.adapter.DockAdapter
import com.marco.launcher.databinding.ActivityHomeBinding
import com.marco.launcher.model.AppInfo
import kotlin.math.abs

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val APPWIDGET_HOST_ID = 1024
        private const val REQUEST_PICK_WIDGET = 2001
        private const val REQUEST_BIND_WIDGET = 2002
        private const val REQUEST_CONFIGURE_WIDGET = 2003
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var prefs: LauncherPreferences
    private lateinit var dockAdapter: DockAdapter
    private lateinit var gestureDetector: GestureDetector

    private val dockApps = mutableListOf<AppInfo>()

    // ── Package change receiver ───────────────────────────────────────────────
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadDockApps()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = LauncherPreferences(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        setupGestureDetector()
        setupDock()
        setupClockDisplay()
        setupFab()
        loadSavedWidgets()
        loadDockApps()
        setWallpaperBackground()
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
        unregisterReceiver(packageReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffY = (e2.y - (e1?.y ?: e2.y))
                if (abs(diffY) > SWIPE_THRESHOLD &&
                    abs(velocityY) > SWIPE_VELOCITY_THRESHOLD &&
                    diffY < 0
                ) {
                    openAppDrawer()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                showHomeContextMenu()
            }
        })

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun setupDock() {
        dockAdapter = DockAdapter(
            apps = dockApps,
            onAppClick = { app -> launchApp(app) },
            onAppLongClick = { app, _ -> showAppContextMenu(app) }
        )
        binding.dockRecycler.apply {
            layoutManager = LinearLayoutManager(
                this@HomeActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = dockAdapter
        }
    }

    private fun setupClockDisplay() {
        // Clock updates are handled by the XML TextClock widget automatically
    }

    private fun setupFab() {
        binding.fabDrawer.setOnClickListener { openAppDrawer() }
        binding.fabWidgets.setOnClickListener { openWidgetPicker() }
    }

    // ── App Loading ───────────────────────────────────────────────────────────

    private fun loadDockApps() {
        val savedPackages = prefs.loadDockApps()
        val pm = packageManager

        val loaded = savedPackages.mapNotNull { pkg ->
            try {
                val intent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                val comp = intent.component ?: return@mapNotNull null
                val info = pm.getActivityInfo(comp, 0)
                AppInfo(
                    packageName = pkg,
                    activityName = comp.className,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        // If no dock apps saved, use defaults
        if (loaded.isEmpty()) {
            loadDefaultDockApps()
        } else {
            dockApps.clear()
            dockApps.addAll(loaded)
            dockAdapter.notifyDataSetChanged()
        }
    }

    private fun loadDefaultDockApps() {
        val defaultPackages = listOf(
            "com.android.dialer",
            "com.android.contacts",
            "com.android.messaging",
            "com.android.chrome",
            "com.android.settings"
        )
        val pm = packageManager
        val loaded = defaultPackages.mapNotNull { pkg ->
            try {
                val intent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                val comp = intent.component ?: return@mapNotNull null
                val info = pm.getActivityInfo(comp, 0)
                AppInfo(
                    packageName = pkg,
                    activityName = comp.className,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm)
                )
            } catch (e: Exception) {
                null
            }
        }
        dockApps.clear()
        dockApps.addAll(loaded)
        dockAdapter.notifyDataSetChanged()
    }

    // ── Widget Host ───────────────────────────────────────────────────────────

    private fun loadSavedWidgets() {
        val ids = prefs.loadWidgetIds()
        ids.forEach { widgetId ->
            val providerInfo = appWidgetManager.getAppWidgetInfo(widgetId)
            if (providerInfo != null) {
                addWidgetToScreen(widgetId, providerInfo)
            } else {
                // Widget no longer valid, clean up
                appWidgetHost.deleteAppWidgetId(widgetId)
            }
        }
    }

    private fun addWidgetToScreen(widgetId: Int, providerInfo: AppWidgetProviderInfo) {
        val hostView: AppWidgetHostView = appWidgetHost.createView(this, widgetId, providerInfo)
        hostView.setAppWidget(widgetId, providerInfo)

        val minWidth = providerInfo.minWidth.coerceAtLeast(200)
        val minHeight = providerInfo.minHeight.coerceAtLeast(100)

        val params = FrameLayout.LayoutParams(minWidth, minHeight).apply {
            topMargin = 16
        }

        binding.widgetContainer.addView(hostView, params)
        hostView.tag = widgetId
    }

    private fun removeWidget(widgetId: Int) {
        val view = binding.widgetContainer.findViewWithTag<View>(widgetId)
        if (view != null) {
            binding.widgetContainer.removeView(view)
        }
        appWidgetHost.deleteAppWidgetId(widgetId)

        // Update persisted list
        val current = prefs.loadWidgetIds().toMutableList()
        current.remove(widgetId)
        prefs.saveWidgetIds(current)
    }

    // ── Widget Picker Flow ────────────────────────────────────────────────────

    private fun openWidgetPicker() {
        val intent = Intent(this, WidgetPickerActivity::class.java)
        startActivityForResult(intent, REQUEST_PICK_WIDGET)
        overridePendingTransition(R.anim.slide_up, 0)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_PICK_WIDGET -> {
                if (resultCode == RESULT_OK) {
                    val providerInfo = data?.getParcelableExtra<AppWidgetProviderInfo>(
                        WidgetPickerActivity.EXTRA_WIDGET_PROVIDER
                    ) ?: return

                    val widgetId = appWidgetHost.allocateAppWidgetId()
                    val bound = appWidgetManager.bindAppWidgetIdIfAllowed(
                        widgetId, providerInfo.provider
                    )

                    if (bound) {
                        finishWidgetBind(widgetId, providerInfo)
                    } else {
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_BIND_WIDGET)
                    }
                }
            }

            REQUEST_BIND_WIDGET -> {
                val widgetId = data?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, -1
                ) ?: -1
                if (resultCode == RESULT_OK && widgetId != -1) {
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info != null) finishWidgetBind(widgetId, info)
                } else if (widgetId != -1) {
                    appWidgetHost.deleteAppWidgetId(widgetId)
                }
            }

            REQUEST_CONFIGURE_WIDGET -> {
                val widgetId = data?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, -1
                ) ?: -1
                if (resultCode == RESULT_OK && widgetId != -1) {
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info != null) persistAndShowWidget(widgetId, info)
                } else if (widgetId != -1) {
                    appWidgetHost.deleteAppWidgetId(widgetId)
                }
            }
        }
    }

    private fun finishWidgetBind(widgetId: Int, providerInfo: AppWidgetProviderInfo) {
        if (providerInfo.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET)
        } else {
            persistAndShowWidget(widgetId, providerInfo)
        }
    }

    private fun persistAndShowWidget(widgetId: Int, providerInfo: AppWidgetProviderInfo) {
        val current = prefs.loadWidgetIds().toMutableList()
        if (!current.contains(widgetId)) {
            current.add(widgetId)
            prefs.saveWidgetIds(current)
        }
        addWidgetToScreen(widgetId, providerInfo)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun openAppDrawer() {
        val intent = Intent(this, AppDrawerActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_up, 0)
    }

    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent().apply {
                setClassName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Context Menus ─────────────────────────────────────────────────────────

    private fun showAppContextMenu(app: AppInfo) {
        val items = arrayOf("Remove from dock", "App info")
        android.app.AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> removeFromDock(app)
                    1 -> openAppInfo(app)
                }
            }
            .show()
    }

    private fun showHomeContextMenu() {
        val widgetIds = prefs.loadWidgetIds()
        val items = mutableListOf("Add widget", "Change wallpaper")

        if (widgetIds.isNotEmpty()) {
            items.add("Remove last widget")
        }

        android.app.AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Home Screen")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Add widget" -> openWidgetPicker()
                    "Change wallpaper" -> pickWallpaper()
                    "Remove last widget" -> {
                        val lastId = widgetIds.last()
                        removeWidget(lastId)
                    }
                }
            }
            .show()
    }

    private fun removeFromDock(app: AppInfo) {
        dockApps.remove(app)
        dockAdapter.notifyDataSetChanged()
        prefs.saveDockApps(dockApps.map { it.packageName })
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    // ── Wallpaper ─────────────────────────────────────────────────────────────

    private fun setWallpaperBackground() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val drawable = wallpaperManager.drawable
            if (drawable != null) {
                binding.wallpaperBackground.background = drawable
            }
        } catch (e: Exception) {
            // Permission not granted, keep default background
        }
    }

    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivity(Intent.createChooser(intent, "Choose Wallpaper"))
    }

    override fun onBackPressed() {
        // Do nothing on back press (launcher behavior)
    }
}
