package com.fakegps.map

import com.fakegps.engine.MovementEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RoutePlanner 測試：驗證路徑插值與平滑化邏輯
 */
class RoutePlannerTest {

    private val movementEngine = mockk<MovementEngine>()
    private val routePlanner = RoutePlanner(movementEngine)

    @Test
    fun `test route smoothing with multi-points`() {
        // Arrange: 兩個點，距離 100 公尺
        val start = Pair(25.0, 121.0)
        val end = Pair(25.0, 121.001) // 假設經度增加 0.001 距離約 100m
        val waypoints = listOf(start, end)
        
        every { movementEngine.calculateDistance(any(), any(), any(), any()) } returns 100.0
        
        // Act: 設定每 10 公尺一個點
        val result = routePlanner.planRoute(waypoints, 10.0)
        
        // Assert: 應生成 1 (起點) + 10 (插值) + 1 (終點) = 12 個點
        // 注意：實作中是 (100/10) = 10 個插值點
        assertEquals(12, result.size)
        assertEquals(start, result.first())
        assertEquals(end, result.last())
    }

    @Test
    fun `test short distance should not generate extra points`() {
        val start = Pair(25.0, 121.0)
        val end = Pair(25.0, 121.00001)
        val waypoints = listOf(start, end)
        
        // 距離僅 1 公尺，小於間距 5.0
        every { movementEngine.calculateDistance(any(), any(), any(), any()) } returns 1.0
        
        val result = routePlanner.planRoute(waypoints, 5.0)
        
        // 應僅保留原始兩點
        assertEquals(2, result.size)
    }

    @Test
    fun `test empty or single point should return as is`() {
        val emptyList = listOf<Pair<Double, Double>>()
        assertEquals(emptyList, routePlanner.planRoute(emptyList))
        
        val singlePoint = listOf(Pair(25.0, 121.0))
        assertEquals(singlePoint, routePlanner.planRoute(singlePoint))
    }
}
