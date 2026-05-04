package com.fakegps.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            initCurrentLocation()
        }
    }

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
        
        userMarker = Marker(map)
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        userMarker.title = "目前模擬位置"
        map.overlays.add(userMarker)

        mockLocationManager = MockLocationManager(this)
        movementEngine = MovementEngine()
        routePlanner = RoutePlanner(movementEngine)

        mockLocationManager.setupMockProvider()

        // 檢查並請求權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            initCurrentLocation()
        }

        // 地圖單擊與長按取點
        val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    val newPoint = GeoPoint(it.latitude, it.longitude)
                    userMarker.position = newPoint
                    mockLocationManager.setMockLocation(it.latitude, it.longitude, 0.0)
                    map.controller.animateTo(newPoint)
                    map.invalidate()
                    Toast.makeText(this@MainActivity, "已瞬移至新位置", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let {
                    Toast.makeText(this@MainActivity, "目的地已設定，請點擊[開始行走]", Toast.LENGTH_SHORT).show()
                    val waypoints = listOf(
                        Pair(userMarker.position.latitude, userMarker.position.longitude),
                        Pair(it.latitude, it.longitude)
                    )
                    currentPath = routePlanner.planRoute(waypoints, 5.0)
                    currentIndex = 0
                }
                return true
            }
        })
        map.overlays.add(0, eventsOverlay)

        val btnStart = findViewById<Button>(R.id.btn_start_auto_walk)
        val btnStop = findViewById<Button>(R.id.btn_stop_auto_walk)
        val btnFloating = findViewById<Button>(R.id.btn_floating_joystick)

        btnFloating.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, FloatingJoystickService::class.java)
                intent.putExtra("LAT", userMarker.position.latitude)
                intent.putExtra("LNG", userMarker.position.longitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "懸浮搖桿已啟動，您可以退出 App 了", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "請先允許顯示在其他應用程式上層的權限", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        btnStart.setOnClickListener {
            if (currentPath.isNotEmpty()) {
                startAutoWalk()
            } else {
                Toast.makeText(this, "請先在地圖上長按設定目的地", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopAutoWalk()
        }
    }

    private fun initCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val startPoint = if (location != null) {
                    GeoPoint(location.latitude, location.longitude)
                } else {
                    GeoPoint(25.0330, 121.5654) // 預設台北 101
                }
                map.controller.setCenter(startPoint)
                userMarker.position = startPoint
                mockLocationManager.setMockLocation(startPoint.latitude, startPoint.longitude, 0.0)
                map.invalidate()
            }
        } catch (e: SecurityException) {
            // Permission denied
        }
    }

    private fun startAutoWalk() {
        if (isAutoWalking) return

        if (currentPath.isEmpty()) {
            Toast.makeText(this, "請先在地圖上長按設定目的地", Toast.LENGTH_SHORT).show()
            return
        }

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
