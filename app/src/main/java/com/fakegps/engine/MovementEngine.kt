package com.fakegps.engine

import kotlin.math.*
import java.util.Random

/**
 * 擬真位移引擎
 */
class MovementEngine {
    private val random = Random()

    /**
     * 計算下一個座標
     */
    fun calculateNextLocation(
        currentLat: Double,
        currentLng: Double,
        targetLat: Double,
        targetLng: Double,
        speedKmh: Double,
        intervalMs: Long
    ): Pair<Double, Double> {
        val totalDist = calculateDistance(currentLat, currentLng, targetLat, targetLng)
        if (totalDist == 0.0) return Pair(currentLat, currentLng)

        // 確保速度在 15-20 km/h 區間
        val enforcedSpeed = clampSpeed(speedKmh)
        
        // 速度轉換: km/h -> m/s
        val speedMs = enforcedSpeed / 3.6
        val moveDist = speedMs * (intervalMs / 1000.0)

        // 若移動距離超過目標，直接回傳目標
        if (moveDist >= totalDist) return Pair(targetLat, targetLng)

        val ratio = moveDist / totalDist
        val nextLat = currentLat + (targetLat - currentLat) * ratio
        val nextLng = currentLng + (targetLng - currentLng) * ratio

        return Pair(nextLat, nextLng)
    }

    /**
     * 限制速度在 15-20 km/h 之間 (除非是轉彎降速的情況)
     */
    fun clampSpeed(speedKmh: Double): Double {
        return when {
            speedKmh > 20.0 -> 20.0
            speedKmh >= 15.0 -> speedKmh
            speedKmh > 10.0 -> 15.0 // 低於 15 但高於 10，視為需要修正的低速
            else -> speedKmh // 低於 10，視為轉彎降速，保持原樣
        }
    }

    /**
     * 增加高斯隨機抖動 (嚴格落在 0.5~1.5 公尺)
     */
    fun applyGaussianJitter(lat: Double, lng: Double): Pair<Double, Double> {
        // 使用高斯分佈產生隨機位移，並強制限制在 0.5~1.5m
        val rawJitter = 1.0 + random.nextGaussian() * 0.3
        val jitterMeters = rawJitter.coerceIn(0.5, 1.5)
        
        val angle = random.nextDouble() * 2 * PI
        
        // 簡易座標偏移計算 (1度緯度約 111320公尺)
        val offsetLat = (jitterMeters * cos(angle)) / 111320.0
        val offsetLng = (jitterMeters * sin(angle)) / (111320.0 * cos(Math.toRadians(lat)))
        
        return Pair(lat + offsetLat, lng + offsetLng)
    }

    /**
     * 根據轉向角度調整速度
     */
    fun adjustSpeedForTurn(currentSpeed: Double, currentBearing: Double, targetBearing: Double): Double {
        var diff = abs(currentBearing - targetBearing)
        if (diff > 180) diff = 360 - diff
        
        return if (diff > 45.0) {
            min(currentSpeed, 8.0) // 轉彎降速至 8km/h 以下
        } else {
            currentSpeed
        }
    }

    /**
     * 計算兩點間距離 (公尺) - Haversine 公式
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球半徑 (公尺)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
