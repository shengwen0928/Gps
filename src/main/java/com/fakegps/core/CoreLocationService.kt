package com.fakegps.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fakegps.engine.AltitudeEngine
import com.fakegps.engine.BehaviorEngine
import com.fakegps.engine.MovementEngine
import com.fakegps.map.RoutePlanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 核心位置服務：負責管理所有的模擬位置邏輯、擬真引擎運算以及背景執行。
 */
class CoreLocationService : LifecycleService() {

    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var movementEngine: MovementEngine
    private lateinit var routePlanner: RoutePlanner
    private lateinit var behaviorEngine: BehaviorEngine
    private lateinit var altitudeEngine: AltitudeEngine
    private var wakeLock: PowerManager.WakeLock? = null

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>>(Pair(25.0330, 121.5654))
    val currentLocation = _currentLocation.asStateFlow()

    private val _mockProviderStatus = MutableStateFlow(true)
    val mockProviderStatus = _mockProviderStatus.asStateFlow()

    private val _isAutoWalking = MutableStateFlow(false)
    val isAutoWalking = _isAutoWalking.asStateFlow()

    private var autoWalkJob: Job? = null
    private var currentPath = listOf<Pair<Double, Double>>()
    private var currentIndex = 0

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): CoreLocationService = this@CoreLocationService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        mockLocationManager = MockLocationManager(this)
        movementEngine = MovementEngine()
        routePlanner = RoutePlanner(movementEngine)
        behaviorEngine = BehaviorEngine()
        altitudeEngine = AltitudeEngine()
        
        setupWakeLock()
        _mockProviderStatus.value = mockLocationManager.setupMockProvider()
        startForegroundService()
    }

    private fun setupWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FakeGPS::MovementLock")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundService() {
        val channelId = "core_location_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Fake GPS Core Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Fake GPS 引擎運行中")
            .setContentText("擬真位移服務正在為您提供穩定的模擬定位")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    /**
     * 開始自動路徑導航
     */
    fun startRoute(waypoints: List<Pair<Double, Double>>) {
        if (waypoints.isEmpty()) return
        
        currentPath = routePlanner.planRoute(waypoints, 5.0)
        currentIndex = 0
        _isAutoWalking.value = true
        
        // 安全獲取 WakeLock
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L /* 2 hours max */)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        autoWalkJob?.cancel()
        autoWalkJob = lifecycleScope.launch {
            var lastLat = _currentLocation.value.first
            var lastLng = _currentLocation.value.second
            var startTime = System.currentTimeMillis()

            while (isActive && currentIndex < currentPath.size) {
                // 1. 檢查隨機停頓 (Micro-Stops)
                behaviorEngine.shouldTriggerMicroStop()?.let { stopSeconds ->
                    delay(stopSeconds * 1000L)
                    startTime = System.currentTimeMillis() // 恢復後重置加速時間
                }

                val target = currentPath[currentIndex]
                
                // 2. 計算擬真速度 (Easing + Fluctuation)
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val baseSpeed = behaviorEngine.calculateEasingSpeed(18.0, elapsedSeconds)
                val currentSpeed = behaviorEngine.generateSpeedFluctuation(baseSpeed)
                
                // 3. 執行位移運算
                val nextLoc = movementEngine.calculateNextLocation(
                    lastLat, lastLng,
                    target.first, target.second,
                    currentSpeed, 1000
                )

                // 計算方位角
                val bearing = movementEngine.calculateBearing(lastLat, lastLng, nextLoc.first, nextLoc.second)
                // 轉換速度為 m/s (系統要求)
                val speedMs = (currentSpeed / 3.6).toFloat()

                val jitteredLoc = movementEngine.applyGaussianJitter(nextLoc.first, nextLoc.second)
                updateLocationFull(jitteredLoc.first, jitteredLoc.second, speedMs, bearing)
                
                lastLat = jitteredLoc.first
                lastLng = jitteredLoc.second

                val distToTarget = movementEngine.calculateDistance(lastLat, lastLng, target.first, target.second)
                if (distToTarget < 1.0) {
                    currentIndex++
                }
                
                delay(1000)
            }
            _isAutoWalking.value = false
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
    }

    /**
     * 停止自動行走
     */
    fun stopRoute() {
        _isAutoWalking.value = false
        autoWalkJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    /**
     * 手動設定座標
     */
    fun updateLocation(lat: Double, lng: Double) {
        updateLocationFull(lat, lng, 0.0f, 0.0f)
    }

    /**
     * 更新完整座標特徵
     */
    private fun updateLocationFull(lat: Double, lng: Double, speed: Float, bearing: Float) {
        val jittered = movementEngine.applyGaussianJitter(lat, lng)
        val altitude = altitudeEngine.calculateCurrentAltitude()
        mockLocationManager.setMockLocation(jittered.first, jittered.second, altitude, speed, bearing)
        _currentLocation.value = jittered
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        mockLocationManager.removeMockProvider()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        intent?.let {
            val action = it.action
            val lat = it.getDoubleExtra("LAT", -1.0)
            val lng = it.getDoubleExtra("LNG", -1.0)
            
            if (action == "UPDATE_LOCATION" && lat != -1.0 && lng != -1.0) {
                updateLocation(lat, lng)
            }
        }
        
        return START_STICKY
    }
}
