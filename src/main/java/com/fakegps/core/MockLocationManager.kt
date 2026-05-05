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
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            // 恢復 v1.8.0 成功的策略：每次寫入前確保 Provider 處於可用狀態
            updateProviderStatus(provider)

            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                altitude = alt
                this.speed = speed
                this.bearing = bearing
                time = currentTime
                accuracy = 1.0f // 恢復標準高精度
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = elapsedNanos
                }
                
                // 簡化 Metadata，回歸基礎但穩定的特徵
                val bundle = android.os.Bundle()
                bundle.putInt("satellites", satellites)
                extras = bundle
            }
            try {
                locationManager.setTestProviderLocation(provider, mockLocation)
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
