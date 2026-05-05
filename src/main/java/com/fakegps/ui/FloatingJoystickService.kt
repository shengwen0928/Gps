package com.fakegps.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.fakegps.R
import com.fakegps.core.CoreLocationService

/**
 * 懸浮搖桿服務：僅負責顯示搖桿 UI 並將指令傳遞給 CoreLocationService
 */
class FloatingJoystickService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    
    private var currentLat = 25.0330
    private var currentLng = 121.5654

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupNotification()
        setupFloatingWindow()
    }

    private fun setupNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "floating_joystick_channel"
            val channel = NotificationChannel(channelId, "Floating Joystick", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Fake GPS 懸浮搖桿")
                .setContentText("搖桿運行中，拖動可改變位置")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
                
            startForeground(2, notification)
        }
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_joystick, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 100
        layoutParams.y = 100

        val joystick = floatingView.findViewById<JoystickView>(R.id.floatingJoystickView)
        joystick.setJoystickListener(object : JoystickView.JoystickListener {
            override fun onJoystickMoved(angle: Double, strength: Double) {
                if (strength > 0) {
                    val rad = Math.toRadians(angle)
                    currentLat += (Math.sin(rad) * 0.0001)
                    currentLng += (Math.cos(rad) * 0.0001)
                    sendCommandToCore(currentLat, currentLng)
                }
            }
        })

        floatingView.findViewById<Button>(R.id.btn_close_joystick).setOnClickListener {
            stopSelf()
        }

        // 實現拖動懸浮窗
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, layoutParams)
    }

    private fun sendCommandToCore(lat: Double, lng: Double) {
        val intent = Intent(this, CoreLocationService::class.java).apply {
            action = "UPDATE_LOCATION"
            putExtra("LAT", lat)
            putExtra("LNG", lng)
        }
        startService(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            currentLat = it.getDoubleExtra("LAT", currentLat)
            currentLng = it.getDoubleExtra("LNG", currentLng)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
