package com.fakegps.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自定義虛擬搖桿 View
 * 繪製內外圓環，監聽觸摸事件並計算偏移方向與力度。
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var hatRadius = 0f

    private var hatX = 0f
    private var hatY = 0f

    private val basePaint = Paint().apply {
        color = Color.parseColor("#88CCCCCC")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val hatPaint = Paint().apply {
        color = Color.parseColor("#AA555555")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * 回調介面，傳回向量 (方向與力度)
     * 力度範圍 0.0 到 1.0
     */
    interface JoystickListener {
        fun onJoystickMoved(angle: Double, strength: Double)
    }

    private var listener: JoystickListener? = null

    fun setJoystickListener(listener: JoystickListener) {
        this.listener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val minDimension = min(w, h)
        baseRadius = minDimension / 2.5f
        hatRadius = minDimension / 6f
        
        hatX = centerX
        hatY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 繪製外圓
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        // 繪製搖桿帽
        canvas.drawCircle(hatX, hatY, hatRadius, hatPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updatePosition(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                resetPosition()
            }
        }
        return true
    }

    private fun updatePosition(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (distance <= baseRadius) {
            hatX = touchX
            hatY = touchY
        } else {
            val ratio = baseRadius / distance
            hatX = centerX + dx * ratio
            hatY = centerY + dy * ratio
        }

        invalidate()

        // 計算角度與力度
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val strength = min(distance / baseRadius, 1f).toDouble()
        
        listener?.onJoystickMoved(angle, strength)
    }

    private fun resetPosition() {
        hatX = centerX
        hatY = centerY
        invalidate()
        listener?.onJoystickMoved(0.0, 0.0)
    }
}
