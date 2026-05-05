package com.fakegps.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.gms.location.LocationServices
import com.fakegps.R
import com.fakegps.core.CoreLocationService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * MainActivity: 負責 UI 顯示與地圖互動
 * 已重構為觀察者模式，邏輯移至 CoreLocationService 與 MainViewModel
 */
class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var userMarker: Marker
    private val viewModel: MainViewModel by viewModels()

    private var currentPath = listOf<Pair<Double, Double>>()
    
    private val backgroundLocationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "需要「一律允許」定位才能在背景穩定執行", Toast.LENGTH_LONG).show()
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            initCurrentLocation()
            requestBackgroundLocationPermission()
        } else {
            Toast.makeText(this, "需要定位權限才能獲取目前位置", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("需要背景定位權限")
                        .setMessage("為了讓模擬行走在螢幕關閉時仍能持續運作，請在接下來的系統設定中選擇「一律允許」。")
                        .setPositiveButton("前往設定") { _, _ ->
                            backgroundLocationPermissionRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    backgroundLocationPermissionRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 設置沉浸式狀態欄
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            
            // OSMDroid 初始化
            val ctx = applicationContext
            Configuration.getInstance().userAgentValue = packageName
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            
            setContentView(R.layout.activity_main)

            setupFullscreen()
            setupMap()
            setupObservers()
            requestPermissions()

            // 啟動並綁定核心服務
            val serviceIntent = Intent(this, CoreLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            bindService(serviceIntent, viewModel.serviceConnection, BIND_AUTO_CREATE)

            // 搖桿對接
            val joystick = findViewById<JoystickView>(R.id.joystickView)
            joystick.setJoystickListener(object : JoystickView.JoystickListener {
                override fun onJoystickMoved(angle: Double, strength: Double) {
                    if (strength > 0) {
                        val rad = Math.toRadians(angle)
                        val nextLat = userMarker.position.latitude + (Math.sin(rad) * 0.0001)
                        val nextLng = userMarker.position.longitude + (Math.cos(rad) * 0.0001)
                        viewModel.updateManualLocation(nextLat, nextLng)
                    }
                }
            })

            // 地圖點擊事件
            val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let {
                        viewModel.updateManualLocation(it.latitude, it.longitude)
                        map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                    return true
                }
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    p?.let {
                        Toast.makeText(this@MainActivity, "目的地已設定", Toast.LENGTH_SHORT).show()
                        currentPath = listOf(
                            Pair(userMarker.position.latitude, userMarker.position.longitude),
                            Pair(it.latitude, it.longitude)
                        )
                    }
                    return true
                }
            })
            map.overlays.add(0, eventsOverlay)

            findViewById<Button>(R.id.btn_floating_joystick).setOnClickListener {
                startFloatingJoystick()
            }

            findViewById<Button>(R.id.btn_start_auto_walk).setOnClickListener {
                if (currentPath.isNotEmpty()) {
                    viewModel.startAutoWalk(currentPath)
                } else {
                    Toast.makeText(this, "請先在地圖上長按設定目的地", Toast.LENGTH_SHORT).show()
                }
            }

            findViewById<Button>(R.id.btn_stop_auto_walk).setOnClickListener {
                viewModel.stopAutoWalk()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "發生未知錯誤，請重啟應用", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        val mapController = map.controller
        mapController.setZoom(18.0)
        
        val defaultPoint = GeoPoint(25.0330, 121.5654)
        mapController.setCenter(defaultPoint)
        
        userMarker = Marker(map)
        userMarker.position = defaultPoint
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        userMarker.title = "目前位置"
        map.overlays.add(userMarker)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isServiceBound.collectLatest { isBound ->
                    if (isBound) {
                        observeServiceData()
                    }
                }
            }
        }
    }

    private fun observeServiceData() {
        val service = viewModel.getService() ?: return
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service.currentLocation.collectLatest { loc ->
                    val newPoint = GeoPoint(loc.first, loc.second)
                    userMarker.position = newPoint
                    map.controller.animateTo(newPoint)
                    map.invalidate()
                }
            }
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service.isAutoWalking.collectLatest { isWalking ->
                    if (isWalking) {
                        Toast.makeText(this@MainActivity, "開始自動擬真行走", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service.mockProviderStatus.collectLatest { success ->
                    if (!success) {
                        Toast.makeText(this@MainActivity, "請在「開發者選項」中將此 App 設為模擬位置應用程式", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun startFloatingJoystick() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingJoystickService::class.java).apply {
                putExtra("LAT", userMarker.position.latitude)
                putExtra("LNG", userMarker.position.longitude)
            }
            startService(intent)
            Toast.makeText(this, "懸浮搖桿已啟動", Toast.LENGTH_LONG).show()
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationPermissionRequest.launch(permissions.toTypedArray())

        // 請求忽略電池優化
        requestIgnoreBatteryOptimizations()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val startPoint = GeoPoint(it.latitude, it.longitude)
                    map.controller.animateTo(startPoint)
                    userMarker.position = startPoint
                    viewModel.updateManualLocation(startPoint.latitude, startPoint.longitude)
                    map.invalidate()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        unbindService(viewModel.serviceConnection)
        super.onDestroy()
    }
}
