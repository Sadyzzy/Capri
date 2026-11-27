package com.capri.assistant

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.io.File
import kotlin.math.abs

/** Capri flutuante sobre os outros apps: arraste para mover, toque para falar com ela. */
class Avatar(private val ctx: Context, private val onTap: () -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val d = ctx.resources.displayMetrics.density
    private val view = AvatarView()
    private var shown = false
    private val lp = WindowManager.LayoutParams(
        (96 * d).toInt(), (96 * d).toInt(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; x = (20 * d).toInt(); y = (200 * d).toInt() }
    private val clear = Runnable { view.bubble = ""; relayout(); view.invalidate() }

    fun show() {
        if (shown || !Settings.canDrawOverlays(ctx)) return
        wm.addView(view, lp); shown = true
    }

    fun hide() { if (shown) { wm.removeView(view); shown = false } }

    fun state(s: String) { view.post { view.state = s; view.invalidate() } }

    fun say(t: String) {
        Handler(Looper.getMainLooper()).post {
            view.bubble = t.take(140); relayout(); view.invalidate()
            view.removeCallbacks(clear); view.postDelayed(clear, 9000)
        }
    }

    private fun relayout() {
        val b = view.bubble.isNotEmpty()
        lp.width = ((if (b) 230 else 96) * d).toInt()
        lp.height = ((if (b) 96 + 84 else 96) * d).toInt()
        if (shown) wm.updateViewLayout(view, lp)
    }

    inner class AvatarView : View(ctx) {
        var state = "idle"
        var bubble = ""
        private var tick = 0
        private var bmp: Bitmap? = null
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13 * d }
        private val anim = object : Runnable {
            override fun run() { tick++; invalidate(); postDelayed(this, 250) }
        }
        private var dx = 0f; private var dy = 0f; private var sx = 0; private var sy = 0; private var moved = false

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            val f = File(ctx.filesDir, "capri.png")
            bmp = if (f.exists()) BitmapFactory.decodeFile(f.path)
                ?.let { Bitmap.createScaledBitmap(it, (88 * d).toInt(), (88 * d).toInt(), true) } else null
            postDelayed(anim, 250)
        }

        override fun onDetachedFromWindow() { removeCallbacks(anim); super.onDetachedFromWindow() }

        override fun onDraw(c: Canvas) {
            val col = when (state) {
                "listening" -> 0xFF05D9E8.toInt(); "thinking" -> 0xFFF9C80E.toInt(); else -> 0xFFFF2A6D.toInt()
            }
            val cx = 48 * d; val cy = 48 * d
            val b = bmp
            if (b != null) c.drawBitmap(b, 4 * d, 4 * d, null)
            else {
                p.style = Paint.Style.FILL; p.color = 0xFF2B3036.toInt(); c.drawCircle(cx, cy, 42 * d, p)
                p.color = col
                val eyeH = if (tick % 12 == 0) 1 * d else 14 * d
                for (ex in listOf(cx - 15 * d, cx + 15 * d)) c.drawOval(ex - 6 * d, cy - 8 * d, ex + 6 * d, cy - 8 * d + eyeH, p)
                val h = if (state == "talking") (3 + (tick % 2) * 9) * d else 3 * d
                c.drawRect(cx - 12 * d, cy + 18 * d, cx + 12 * d, cy + 18 * d + h, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = col
            c.drawCircle(cx, cy, 44 * d, p)
            if (bubble.isNotEmpty()) {
                val top = 98 * d
                p.style = Paint.Style.FILL; p.color = 0xEE1E2327.toInt()
                c.drawRoundRect(2 * d, top, width - 2 * d, height - 2 * d, 12 * d, 12 * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = col
                c.drawRoundRect(2 * d, top, width - 2 * d, height - 2 * d, 12 * d, 12 * d, p)
                var rest = bubble; var y = top + 22 * d; var n = 0
                while (rest.isNotEmpty() && n < 4) {
                    val k = tp.breakText(rest, true, width - 20 * d, null).coerceAtLeast(1)
                    c.drawText(rest.substring(0, k), 10 * d, y, tp)
                    rest = rest.substring(k).trimStart(); y += 17 * d; n++
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt(); val my = (e.rawY - dy).toInt()
                    if (abs(mx) > 10 || abs(my) > 10) moved = true
                    if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(this, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            return true
        }
    }
}
