package com.fakegps.ui

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import com.fakegps.core.CoreLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MainActivity 的 ViewModel
 * 負責管理 CoreLocationService 的連接狀態與 UI 狀態分發
 */
class MainViewModel : ViewModel() {

    private var locationService: CoreLocationService? = null
    
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound = _isServiceBound.asStateFlow()

    // 從 Service 轉發的 Flow
    val currentLocation = MutableStateFlow<Pair<Double, Double>>(Pair(25.0330, 121.5654))
    val isAutoWalking = MutableStateFlow(false)

    /**
     * Service 連接器
     */
    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CoreLocationService.LocalBinder
            locationService = binder.getService()
            _isServiceBound.value = true
            
            // 由於 ViewModel 的生命週期通常比 Service 綁定久，
            // 這裡可以考慮訂閱 Service 的 Flow (實際專案中可用更多進階技術)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            _isServiceBound.value = false
        }
    }

    fun startAutoWalk(waypoints: List<Pair<Double, Double>>) {
        locationService?.startRoute(waypoints)
    }

    fun stopAutoWalk() {
        locationService?.stopRoute()
    }

    fun updateManualLocation(lat: Double, lng: Double) {
        locationService?.updateLocation(lat, lng)
    }

    fun getService(): CoreLocationService? = locationService
}
