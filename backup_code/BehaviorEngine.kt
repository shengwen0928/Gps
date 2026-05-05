package com.fakegps.engine

import java.util.Random
import kotlin.math.min

/**
 * 高擬真行為引擎：負責處理加減速曲線與隨機停頓邏輯。
 * 符合「每項功能分開寫」的開發規範。
 */
class BehaviorEngine {
    private val random = Random()

    /**
     * 模擬衛星鎖定過程
     * 返回當前鎖定的衛星數量 (4 -> 12)
     */
    fun calculateSatellites(elapsedSeconds: Double): Int {
        return when {
            elapsedSeconds < 5 -> 4
            elapsedSeconds < 10 -> 8
            else -> 12
        }
    }

    /**
     * 計算平滑的擬真速度 (採用 S 型曲線)
     * @param targetSpeed 期望目標速度 (km/h)
     * @param elapsedSeconds 從動作起算的秒數
     */
    fun calculateEasingSpeed(targetSpeed: Double, elapsedSeconds: Double, isStopping: Boolean = false): Double {
        val accelDuration = 5.0
        val t = (elapsedSeconds / accelDuration).coerceIn(0.0, 1.0)
        
        // 採用 Smoothstep (S型曲線) 算法: 3t^2 - 2t^3
        val smoothFactor = t * t * (3 - 2 * t)
        
        return if (isStopping) {
            targetSpeed * (1.0 - smoothFactor)
        } else {
            targetSpeed * smoothFactor
        }
    }

    /**
     * 判定是否應觸發隨機停頓 (Micro-Stops)
     * 模擬等紅綠燈或查看手機的情境
     */
    fun shouldTriggerMicroStop(): Int? {
        // 每步有 0.5% 的機率停頓 (5Hz 下較合理)
        if (random.nextDouble() < 0.005) {
            return 5 + random.nextInt(26)
        }
        return null
    }

    /**
     * 產生正弦波物理平滑偏移
     * 模擬真實人類移動時的微小重心晃動
     */
    fun calculateSineJitter(lat: Double, elapsedSeconds: Double): Pair<Double, Double> {
        // 使用正弦波產生平滑的週期性晃動，頻率為 0.5Hz ~ 1Hz
        val phase = elapsedSeconds * Math.PI
        val jitterMeters = 0.2 + (Math.sin(phase) * 0.1) 
        val angle = phase % (2 * Math.PI)
        
        val offsetLat = (jitterMeters * Math.cos(angle)) / 111320.0
        val offsetLng = (jitterMeters * Math.sin(angle)) / (111320.0 * Math.cos(Math.toRadians(lat)))
        
        return Pair(offsetLat, offsetLng)
    }

    /**
     * 產生擬真速度波動
     */
    fun generateSpeedFluctuation(baseSpeed: Double): Double {
        val jitter = (random.nextDouble() - 0.5) * 1.5 
        return (baseSpeed + jitter).coerceIn(15.0, 20.0)
    }
}
