package com.marco.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.marco.launcher.adapter.WidgetPickerAdapter
import com.marco.launcher.databinding.ActivityWidgetPickerBinding

class WidgetPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WIDGET_PROVIDER = "extra_widget_provider"
    }

    private lateinit var binding: ActivityWidgetPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        binding = ActivityWidgetPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
            overridePendingTransition(0, R.anim.slide_down)
        }

        loadWidgets()
    }

    private fun loadWidgets() {
        val widgetManager = AppWidgetManager.getInstance(this)
        val installedWidgets: List<AppWidgetProviderInfo> =
            widgetManager.installedProviders.sortedBy { info ->
                try {
                    info.loadLabel(packageManager)
                } catch (e: Exception) {
                    info.provider.packageName
                }
            }

        val adapter = WidgetPickerAdapter(
            widgets = installedWidgets,
            pm = packageManager,
            onWidgetSelected = { providerInfo ->
                val intent = android.content.Intent().apply {
                    putExtra(EXTRA_WIDGET_PROVIDER, providerInfo)
                }
                setResult(RESULT_OK, intent)
                finish()
                overridePendingTransition(0, R.anim.slide_down)
            }
        )

        binding.widgetList.apply {
            layoutManager = LinearLayoutManager(this@WidgetPickerActivity)
            this.adapter = adapter
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(0, R.anim.slide_down)
    }
}
