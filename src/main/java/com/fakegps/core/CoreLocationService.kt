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
        startForegroundService()
        
        // 延遲 500ms 進行 Mock Provider 初始化，防止啟動時 Binder 競爭導致當機
        lifecycleScope.launch {
            delay(500)
            _mockProviderStatus.value = mockLocationManager.setupMockProvider()
        }
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
            var logicalLat = _currentLocation.value.first
            var logicalLng = _currentLocation.value.second
            var startTime = System.currentTimeMillis()

            while (isActive && currentIndex < currentPath.size) {
                // 1. 檢查隨機停頓 (Micro-Stops)
                behaviorEngine.shouldTriggerMicroStop()?.let { stopSeconds ->
                    delay(stopSeconds * 1000L)
                    startTime = System.currentTimeMillis()
                }

                val target = currentPath[currentIndex]
                
                // 2. 計算物理特徵 (S型曲線 + 隨機衛星鎖定)
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val baseSpeed = behaviorEngine.calculateEasingSpeed(18.0, elapsedSeconds)
                val currentSpeed = behaviorEngine.generateSpeedFluctuation(baseSpeed)
                val satellites = behaviorEngine.calculateSatellites(elapsedSeconds)
                
                // 3. 執行純淨位移運算 (基於 200ms 步長)
                val nextLoc = movementEngine.calculateNextLocation(
                    logicalLat, logicalLng,
                    target.first, target.second,
                    currentSpeed, 200
                )

                val bearing = movementEngine.calculateBearing(logicalLat, logicalLng, nextLoc.first, nextLoc.second)
                val speedMs = (currentSpeed / 3.6).toFloat()

                // 更新邏輯座標（不含抖動）供下一次循環
                logicalLat = nextLoc.first
                logicalLng = nextLoc.second

                // 4. 輸出至系統（套用微量抖動與動態衛星數）
                updateLocationFull(logicalLat, logicalLng, speedMs, bearing, satellites)

                val distToTarget = movementEngine.calculateDistance(logicalLat, logicalLng, target.first, target.second)
                if (distToTarget < 1.0) {
                    currentIndex++
                }
                
                delay(200) // 5Hz 高頻鎖定
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
        updateLocationFull(lat, lng, 0.0f, 0.0f, 12)
    }

    /**
     * 更新完整座標特徵 (系統層級輸出)
     */
    private fun updateLocationFull(lat: Double, lng: Double, speed: Float, bearing: Float, satellites: Int) {
        // 升級：使用正弦波物理平滑偏移，模擬真實移動慣性
        val elapsedTotal = (System.currentTimeMillis() % 1000000).toDouble() / 1000.0
        val jitter = behaviorEngine.calculateSineJitter(lat, elapsedTotal)
        val finalLat = lat + jitter.first
        val finalLng = lng + jitter.second

        val altitude = altitudeEngine.calculateCurrentAltitude()

        mockLocationManager.setMockLocation(finalLat, finalLng, altitude, speed, bearing, satellites)
        _currentLocation.value = Pair(finalLat, finalLng)
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
