package com.fakegps.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fakegps.R
import com.fakegps.core.MockLocationManager
import com.fakegps.engine.MovementEngine
import com.fakegps.map.RoutePlanner

class MainActivity : AppCompatActivity() {

    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var movementEngine: MovementEngine
    private lateinit var routePlanner: RoutePlanner

    private var isAutoWalking = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentPath = listOf<Pair<Double, Double>>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mockLocationManager = MockLocationManager(this)
        movementEngine = MovementEngine()
        routePlanner = RoutePlanner(movementEngine)

        // 初始化模擬位置提供者
        mockLocationManager.setupMockProvider()

        val btnStart = findViewById<Button>(R.id.btn_start_auto_walk)
        val btnStop = findViewById<Button>(R.id.btn_stop_auto_walk)

        btnStart.setOnClickListener {
            startAutoWalk()
        }

        btnStop.setOnClickListener {
            stopAutoWalk()
        }
    }

    private fun startAutoWalk() {
        if (isAutoWalking) return

        // 模擬選取的座標點 (Waypoint)
        val waypoints = listOf(
            Pair(25.0330, 121.5654), // 台北101
            Pair(25.0335, 121.5660),
            Pair(25.0340, 121.5670),
            Pair(25.0345, 121.5680)
        )

        currentPath = routePlanner.planRoute(waypoints, 2.0)
        currentIndex = 0
        isAutoWalking = true

        Toast.makeText(this, "開始自動行走", Toast.LENGTH_SHORT).show()
        autoWalkRunnable.run()
    }

    private val autoWalkRunnable = object : Runnable {
        private var lastLat = 0.0
        private var lastLng = 0.0

        override fun run() {
            if (!isAutoWalking || currentIndex >= currentPath.size) {
                stopAutoWalk()
                return
            }

            val target = currentPath[currentIndex]
            
            if (currentIndex == 0) {
                lastLat = target.first
                lastLng = target.second
            }

            // 使用 MovementEngine 計算下一個位置，確保速度與間隔符合擬真邏輯
            // 這裡假設期望速度為 18km/h，間隔為 1000ms
            val nextLoc = movementEngine.calculateNextLocation(
                lastLat, lastLng,
                target.first, target.second,
                18.0, 1000
            )

            // 增加隨機抖動以增加擬真度
            val jitteredLoc = movementEngine.applyGaussianJitter(nextLoc.first, nextLoc.second)

            mockLocationManager.setMockLocation(jitteredLoc.first, jitteredLoc.second, 0.0)
            
            lastLat = jitteredLoc.first
            lastLng = jitteredLoc.second

            // 如果已經接近目標點，則移動到下一個路徑點
            val distToTarget = movementEngine.calculateDistance(lastLat, lastLng, target.first, target.second)
            if (distToTarget < 1.0) {
                currentIndex++
            }
            
            // 每秒移動一次
            handler.postDelayed(this, 1000)
        }
    }

    private fun stopAutoWalk() {
        isAutoWalking = false
        handler.removeCallbacks(autoWalkRunnable)
        Toast.makeText(this, "停止自動行走", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mockLocationManager.removeMockProvider()
    }
}
