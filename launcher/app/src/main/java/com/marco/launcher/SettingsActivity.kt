package com.marco.launcher

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.marco.launcher.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: LauncherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = LauncherPreferences(this)

        binding.btnBack.setOnClickListener { finish() }

        // Live wallpaper toggle
        binding.switchLiveWallpaper.isChecked = prefs.loadWallpaperUri()?.startsWith("live_") == true
        binding.switchLiveWallpaper.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                prefs.saveWallpaperUri("live_gradient")
                Toast.makeText(this, "Live wallpaper ON — restart launcher", Toast.LENGTH_SHORT).show()
            } else {
                prefs.saveWallpaperUri("")
                Toast.makeText(this, "Live wallpaper OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // Grid size
        binding.btnGrid4.setOnClickListener {
            prefs.saveGridSize(4)
            Toast.makeText(this, "Grid: 4 columns", Toast.LENGTH_SHORT).show()
        }
        binding.btnGrid5.setOnClickListener {
            prefs.saveGridSize(5)
            Toast.makeText(this, "Grid: 5 columns", Toast.LENGTH_SHORT).show()
        }

        // Icon size
        binding.sliderIconSize.value = prefs.loadIconSize().toFloat()
        binding.sliderIconSize.addOnChangeListener { _, value, _ ->
            prefs.saveIconSize(value.toInt())
        }
    }
}
