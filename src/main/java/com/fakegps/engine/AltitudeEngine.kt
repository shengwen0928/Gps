package com.fakegps.engine

import java.util.Random

/**
 * 海拔擬真引擎：負責提供擬真的海拔高度數據。
 * 符合「功能分開寫」的開發規範。
 */
class AltitudeEngine {
    private val random = Random()
    private var currentBaseAltitude = 20.0 // 假設初始海拔

    /**
     * 模擬海拔高度。
     * 在實際生產環境中，此處應對接地形 API 或地圖數據。
     * 目前實作擬真隨機波動，模擬行走時的地形起伏。
     */
    fun calculateCurrentAltitude(): Double {
        // 隨機產生 0.1~0.3 公尺的微小起伏
        val fluctuation = (random.nextDouble() - 0.5) * 0.5
        currentBaseAltitude += fluctuation
        
        // 確保海拔在合理範圍內 (例如 5m ~ 1000m)
        currentBaseAltitude = currentBaseAltitude.coerceIn(5.0, 1000.0)
        
        return currentBaseAltitude
    }

    /**
     * 重置基礎海拔（例如切換城市時）
     */
    fun setBaseAltitude(base: Double) {
        currentBaseAltitude = base
    }
}
