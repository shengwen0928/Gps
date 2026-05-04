package com.fakegps.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.fakegps.R
import com.fakegps.core.MockLocationManager
import com.fakegps.engine.MovementEngine
import com.fakegps.map.RoutePlanner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var movementEngine: MovementEngine
    private lateinit var routePlanner: RoutePlanner
    private lateinit var userMarker: Marker

    private var isAutoWalking = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentPath = listOf<Pair<Double, Double>>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // OSMDroid 需要在載入 layout 前初始化，並設定 User-Agent 以免被封鎖圖資
        val ctx = applicationContext
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        val mapController = map.controller
        mapController.setZoom(18.0)
        
        // 初始位置設定在台北 101
        val startPoint = GeoPoint(25.0330, 121.5654)
        mapController.setCenter(startPoint)

        userMarker = Marker(map)
        userMarker.position = startPoint
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        userMarker.title = "目前模擬位置"
        map.overlays.add(userMarker)

        mockLocationManager = MockLocationManager(this)
        movementEngine = MovementEngine()
        routePlanner = RoutePlanner(movementEngine)

        mockLocationManager.setupMockProvider()

        // 搖桿對接
        val joystick = findViewById<JoystickView>(R.id.joystickView)
        joystick.setJoystickListener(object : JoystickView.JoystickListener {
            override fun onJoystickMoved(angle: Double, strength: Double) {
                if (strength > 0) {
                    // 將搖桿角度與力度轉換為位移指令
                    // 力度 0.0-1.0 對應 15-20km/h
                    val speed = 15.0 + (strength * 5.0)
                    val rad = Math.toRadians(angle)
                    val nextLat = userMarker.position.latitude + (Math.cos(rad) * 0.0001)
                    val nextLng = userMarker.position.longitude + (Math.sin(rad) * 0.0001)
                    
                    val jittered = movementEngine.applyGaussianJitter(nextLat, nextLng)
                    mockLocationManager.setMockLocation(jittered.first, jittered.second, 0.0)
                    
                    val newPoint = GeoPoint(jittered.first, jittered.second)
                    userMarker.position = newPoint
                    map.invalidate()
                }
            }
        })

        // 地圖長按取點
        val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let {
                    Toast.makeText(this@MainActivity, "設定目的地: ${it.latitude}, ${it.longitude}", Toast.LENGTH_SHORT).show()
                    val waypoints = listOf(
                        Pair(userMarker.position.latitude, userMarker.position.longitude),
                        Pair(it.latitude, it.longitude)
                    )
                    currentPath = routePlanner.planRoute(waypoints, 5.0)
                    currentIndex = 0
                    startAutoWalk()
                }
                return true
            }
        })
        map.overlays.add(0, eventsOverlay)

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

        val waypoints = listOf(
            Pair(25.0330, 121.5654),
            Pair(25.0335, 121.5660),
            Pair(25.0340, 121.5670),
            Pair(25.0345, 121.5680),
            Pair(25.0330, 121.5654) // 返回原點
        )

        currentPath = routePlanner.planRoute(waypoints, 2.0)
        currentIndex = 0
        isAutoWalking = true

        Toast.makeText(this, "開始自動擬真行走 (15-20km/h)", Toast.LENGTH_SHORT).show()
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

            val nextLoc = movementEngine.calculateNextLocation(
                lastLat, lastLng,
                target.first, target.second,
                18.0, 1000
            )

            val jitteredLoc = movementEngine.applyGaussianJitter(nextLoc.first, nextLoc.second)

            // 更新系統模擬位置
            mockLocationManager.setMockLocation(jitteredLoc.first, jitteredLoc.second, 0.0)
            
            // 更新地圖上的標記位置
            val newPoint = GeoPoint(jitteredLoc.first, jitteredLoc.second)
            userMarker.position = newPoint
            map.controller.animateTo(newPoint)
            map.invalidate()
            
            lastLat = jitteredLoc.first
            lastLng = jitteredLoc.second

            val distToTarget = movementEngine.calculateDistance(lastLat, lastLng, target.first, target.second)
            if (distToTarget < 1.0) {
                currentIndex++
            }
            
            handler.postDelayed(this, 1000)
        }
    }

    private fun stopAutoWalk() {
        isAutoWalking = false
        handler.removeCallbacks(autoWalkRunnable)
        Toast.makeText(this, "停止行走", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mockLocationManager.removeMockProvider()
    }
}
