package com.marco.launcher.adapter

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.marco.launcher.R

class WidgetPickerAdapter(
    private val widgets: List<AppWidgetProviderInfo>,
    private val pm: PackageManager,
    private val onWidgetSelected: (AppWidgetProviderInfo) -> Unit
) : RecyclerView.Adapter<WidgetPickerAdapter.WidgetViewHolder>() {

    inner class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.widget_icon)
        val label: TextView = itemView.findViewById(R.id.widget_label)
        val description: TextView = itemView.findViewById(R.id.widget_description)

        fun bind(info: AppWidgetProviderInfo) {
            val appLabel = try {
                pm.getApplicationInfo(info.provider.packageName, 0)
                    .loadLabel(pm).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                info.provider.packageName
            }

            val widgetLabel = try {
                info.loadLabel(pm)
            } catch (e: Exception) {
                appLabel
            }

            label.text = widgetLabel
            description.text = appLabel

            try {
                val drawable = info.loadIcon(itemView.context, 1)
                icon.setImageDrawable(drawable)
            } catch (e: Exception) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            itemView.setOnClickListener { onWidgetSelected(info) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_widget_picker, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        holder.bind(widgets[position])
    }

    override fun getItemCount() = widgets.size
}
