package com.fakegps.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.fakegps.R
import com.fakegps.core.MockLocationManager
import com.fakegps.engine.MovementEngine

class FloatingJoystickService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var movementEngine: MovementEngine
    
    // 儲存當前位置，這裡先預設一個，實際應用可由 Intent 傳入
    private var currentLat = 25.0330
    private var currentLng = 121.5654

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("fakegps_channel", "Fake GPS Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            
            val notification: Notification = NotificationCompat.Builder(this, "fakegps_channel")
                .setContentTitle("Fake GPS 懸浮搖桿")
                .setContentText("正在背景運行中...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
                
            startForeground(1, notification)
        }

        mockLocationManager = MockLocationManager(this)
        mockLocationManager.setupMockProvider()
        movementEngine = MovementEngine()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_joystick, null)

        val joystick = floatingView.findViewById<JoystickView>(R.id.floatingJoystickView)
        val btnClose = floatingView.findViewById<View>(R.id.btn_close_joystick)

        joystick.setJoystickListener(object : JoystickView.JoystickListener {
            override fun onJoystickMoved(angle: Double, strength: Double) {
                if (strength > 0) {
                    val rad = Math.toRadians(angle)
                    // 力度 0.0-1.0 對應位移量
                    // 修正：0度為東(cos), 90度為北(sin)
                    currentLat += (Math.sin(rad) * 0.0001)
                    currentLng += (Math.cos(rad) * 0.0001)
                    
                    val jittered = movementEngine.applyGaussianJitter(currentLat, currentLng)
                    mockLocationManager.setMockLocation(jittered.first, jittered.second, 0.0)
                }
            }
        })

        btnClose.setOnClickListener {
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            currentLat = it.getDoubleExtra("LAT", 25.0330)
            currentLng = it.getDoubleExtra("LNG", 121.5654)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        mockLocationManager.removeMockProvider()
    }
}
