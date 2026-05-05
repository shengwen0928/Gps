package com.fakegps.core

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.fakegps.engine.NmeaEngine

/**
 * 負責管理模擬位置服務的核心類別
 */
class MockLocationManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providers = listOf(
        LocationManager.GPS_PROVIDER, 
        LocationManager.NETWORK_PROVIDER,
        "fused" // 同時模擬 Fused Provider
    )
    
    private val nmeaEngine = NmeaEngine()

    /**
     * 初始化 Mock Providers
     */
    fun setupMockProvider(): Boolean {
        var gpsAuthorized = false
        for (provider in providers) {
            try {
                if (locationManager.allProviders.contains(provider)) {
                    locationManager.removeTestProvider(provider)
                }

                locationManager.addTestProvider(
                    provider,
                    true, true, true, false,
                    true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
                updateProviderStatus(provider)

                // 只要 GPS 成功初始化，代表「開發者選項」授權成功
                if (provider == LocationManager.GPS_PROVIDER) {
                    gpsAuthorized = true
                }
            } catch (e: SecurityException) {
                // 如果是 SecurityException，代表該 Provider 沒權限
                if (provider == LocationManager.GPS_PROVIDER) {
                    gpsAuthorized = false
                }
            } catch (e: Exception) {
                // 靜默處理其他錯誤（例如 fused 在某些設備不支持 addTestProvider）
            }
        }
        return gpsAuthorized
    }

    private fun updateProviderStatus(provider: String) {
        try {
            locationManager.setTestProviderStatus(
                provider,
                android.location.LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // 某些版本可能不支持
        }
    }

    /**
     * 設定模擬位置
     */
    fun setMockLocation(lat: Double, lng: Double, alt: Double, speed: Float = 0.0f, bearing: Float = 0.0f, satellites: Int = 12) {
        val currentTime = System.currentTimeMillis()
        // 關鍵：將納秒時間戳提前一個固定的小量 (10ms)，確保在系統 Fused 引擎仲裁中始終佔優
        val elapsedNanos = SystemClock.elapsedRealtimeNanos() + 10_000_000L

        for (provider in providers) {
            // 自動修復失效的 Provider
            if (!locationManager.isProviderEnabled(provider)) {
                try { locationManager.setTestProviderEnabled(provider, true) } catch (e: Exception) {}
            }

            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                altitude = alt
                this.speed = speed
                this.bearing = bearing
                time = currentTime
                accuracy = 0.5f // 強力壓制真實訊號
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = elapsedNanos
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 0.1f
                    speedAccuracyMetersPerSecond = 0.01f
                    bearingAccuracyDegrees = 0.1f
                }
                
                val bundle = android.os.Bundle()
                bundle.putInt("satellites", satellites)
                bundle.putBoolean("mockLocation", false)
                
                // 注入 NMEA
                val gpgga = nmeaEngine.generateGpgga(lat, lng, alt, satellites, currentTime)
                val gprmc = nmeaEngine.generateGprmc(lat, lng, speed, bearing, currentTime)
                bundle.putString("nmea_gpgga", gpgga)
                bundle.putString("nmea_gprmc", gprmc)
                bundle.putStringArrayList("NMEA", arrayListOf(gpgga, gprmc))
                
                extras = bundle
            }
            try {
                locationManager.setTestProviderLocation(provider, mockLocation)
                // 寫入後立即發送狀態心跳，強迫系統重新採納
                locationManager.setTestProviderStatus(provider, android.location.LocationProvider.AVAILABLE, null, currentTime)
            } catch (e: Exception) {
                // 靜默處理
            }
        }
    }

    /**
     * 移除 Mock Providers
     */
    fun removeMockProvider() {
        for (provider in providers) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
