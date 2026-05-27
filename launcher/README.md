# Marco Launcher — Android Kotlin

A fully-featured Android home screen launcher built in Kotlin with:

- **RecyclerView App Drawer** (Grid layout, search, long-press context menu)
- **Android Widget Host** (Add, remove, configure, persist widgets)
- **Glassmorphic Dock** with horizontal RecyclerView
- **Swipe-up gesture** to open drawer
- **Clock / Date** overlay (TextClock)
- **SharedPreferences** persistence for dock apps + widget IDs
- **Broadcast receiver** for package changes and boot

---

## Project Structure

```
app/src/main/
├── java/com/marco/launcher/
│   ├── HomeActivity.kt           # Launcher home screen, widget host
│   ├── AppDrawerActivity.kt      # Full-screen app grid + search
│   ├── WidgetPickerActivity.kt   # Widget list picker
│   ├── LauncherPreferences.kt    # SharedPrefs helpers
│   ├── adapter/
│   │   ├── AppDrawerAdapter.kt   # ListAdapter<AppInfo> for grid
│   │   ├── DockAdapter.kt        # RecyclerView adapter for dock
│   │   └── WidgetPickerAdapter.kt# Widget list adapter
│   ├── model/
│   │   ├── AppInfo.kt
│   │   └── WidgetInfo.kt
│   └── receiver/
│       └── BootReceiver.kt       # Re-launch on device boot
├── res/
│   ├── layout/
│   │   ├── activity_home.xml
│   │   ├── activity_app_drawer.xml
│   │   ├── activity_widget_picker.xml
│   │   ├── item_app.xml
│   │   ├── item_dock_app.xml
│   │   └── item_widget_picker.xml
│   ├── drawable/          # Vector icons + shape backgrounds
│   ├── anim/              # slide_up / slide_down transitions
│   └── values/            # themes, colors, strings
```

---

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1+ |
| Kotlin | 1.9.22 |
| compileSdk | 34 |
| minSdk | 26 (Android 8.0) |

---

## Setup

1. **Open** the project in Android Studio: `File → Open → /path/to/launcher/`
2. Let Gradle sync complete
3. **Run** on a device or emulator (API 26+)
4. On device: Go to **Settings → Apps → Default Apps → Home App** → select **Marco Launcher**

---

## Key Features In Detail

### App Drawer (AppDrawerActivity)
```kotlin
// Uses ListAdapter with DiffUtil for efficient diffing
class AppDrawerAdapter : ListAdapter<AppInfo, AppViewHolder>(APP_DIFF)

// Loads apps off-thread via coroutines
activityScope.launch {
    val apps = withContext(Dispatchers.IO) { queryInstalledApps() }
    adapter.submitList(apps)
}
```

The drawer opens with a `slide_up` animation on swipe-up or FAB tap.
A real-time search bar filters `allApps` list on every keystroke.

### Widget Host (HomeActivity)
```kotlin
// Allocate a new widget ID
val widgetId = appWidgetHost.allocateAppWidgetId()

// Bind and optionally configure
val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, providerInfo.provider)
if (providerInfo.configure != null) {
    // Launch configuration activity
} else {
    persistAndShowWidget(widgetId, providerInfo)
}

// Add to layout
val hostView = appWidgetHost.createView(this, widgetId, providerInfo)
binding.widgetContainer.addView(hostView, params)
```

Widget IDs are persisted in SharedPreferences and restored on every `onCreate`.

### Dock
The dock is a horizontal `RecyclerView` in a glassmorphic card at the bottom.
Long-press → context menu → "Remove from dock" or "App info".
Tap FAB to open App Drawer. The dock persists via `LauncherPreferences.saveDockApps()`.

### Gesture Detection
```kotlin
gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
    override fun onFling(e1, e2, velocityX, velocityY): Boolean {
        // Swipe up → open drawer
        if (diffY < -SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD)
            openAppDrawer()
    }
    override fun onLongPress(e: MotionEvent) {
        showHomeContextMenu() // Add widget / change wallpaper
    }
})
```

---

## Extending

### Add Icon Pack Support
In `AppDrawerAdapter`, swap `app.icon` with a lookup in your icon pack's resources by package name.

### Drag & Drop Dock Reordering
Attach an `ItemTouchHelper` with a `SimpleCallback` to `binding.dockRecycler` and swap items in `onMove`.

### Multiple Home Screen Pages
Replace `ScrollView` in `activity_home.xml` with `ViewPager2` and create a `HomePageAdapter` that holds multiple `FrameLayout` pages for widgets.

### Notification Badges
Use `NotificationListenerService` to count unread notifications per package and overlay a badge on app icons.

---

## Permissions

| Permission | Use |
|---|---|
| `BIND_APPWIDGET` | Host widgets on the home screen |
| `EXPAND_STATUS_BAR` | Allow status bar expansion from home |
| `SET_WALLPAPER` | Wallpaper picker integration |
| `RECEIVE_BOOT_COMPLETED` | Re-launch as home after reboot |

---

## License
MIT — free to use, modify, and redistribute.
