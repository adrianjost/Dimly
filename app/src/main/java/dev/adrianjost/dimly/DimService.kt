package dev.adrianjost.dimly

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import java.security.SecureRandom

/**
 * Keeps a full-screen black layer above everything and fades its opacity.
 *
 * The level (0 = off, [MAX_LEVEL] = darkest) comes from the slider panel or, when network control
 * is switched on, from [DimServer]. It is stored, so the screen keeps its darkness across restarts;
 * the slider is always there to brighten it again.
 *
 * Runs as an accessibility service because that keeps it alive in the background, restarts it
 * after a reboot and lets it draw over other apps without a separate permission.
 */
class DimService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var windows: WindowManager

    private var dimView: View? = null
    private var panel: ControlPanel? = null
    private var server: DimServer? = null

    /** Current darkness, 0..[MAX_LEVEL]. Read from any thread. */
    @Volatile
    var level: Int = 0
        private set

    val key: String get() = prefs.key
    val remoteEnabled: Boolean get() = prefs.remoteEnabled

    override fun onServiceConnected() {
        prefs = Prefs(this)
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        level = prefs.level
        addDimWindow()
        applyLevel()
        if (prefs.remoteEnabled) startServer()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        stopServer()
        hidePanel()
        dimView?.let { runCatching { windows.removeView(it) } }
        dimView = null
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /**
     * Sets the darkness (clamped to 0..[MAX_LEVEL]) and returns the value that was applied. Any thread.
     * The layer fades to the new value over [fadeMs]; 0 switches at once.
     */
    fun setLevel(value: Int, fadeMs: Long = FADE_MS): Int {
        val clamped = value.coerceIn(0, MAX_LEVEL)
        level = clamped
        prefs.level = clamped
        main.post { applyLevel(fadeMs) }
        return clamped
    }

    /** Called by the panel's Left/Right keys: positive = darker. Short fade so it follows the key. */
    fun step(delta: Int) {
        setLevel(level + delta, STEP_FADE_MS)
    }

    /** Switches the web server on or off; off by default until the user enables it in the panel. */
    fun setRemoteEnabled(enabled: Boolean) {
        prefs.remoteEnabled = enabled
        if (enabled) startServer() else stopServer()
    }

    fun showPanel(): ControlPanel {
        panel?.let { return it }
        return ControlPanel(this, ipAddress = localIp(), port = PORT).also {
            windows.addView(it.view, overlayParams())
            it.update(level)
            panel = it
        }
    }

    fun hidePanel() {
        panel?.let { runCatching { windows.removeView(it.view) } }
        panel = null
    }

    private fun startServer() {
        if (server != null) return
        server = DimServer(PORT, this).also {
            runCatching { it.start(5000, false) }.onFailure { e -> Log.e(TAG, "Web server failed to start", e) }
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun addDimWindow() {
        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
        }
        windows.addView(view, overlayParams())
        dimView = view
    }

    /** Fades the layer to the current level, starting from wherever a running fade has got to. */
    private fun applyLevel(fadeMs: Long = 0) {
        panel?.update(level)
        val view = dimView ?: return
        val target = level / 100f
        view.animate().cancel()
        if (target > 0f && view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
        }
        if (fadeMs <= 0 || view.visibility != View.VISIBLE) {
            view.alpha = target
            if (target == 0f) view.visibility = View.GONE
            return
        }
        view.animate()
            .alpha(target)
            .setDuration(fadeMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (level == 0) view.visibility = View.GONE }
            .start()
    }

    /** Full-screen accessibility overlay that never takes focus or input, so the remote keeps working. */
    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    companion object {
        private const val TAG = "Dimly"
        const val PORT = 8099

        /** Never fully black, so the slider and the picture stay findable. */
        const val MAX_LEVEL = 95

        /** Slider step per Left/Right press. */
        const val STEP = 5

        /** Default fade for network requests; a request can pass its own (`fade` in ms). */
        const val FADE_MS = 800L

        /** Fade for slider steps, short so holding a key still feels direct. */
        const val STEP_FADE_MS = 150L

        /** Upper limit for a requested fade. */
        const val MAX_FADE_MS = 10_000L

        @Volatile
        var instance: DimService? = null
            private set
    }
}

/** Stored settings: the current level, whether network control is on, and its key. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("dimly", Context.MODE_PRIVATE)

    var level: Int
        get() = sp.getInt("level", 0)
        set(value) = sp.edit().putInt("level", value).apply()

    /** Off until the user switches it on in the panel. */
    var remoteEnabled: Boolean
        get() = sp.getBoolean("remote_enabled", false)
        set(value) = sp.edit().putBoolean("remote_enabled", value).apply()

    /**
     * Created once on first use; stays the same when network control is switched off and on.
     * Can be replaced with your own from a computer, see [SetKeyReceiver].
     */
    var key: String
        get() = sp.getString("key", null) ?: newKey().also { sp.edit().putString("key", it).apply() }
        set(value) = sp.edit().putString("key", value).apply()

    private fun newKey(): String {
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
