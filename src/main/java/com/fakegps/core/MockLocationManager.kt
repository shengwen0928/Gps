package com.fakegps.core

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

/**
 * 負責管理模擬位置服務的核心類別
 */
class MockLocationManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    /**
     * 初始化 Mock Providers
     */
    fun setupMockProvider(): Boolean {
        var success = true
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
                
                // 初次設定狀態
                updateProviderStatus(provider)
            } catch (e: SecurityException) {
                success = false
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return success
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
    fun setMockLocation(lat: Double, lng: Double, alt: Double) {
        val currentTime = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            // 每一次更新位置前，再次確保狀態為可用，強迫 Fused Location 重新計算
            updateProviderStatus(provider)

            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                altitude = alt
                time = currentTime
                accuracy = 1.0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = elapsedNanos
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 0.5f
                    speedAccuracyMetersPerSecond = 0.1f
                    bearingAccuracyDegrees = 0.1f
                }
                val bundle = android.os.Bundle()
                bundle.putInt("satellites", 10)
                extras = bundle
            }
            try {
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (e: Exception) {
                e.printStackTrace()
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
