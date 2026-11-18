package dev.adrianjost.dimly

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

private const val ACCENT = 0xFFFFB547.toInt()
private const val DOCK = 0xF0121418.toInt()
private const val TEXT = 0xFFF2F3F5.toInt()
private const val MUTED = 0xFFA9B0BA.toInt()
private const val BODY = 0xFFC3C8CF.toInt()
private const val SEGMENT_OFF = 0xFF3A3F47.toInt()
private const val OUTLINE = 0xFF454A52.toInt()
private const val DIVIDER = 0xFF2E3239.toInt()
private const val CARD = 0xFF1D2026.toInt()
private const val ON_ACCENT = 0xFF15171A.toInt()
private const val MONO = "monospace"

/**
 * The control dock ("design A"), shown by [DimService] as an overlay above the dim layer.
 *
 * A dark dock along the bottom edge:
 *  - top row: moon, "Dim layer", a segmented brightness bar (one segment per 5 % step), sun, percent
 *  - network section (only while focused): on/off, show key, what it is for and how to call it
 *  - key hints for the current focus
 *
 * It only draws; [MainActivity] owns the keys and tells the dock what to show.
 */
class ControlPanel(context: Context, private val ipAddress: String?, private val port: Int) {

    /** What the remote's keys currently act on. */
    enum class Focus { SLIDER, REMOTE_TOGGLE, SHOW_KEY }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).roundToInt()

    private val segments = SegmentBar(context)
    private val percent = text(context, "", 27f, TEXT, bold = true).apply {
        gravity = Gravity.END
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(IconView(context, IconView.Kind.MOON), LinearLayout.LayoutParams(dp(21f), dp(21f)))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(context, "SCREEN", 10f, MUTED).apply { letterSpacing = 0.08f })
                addView(text(context, "Dim layer", 15f, TEXT, bold = true))
            },
            LinearLayout.LayoutParams(dp(110f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16f) },
        )
        addView(segments, LinearLayout.LayoutParams(0, dp(31f), 1f).apply { marginStart = dp(8f); marginEnd = dp(16f) })
        addView(IconView(context, IconView.Kind.SUN), LinearLayout.LayoutParams(dp(21f), dp(21f)))
        addView(percent, LinearLayout.LayoutParams(dp(72f), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private val toggleLabel = text(context, "", 13f, TEXT, bold = true)
    private val toggleSwitch = SwitchView(context)
    private val toggleButton = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(15f), dp(9f), dp(15f), dp(9f))
        addView(toggleSwitch, LinearLayout.LayoutParams(dp(29f), dp(17f)))
        addView(toggleLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(9f) })
    }
    private val keyButton = text(context, "", 13f, TEXT, bold = true).apply {
        setPadding(dp(15f), dp(9f), dp(15f), dp(9f))
    }
    private val description = text(context, "", 12f, BODY).apply { setLineSpacing(0f, 1.3f) }
    private val requestValue = mono(context)
    private val addressValue = mono(context)
    private val keyValue = mono(context)
    private val detailsCard = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 10f, stroke = DIVIDER, strokeDp = 1f)
        setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        addView(detailRow(context, "Request", requestValue))
        addView(detailRow(context, "Address", addressValue), spaced())
        addView(detailRow(context, "Header", keyValue), spaced())
    }
    private val network = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(context).apply { setBackgroundColor(DIVIDER) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1f),
        ).apply { topMargin = dp(14f); bottomMargin = dp(14f) })
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(context, "NETWORK CONTROL", 10f, MUTED).apply { letterSpacing = 0.08f })
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                addView(toggleButton)
                                addView(keyButton, LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                                ).apply { marginStart = dp(9f) })
                            },
                            spaced(9f),
                        )
                        addView(description, spaced(9f))
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(21f) },
                )
                addView(detailsCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            },
        )
    }

    private val hints = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val view: View = FrameLayout(context).apply {
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(DOCK, 16f)
                elevation = dp(9f).toFloat()
                setPadding(dp(21f), dp(16f), dp(21f), dp(14f))
                addView(topRow)
                addView(network)
                addView(hints, spaced(11f))
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
            ).apply { setMargins(dp(36f), 0, dp(36f), dp(27f)) },
        )
    }

    /** Shows the darkness [level] (0..[DimService.MAX_LEVEL]) as brightness: more segments = brighter. */
    fun update(level: Int) {
        val brightness = 100 - level
        segments.filled = ((brightness - (100 - DimService.MAX_LEVEL)) / DimService.STEP.toFloat())
            .roundToInt().coerceIn(0, SegmentBar.COUNT)
        percent.text = "$brightness%"
    }

    /**
     * Shows the dock for the current [focus]. The network section only appears while it is focused;
     * the key is only written out when [keyVisible], otherwise masked so it doesn't sit on the screen.
     */
    fun show(focus: Focus, remoteEnabled: Boolean, key: String, keyVisible: Boolean) {
        val onSlider = focus == Focus.SLIDER
        segments.focused = onSlider
        topRow.alpha = if (onSlider) 1f else 0.55f
        network.visibility = if (onSlider) View.GONE else View.VISIBLE

        toggleLabel.text = if (remoteEnabled) "On" else "Off"
        toggleSwitch.on = remoteEnabled
        styleButton(toggleButton, focus == Focus.REMOTE_TOGGLE)
        toggleLabel.setTextColor(if (focus == Focus.REMOTE_TOGGLE) ON_ACCENT else TEXT)
        toggleSwitch.onAccent = focus == Focus.REMOTE_TOGGLE

        keyButton.visibility = if (remoteEnabled) View.VISIBLE else View.GONE
        keyButton.text = if (keyVisible) "Hide key" else "Show key"
        styleButton(keyButton, focus == Focus.SHOW_KEY)
        keyButton.setTextColor(if (focus == Focus.SHOW_KEY) ON_ACCENT else TEXT)

        description.text = if (remoteEnabled) {
            "Lets your home automation or a script on your home network set the darkness. " +
                "Every request must carry the key."
        } else {
            "Off: only this dock changes the darkness. Turn it on to let your home automation " +
                "or a script set it over your home network, protected by a key."
        }
        detailsCard.alpha = if (remoteEnabled) 1f else 0.4f
        requestValue.text = "POST /dim?level=0–${DimService.MAX_LEVEL}"
        addressValue.text = "http://${ipAddress ?: "?"}:$port"
        keyValue.text = "X-Key: " + if (remoteEnabled && keyVisible) key else "•".repeat(12)

        hints.removeAllViews()
        if (onSlider) {
            hint("◀  ▶", "Brightness")
            hint("▼", "Network control · " + if (remoteEnabled) "On" else "Off")
            hint("BACK", "Close", last = true)
        } else {
            hint("◀  ▶", "Choose")
            hint("OK", "Select")
            hint("▲", "Brightness", last = true)
        }
    }

    private fun hint(key: String, label: String, last: Boolean = false) {
        val context = hints.context
        hints.addView(text(context, key, 12f, TEXT, bold = true).apply {
            background = rounded(0xFF2A2E35.toInt(), 5f, stroke = OUTLINE, strokeDp = 1f)
            setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
        })
        hints.addView(
            text(context, label, 12f, MUTED),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6f); marginEnd = if (last) 0 else dp(16f) },
        )
    }

    private fun styleButton(button: View, focused: Boolean) {
        button.background = if (focused) rounded(ACCENT, 9f, stroke = ACCENT, strokeDp = 2f)
        else rounded(0xFF22262C.toInt(), 9f, stroke = OUTLINE, strokeDp = 2f)
    }

    private fun detailRow(context: Context, label: String, value: TextView) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(text(context, label, 12f, MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(value)
    }

    private fun mono(context: Context) = text(context, "", 12f, TEXT).apply { typeface = Typeface.create(MONO, Typeface.NORMAL) }

    private fun spaced(topDp: Float = 8f) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(topDp) }

    private fun rounded(color: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Float = 0f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
        if (strokeDp > 0f) setStroke(dp(strokeDp), stroke)
    }

    private fun text(context: Context, value: String, sp: Float, color: Int, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = sp
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}

/** Row of [COUNT] rounded segments; the first [filled] are lit. Outlined while [focused]. */
private class SegmentBar(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SEGMENT_OFF }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; style = Paint.Style.STROKE }
    private val rect = RectF()

    var filled = COUNT
        set(value) { field = value; invalidate() }
    var focused = true
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val border = 2f * density
        val padX = 10f * density
        val padY = 7f * density
        val gap = 4.5f * density
        if (focused) {
            frame.strokeWidth = border
            rect.set(border / 2, border / 2, width - border / 2, height - border / 2)
            canvas.drawRoundRect(rect, 10f * density, 10f * density, frame)
        }
        val w = (width - 2 * padX - gap * (COUNT - 1)) / COUNT
        val r = 3f * density
        for (i in 0 until COUNT) {
            val left = padX + i * (w + gap)
            rect.set(left, padY, left + w, height - padY)
            canvas.drawRoundRect(rect, r, r, if (i < filled) on else off)
        }
    }

    companion object {
        /** One segment per 5 % step between the darkest level and 100 %. */
        const val COUNT = DimService.MAX_LEVEL / DimService.STEP
    }
}

/** Small on/off switch. [onAccent] = drawn on an accent-coloured (focused) button. */
private class SwitchView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var on = false
        set(value) { field = value; invalidate() }
    var onAccent = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        track.color = when {
            onAccent -> ON_ACCENT
            on -> ACCENT
            else -> SEGMENT_OFF
        }
        knob.color = when {
            onAccent -> ACCENT
            on -> ON_ACCENT
            else -> MUTED
        }
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, h / 2, h / 2, track)
        val r = h / 2 - h * 0.15f
        val cx = if (on) width - h / 2 else h / 2
        canvas.drawCircle(cx, h / 2, r, knob)
    }
}

/** Outline moon (dark end) or sun (bright end). */
private class IconView(context: Context, private val kind: Kind) : View(context) {
    enum class Kind { MOON, SUN }

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        paint.strokeWidth = 1.6f * density
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) / 2f - paint.strokeWidth
        when (kind) {
            Kind.MOON -> {
                // Outer circle minus an offset circle: a crescent opening to the upper right.
                val outer = Path().apply { addCircle(cx, cy, s, Path.Direction.CW) }
                val inner = Path().apply { addCircle(cx + s * 0.55f, cy - s * 0.45f, s * 0.85f, Path.Direction.CW) }
                outer.op(inner, Path.Op.DIFFERENCE)
                canvas.drawPath(outer, paint)
            }
            Kind.SUN -> {
                canvas.drawCircle(cx, cy, s * 0.42f, paint)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = Math.cos(a).toFloat()
                    val n = Math.sin(a).toFloat()
                    canvas.drawLine(cx + c * s * 0.68f, cy + n * s * 0.68f, cx + c * s, cy + n * s, paint)
                }
            }
        }
    }
}
