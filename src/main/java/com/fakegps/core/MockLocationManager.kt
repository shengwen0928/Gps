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
                    false, false, false, false,
                    true, true, true,
                    0, 1
                )
                locationManager.setTestProviderEnabled(provider, true)
                
                // 設定狀態為可用，這對某些 Android 版本很重要
                locationManager.setTestProviderStatus(
                    provider,
                    android.location.LocationProvider.AVAILABLE,
                    null,
                    System.currentTimeMillis()
                )
            } catch (e: SecurityException) {
                success = false
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return success
    }

    /**
     * 設定模擬位置（同時發送到所有 Provider）
     */
    fun setMockLocation(lat: Double, lng: Double, alt: Double) {
        val currentTime = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                altitude = alt
                time = currentTime
                accuracy = 1.0f // 設定更高的精確度（數值越小越精確）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = elapsedNanos
                }
                // 加入必要的 flag
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
