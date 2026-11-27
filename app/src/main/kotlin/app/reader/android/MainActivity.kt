package app.reader.android

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.reader.App
import app.reader.AppController
import app.reader.Hooks
import app.reader.ReadingOrientation
import app.reader.SettingsStore

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        SettingsStore.init(applicationContext)
        app.reader.BookStore.init(applicationContext)
        controller = AppController()
        lifecycleScope.launch { controller.loadLibrary() } // lists saved books off the main thread
        val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { openUri(it) } }
        controller.requestOpenFile = { picker.launch(arrayOf("application/epub+zip", "application/octet-stream")) }
        Hooks.setBrightness = { v ->
            window.attributes = window.attributes.also {
                it.screenBrightness = v ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        Hooks.setOrientation = { orientation ->
            requestedOrientation = when (orientation) {
                ReadingOrientation.System -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ReadingOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ReadingOrientation.Locked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
        Hooks.setNightMode = { dark -> applyNightMode(dark) }
        Hooks.share = { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            runCatching { startActivity(Intent.createChooser(send, null)) }
        }
        intent?.data?.let { openUri(it) }
        setContent {
            BackHandler(enabled = controller.back.canGoBack) { controller.back.back() }
            val systemDark = isSystemInDarkTheme()
            val settings = controller.settings
            val uiDark = settings.appearanceDark(systemDark)
            val bg = if (controller.reading != null) settings.theme.colors(uiDark).page
            else controller.homeTheme.palette(uiDark).background
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = bg.luminance() > 0.5f
                    isAppearanceLightNavigationBars = bg.luminance() > 0.5f
                }
            }
            // edge to edge: each screen keeps its own content clear of the system bars
            Box(Modifier.fillMaxSize().background(bg)) {
                App(controller, systemDark = systemDark)
            }
        }
    }

    /**
     * Asks for the display's fastest mode at the current resolution (90/120/144 Hz panels). Many phones
     * keep apps at 60 Hz unless the window asks; adaptive panels still drop the rate when nothing moves.
     */
    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        val display = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.modeId != window.attributes.preferredDisplayModeId) {
            window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        }
    }

    /**
     * Android 12+ draws the launch screen before any app code runs, in the app's night mode. Recording
     * the in-app Appearance there makes the next launch screen light or dark to match the app.
     */
    private fun applyNightMode(dark: Boolean?) {
        if (Build.VERSION.SDK_INT < 31) return
        val mode = when (dark) {
            true -> android.app.UiModeManager.MODE_NIGHT_YES
            false -> android.app.UiModeManager.MODE_NIGHT_NO
            null -> android.app.UiModeManager.MODE_NIGHT_AUTO
        }
        if (SettingsStore.get("nightMode") == mode.toString()) return
        getSystemService(android.app.UiModeManager::class.java)?.setApplicationNightMode(mode)
        SettingsStore.put("nightMode", mode.toString())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { openUri(it) }
    }

    /** Reads a picked or shared book off the main thread, then adds and opens it. */
    private fun openUri(uri: Uri) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            } ?: return@launch
            controller.open(bytes, uri.lastPathSegment?.substringAfterLast('/') ?: "book.epub")
        }
    }
}
