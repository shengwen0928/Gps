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
     * 計算加減速後的當前速度 (Easing)
     * @param targetSpeed 期望目標速度 (km/h)
     * @param elapsedSeconds 從開始或停止動作起算的秒數
     * @param isStopping 是否正在減速停止
     * @return 調整後的擬真速度
     */
    fun calculateEasingSpeed(targetSpeed: Double, elapsedSeconds: Double, isStopping: Boolean = false): Double {
        val accelerationTime = 5.0 // 假設 5 秒達到目標速度
        return if (isStopping) {
            // 減速邏輯
            val speed = targetSpeed * (1.0 - (elapsedSeconds / accelerationTime))
            speed.coerceAtLeast(0.0)
        } else {
            // 加速邏輯
            val speed = targetSpeed * (elapsedSeconds / accelerationTime)
            min(speed, targetSpeed)
        }
    }

    /**
     * 判定是否應觸發隨機停頓 (Micro-Stops)
     * 模擬等紅綠燈或查看手機的情境
     */
    fun shouldTriggerMicroStop(): Int? {
        // 假設每步有 1% 的機率停頓
        if (random.nextDouble() < 0.01) {
            // 隨機停等 5~30 秒
            return 5 + random.nextInt(26)
        }
        return null
    }

    /**
     * 產生隨機的速度抖動 (15-20km/h 區間內)
     */
    fun generateSpeedFluctuation(baseSpeed: Double): Double {
        val jitter = (random.nextDouble() - 0.5) * 2.0 // -1.0 ~ 1.0
        return (baseSpeed + jitter).coerceIn(15.0, 20.0)
    }
}
