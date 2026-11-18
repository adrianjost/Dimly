package dev.adrianjost.dimly

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.widget.TextView
import dev.adrianjost.dimly.ControlPanel.Focus

/**
 * Started from the home screen. Invisible itself: it only takes the remote's keys while the
 * service shows the slider panel above the dim layer.
 *
 *  - Slider:  Left = darker, Right = brighter, Down = network control, Back/OK closes
 *  - Buttons: Left/Right choose, OK selects, Up/Back returns to the slider
 *
 * Closes by itself after a while without input. The key is hidden again every time.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }

    private var panel: ControlPanel? = null
    private var focus = Focus.SLIDER
    private var keyVisible = false

    override fun onResume() {
        super.onResume()
        val service = DimService.instance
        if (service == null) {
            showSetupHint()
        } else {
            focus = Focus.SLIDER
            keyVisible = false
            panel = service.showPanel()
            render()
        }
        restartAutoClose()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoClose)
        DimService.instance?.hidePanel()
        panel = null
        // Leaving with Home or another app: don't stay around invisibly and swallow keys later.
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        restartAutoClose()
        val service = DimService.instance ?: return super.onKeyDown(keyCode, event).also {
            if (keyCode == KeyEvent.KEYCODE_BACK) finish()
        }
        val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        when (focus) {
            Focus.SLIDER -> when {
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> service.step(DimService.STEP)
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> service.step(-DimService.STEP)
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> focus = Focus.REMOTE_TOGGLE
                keyCode == KeyEvent.KEYCODE_BACK || ok -> finish()
                else -> return super.onKeyDown(keyCode, event)
            }
            Focus.REMOTE_TOGGLE, Focus.SHOW_KEY -> when {
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_BACK -> focus = Focus.SLIDER
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> focus = Focus.REMOTE_TOGGLE
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ->
                    if (service.remoteEnabled) focus = Focus.SHOW_KEY
                ok && focus == Focus.REMOTE_TOGGLE -> {
                    service.setRemoteEnabled(!service.remoteEnabled)
                    if (!service.remoteEnabled) keyVisible = false
                }
                ok && focus == Focus.SHOW_KEY -> keyVisible = !keyVisible
                else -> return super.onKeyDown(keyCode, event)
            }
        }
        render()
        return true
    }

    private fun render() {
        val service = DimService.instance ?: return
        panel?.show(focus, service.remoteEnabled, service.key, keyVisible)
    }

    private fun restartAutoClose() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    /** Shown when the accessibility service is not enabled yet. */
    private fun showSetupHint() {
        setContentView(
            TextView(this).apply {
                setBackgroundColor(0xF0101114.toInt())
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(80, 80, 80, 80)
                text = """
                    Dimly is not enabled yet.

                    Enable it once from a computer (append it to any services already listed):
                    adb shell settings put secure enabled_accessibility_services dev.adrianjost.dimly/dev.adrianjost.dimly.DimService

                    Press BACK to close.
                """.trimIndent()
            },
        )
    }

    companion object {
        private const val AUTO_CLOSE_MS = 15_000L
    }
}
