package com.marco.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.marco.launcher.adapter.AppDrawerAdapter
import com.marco.launcher.databinding.ActivityAppDrawerBinding
import com.marco.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var prefs: LauncherPreferences

    private val allApps = mutableListOf<AppInfo>()
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make status bar transparent
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = LauncherPreferences(this)

        setupRecyclerView()
        setupSearch()
        setupClose()
        loadApps()
    }

    override fun onBackPressed() {
        finishWithAnimation()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = AppDrawerAdapter(
            onAppClick = { app -> launchApp(app) },
            onAppLongClick = { app, view -> showAppMenu(app, view) }
        )

        binding.appGrid.apply {
            layoutManager = GridLayoutManager(this@AppDrawerActivity, 4)
            adapter = this@AppDrawerActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClose() {
        binding.btnClose.setOnClickListener { finishWithAnimation() }
        binding.backdropDimmer.setOnClickListener { finishWithAnimation() }
    }

    // ── App Loading ───────────────────────────────────────────────────────────

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE

        activityScope.launch {
            val apps = withContext(Dispatchers.IO) {
                queryInstalledApps()
            }
            allApps.clear()
            allApps.addAll(apps)
            adapter.submitList(apps)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun queryInstalledApps(): List<AppInfo> {
        val pm: PackageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        return resolveInfoList
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    activityName = ri.activityInfo.name,
                    label = ri.loadLabel(pm).toString(),
                    icon = ri.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        adapter.submitList(filtered.toList())

        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── App Actions ───────────────────────────────────────────────────────────

    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent().apply {
                setClassName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
            finishWithAnimation()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppMenu(app: AppInfo, anchor: View) {
        val items = arrayOf("Add to dock", "Uninstall", "App info")
        android.app.AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> addToDock(app)
                    1 -> uninstallApp(app)
                    2 -> openAppInfo(app)
                }
            }
            .show()
    }

    private fun addToDock(app: AppInfo) {
        val current = prefs.loadDockApps().toMutableList()
        if (!current.contains(app.packageName)) {
            current.add(app.packageName)
            prefs.saveDockApps(current)
            Toast.makeText(this, "${app.label} added to dock", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "${app.label} is already in dock", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    private fun finishWithAnimation() {
        finish()
        overridePendingTransition(0, R.anim.slide_down)
    }
}
