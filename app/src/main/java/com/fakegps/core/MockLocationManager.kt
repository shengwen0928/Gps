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
    private val providerName = LocationManager.GPS_PROVIDER

    /**
     * 初始化 Mock Provider
     * 需確保在「開發者選項」中已將此 App 設定為模擬位置應用程式
     */
    fun setupMockProvider() {
        try {
            // 參數說明：提供者名稱, 是否支援高度, 是否支援速度, 是否支援方位, 是否有成本, 支援電池電力, 支援高度, 支援速度, 功率需求, 精準度
            locationManager.addTestProvider(
                providerName,
                false, false, false, false,
                true, true, true,
                0, 5
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: SecurityException) {
            // 處理權限不足（未在開發者選項中設定為模擬位置 App）
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 設定模擬位置
     * @param lat 緯度
     * @param lng 經度
     * @param alt 高度
     */
    fun setMockLocation(lat: Double, lng: Double, alt: Double) {
        val mockLocation = Location(providerName).apply {
            latitude = lat
            longitude = lng
            altitude = alt
            time = System.currentTimeMillis()
            accuracy = 3.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
        }
        try {
            locationManager.setTestProviderLocation(providerName, mockLocation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 移除 Mock Provider
     */
    fun removeMockProvider() {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
