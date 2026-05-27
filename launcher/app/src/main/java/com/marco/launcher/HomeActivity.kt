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
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
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
    private val handler = Handler(Looper.getMainLooper())

    // Animated wallpaper colors
    private val wallpaperColors = listOf(
        intArrayOf(0xFF0f0c29.toInt(), 0xFF302b63.toInt(), 0xFF24243e.toInt()),
        intArrayOf(0xFF1a1a2e.toInt(), 0xFF16213e.toInt(), 0xFF0f3460.toInt()),
        intArrayOf(0xFF2d1b69.toInt(), 0xFF11998e.toInt(), 0xFF38ef7d.toInt()),
        intArrayOf(0xFF360033.toInt(), 0xFF0b8793.toInt(), 0xFF360033.toInt()),
        intArrayOf(0xFF000428.toInt(), 0xFF004e92.toInt(), 0xFF000428.toInt())
    )
    private var colorIndex = 0

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { loadDockApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = LauncherPreferences(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        setupGestureDetector()
        setupDock()
        setupFab()
        setupMarcoWatermark()
        startLiveWallpaper()
        loadSavedWidgets()
        loadDockApps()
        animateHomeEntrance()
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
        handler.removeCallbacksAndMessages(null)
    }

    // ── Animated Entrance ─────────────────────────────────────────────────────
    private fun animateHomeEntrance() {
        binding.clockContainer.alpha = 0f
        binding.clockContainer.translationY = -60f
        binding.clockContainer.animate()
            .alpha(1f).translationY(0f).setDuration(800)
            .setStartDelay(100).start()

        binding.dockCard.alpha = 0f
        binding.dockCard.translationY = 80f
        binding.dockCard.animate()
            .alpha(1f).translationY(0f).setDuration(700)
            .setStartDelay(300).start()

        binding.marcoWatermark.alpha = 0f
        binding.marcoWatermark.animate()
            .alpha(1f).setDuration(1200)
            .setStartDelay(600).start()
    }

    // ── Live Wallpaper Effect ─────────────────────────────────────────────────
    private fun startLiveWallpaper() {
        val wallpaperPref = prefs.loadWallpaperUri()
        if (wallpaperPref != null && wallpaperPref.startsWith("live_")) {
            animateGradientWallpaper()
        } else {
            setStaticWallpaper()
        }
    }

    private fun animateGradientWallpaper() {
        val runnable = object : Runnable {
            override fun run() {
                colorIndex = (colorIndex + 1) % wallpaperColors.size
                binding.wallpaperBackground.animate()
                    .setDuration(3000).withEndAction { run() }.start()
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)
    }

    private fun setStaticWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)
            val drawable = wm.drawable
            if (drawable != null) binding.wallpaperBackground.background = drawable
        } catch (e: Exception) { }
    }

    // ── MARCO Watermark ───────────────────────────────────────────────────────
    private fun setupMarcoWatermark() {
        binding.marcoWatermark.text = "MARCO"
        binding.marcoWatermark.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // ── Gesture ───────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                val diffY = e2.y - (e1?.y ?: e2.y)
                if (abs(diffY) > 100 && abs(vY) > 100 && diffY < 0) {
                    openAppDrawer(); return true
                }
                return false
            }
            override fun onLongPress(e: MotionEvent) { showHomeContextMenu() }
        })
        binding.root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
    }

    // ── Dock ──────────────────────────────────────────────────────────────────
    private fun setupDock() {
        dockAdapter = DockAdapter(
            apps = dockApps,
            onAppClick = { app -> launchApp(app) },
            onAppLongClick = { app, _ -> showAppContextMenu(app) }
        )
        binding.dockRecycler.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = dockAdapter
        }
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
                AppInfo(pkg, comp.className, info.loadLabel(pm).toString(), info.loadIcon(pm))
            } catch (e: Exception) { null }
        }
        if (loaded.isEmpty()) loadDefaultDockApps()
        else { dockApps.clear(); dockApps.addAll(loaded); dockAdapter.notifyDataSetChanged() }
    }

    private fun loadDefaultDockApps() {
        val defaults = listOf("com.android.dialer", "com.android.settings", "com.android.chrome")
        val pm = packageManager
        val loaded = defaults.mapNotNull { pkg ->
            try {
                val intent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                val comp = intent.component ?: return@mapNotNull null
                val info = pm.getActivityInfo(comp, 0)
                AppInfo(pkg, comp.className, info.loadLabel(pm).toString(), info.loadIcon(pm))
            } catch (e: Exception) { null }
        }
        dockApps.clear(); dockApps.addAll(loaded); dockAdapter.notifyDataSetChanged()
    }

    // ── Widgets ───────────────────────────────────────────────────────────────
    private fun loadSavedWidgets() {
        prefs.loadWidgetIds().forEach { widgetId ->
            val info = appWidgetManager.getAppWidgetInfo(widgetId)
            if (info != null) addWidgetToScreen(widgetId, info)
            else appWidgetHost.deleteAppWidgetId(widgetId)
        }
    }

    private fun addWidgetToScreen(widgetId: Int, info: AppWidgetProviderInfo) {
        val hostView: AppWidgetHostView = appWidgetHost.createView(this, widgetId, info)
        hostView.setAppWidget(widgetId, info)
        val params = FrameLayout.LayoutParams(
            info.minWidth.coerceAtLeast(200), info.minHeight.coerceAtLeast(100)
        ).apply { topMargin = 16 }
        binding.widgetContainer.addView(hostView, params)
        hostView.tag = widgetId
    }

    private fun removeWidget(widgetId: Int) {
        binding.widgetContainer.findViewWithTag<View>(widgetId)?.let {
            binding.widgetContainer.removeView(it)
        }
        appWidgetHost.deleteAppWidgetId(widgetId)
        val current = prefs.loadWidgetIds().toMutableList()
        current.remove(widgetId); prefs.saveWidgetIds(current)
    }

    private fun openWidgetPicker() {
        startActivityForResult(Intent(this, WidgetPickerActivity::class.java), REQUEST_PICK_WIDGET)
        overridePendingTransition(R.anim.slide_up, 0)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_WIDGET -> {
                if (resultCode == RESULT_OK) {
                    val info = data?.getParcelableExtra<AppWidgetProviderInfo>(
                        WidgetPickerActivity.EXTRA_WIDGET_PROVIDER) ?: return
                    val widgetId = appWidgetHost.allocateAppWidgetId()
                    val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, info.provider)
                    if (bound) finishWidgetBind(widgetId, info)
                    else {
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_BIND_WIDGET)
                    }
                }
            }
            REQUEST_BIND_WIDGET -> {
                val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (resultCode == RESULT_OK && widgetId != -1) {
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info != null) finishWidgetBind(widgetId, info)
                } else if (widgetId != -1) appWidgetHost.deleteAppWidgetId(widgetId)
            }
            REQUEST_CONFIGURE_WIDGET -> {
                val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (resultCode == RESULT_OK && widgetId != -1) {
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info != null) persistAndShowWidget(widgetId, info)
                } else if (widgetId != -1) appWidgetHost.deleteAppWidgetId(widgetId)
            }
        }
    }

    private fun finishWidgetBind(widgetId: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET)
        } else persistAndShowWidget(widgetId, info)
    }

    private fun persistAndShowWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val current = prefs.loadWidgetIds().toMutableList()
        if (!current.contains(widgetId)) { current.add(widgetId); prefs.saveWidgetIds(current) }
        addWidgetToScreen(widgetId, info)
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    private fun openAppDrawer() {
        startActivity(Intent(this, AppDrawerActivity::class.java))
        overridePendingTransition(R.anim.slide_up, 0)
    }

    private fun launchApp(app: AppInfo) {
        try {
            startActivity(Intent().apply {
                setClassName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Context Menus ─────────────────────────────────────────────────────────
    private fun showAppContextMenu(app: AppInfo) {
        android.app.AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle(app.label)
            .setItems(arrayOf("Remove from dock", "App info")) { _, which ->
                when (which) {
                    0 -> { dockApps.remove(app); dockAdapter.notifyDataSetChanged()
                        prefs.saveDockApps(dockApps.map { it.packageName }) }
                    1 -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${app.packageName}") })
                }
            }.show()
    }

    private fun showHomeContextMenu() {
        val items = mutableListOf("Add widget", "Change wallpaper", "Settings")
        if (prefs.loadWidgetIds().isNotEmpty()) items.add("Remove last widget")
        android.app.AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("MARCO Launcher")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Add widget" -> openWidgetPicker()
                    "Change wallpaper" -> pickWallpaper()
                    "Settings" -> startActivity(Intent(this, SettingsActivity::class.java))
                    "Remove last widget" -> removeWidget(prefs.loadWidgetIds().last())
                }
            }.show()
    }

    private fun pickWallpaper() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose Wallpaper"))
    }

    override fun onBackPressed() { }
}
