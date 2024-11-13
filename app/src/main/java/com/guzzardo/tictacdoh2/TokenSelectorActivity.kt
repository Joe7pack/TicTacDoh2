package com.guzzardo.tictacdoh2

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.widget.FrameLayout

class TokenSelectorActivity : android.app.Activity() {
    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.token_selector)
        findViewById<android.view.View>(R.id.start_x).setOnClickListener {
            setResult(GameView.BoardSpaceValues.CROSS)
            finish()
        }
        findViewById<android.view.View>(R.id.start_o).setOnClickListener {
            setResult(GameView.BoardSpaceValues.CIRCLE)
            finish()
        }
    }
}

class TokenColorPickerView(
    context: android.content.Context?,
    private val mSettingsDialogs: SettingsDialogs,
    color: Int
    ) : android.view.View(context) {
    interface OnColorChangedListener {
        fun colorChanged(color: Int)
    }

    private val mPaint: android.graphics.Paint
    private val mCenterPaint: android.graphics.Paint
    private val mColors: IntArray
    private var mTrackingCenter = false
    private var mHighlightCenter = false
    override fun onDraw(canvas: android.graphics.Canvas) {
        val r = CENTER_X - mPaint.strokeWidth * 0.5f
        val frameLayout = this.parent as FrameLayout
        val frameWidth = frameLayout.measuredWidth
        val leftOffset = frameWidth.toDouble() / 2.0 - 100
        this.left = leftOffset.toInt()
        this.right = 2 * leftOffset.toInt()
        canvas.translate(
            CENTER_X.toFloat(),
            CENTER_X.toFloat()
        )
        canvas.drawOval(RectF(-r, -r, r, r), mPaint)
        canvas.drawCircle(0f, 0f, CENTER_RADIUS.toFloat(), mCenterPaint)
        if (mTrackingCenter) {
            val c = mCenterPaint.color
            mCenterPaint.style = Paint.Style.STROKE
            if (mHighlightCenter) {
                mCenterPaint.alpha = 0xFF
            } else {
                mCenterPaint.alpha = 0x80
            }
            canvas.drawCircle(0f, 0f, CENTER_RADIUS + mCenterPaint.strokeWidth, mCenterPaint)
            mCenterPaint.style = Paint.Style.FILL
            mCenterPaint.color = c
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(CENTER_X * 2, CENTER_Y * 2)
    }

    private fun ave(s: Int, d: Int, p: Float): Int {
        return s + Math.round(p * (d - s))
    }

    private fun interpColor(colors: IntArray, unit: Float): Int {
        if (unit <= 0) {
            return colors[0]
        }
        if (unit >= 1) {
            return colors[colors.size - 1]
        }
        var p = unit * (colors.size - 1)
        val i = p.toInt()
        p -= i.toFloat()

        // now p is just the fractional part [0...1) and i is the index
        val c0 = colors[i]
        val c1 = colors[i + 1]
        val a = ave(Color.alpha(c0), Color.alpha(c1), p)
        val r = ave(Color.red(c0), Color.red(c1), p)
        val g = ave(Color.green(c0), Color.green(c1), p)
        val b = ave(Color.blue(c0), Color.blue(c1), p)
        return Color.argb(a, r, g, b)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x - CENTER_X
        val y = event.y - CENTER_Y
        val inCenter =  Math.sqrt(x * x + y * y.toDouble()) <= CENTER_RADIUS

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTrackingCenter = inCenter
                if (inCenter) {
                    mHighlightCenter = true
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mTrackingCenter) {
                    if (mHighlightCenter != inCenter) {
                        mHighlightCenter = inCenter
                        invalidate()
                    }
                } else {
                    val angle = Math.atan2(y.toDouble(), x.toDouble()).toFloat()
                    var unit = angle / (2 * PI)
                    if (unit < 0) {
                        unit += 1f
                    }
                    mCenterPaint.color = interpColor(mColors, unit)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mCenterPaint.color != 0) {
                    mSettingsDialogs.setTokenColorFromDialog(mCenterPaint.color)
                }
                invalidate()
            }
        }
        return true
    }

    companion object {
        private const val CENTER_X = 100
        private const val CENTER_Y = 100
        private const val CENTER_RADIUS = 32
        private const val PI = 3.1415926f
    }

    init {
        mColors = intArrayOf(
            -0x10000, -0xff01, -0xffff01, -0xff0001, -0xff0100,
            -0x100, -0x10000
        )
        val s: Shader = SweepGradient(0F, 0F, mColors, null)
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.shader = s
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = 32f
        mCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mCenterPaint.color = color
        mCenterPaint.strokeWidth = 5f

    }
}