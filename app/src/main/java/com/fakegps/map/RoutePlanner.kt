package com.fakegps.map

import com.fakegps.engine.MovementEngine
import kotlin.math.*

/**
 * 路徑規劃器：負責生成平滑的路徑點
 */
class RoutePlanner(private val movementEngine: MovementEngine) {

    /**
     * 生成平滑路徑
     * @param waypoints 原始座標點清單
     * @param pointSpacingMeters 點與點之間的期望間距 (公尺)
     * @return 平滑後的全量路徑點
     */
    fun planRoute(waypoints: List<Pair<Double, Double>>, pointSpacingMeters: Double = 5.0): List<Pair<Double, Double>> {
        if (waypoints.size < 2) return waypoints

        val smoothRoute = mutableListOf<Pair<Double, Double>>()
        
        for (i in 0 until waypoints.size - 1) {
            val start = waypoints[i]
            val end = waypoints[i + 1]
            
            val segmentDist = movementEngine.calculateDistance(start.first, start.second, end.first, end.second)
            
            // 加入起點
            smoothRoute.add(start)
            
            if (segmentDist > pointSpacingMeters) {
                val numIntermediatePoints = (segmentDist / pointSpacingMeters).toInt()
                for (j in 1..numIntermediatePoints) {
                    val ratio = (j * pointSpacingMeters) / segmentDist
                    val lat = start.first + (end.first - start.first) * ratio
                    val lng = start.second + (end.second - start.second) * ratio
                    smoothRoute.add(Pair(lat, lng))
                }
            }
        }
        
        // 加入最後一個點
        smoothRoute.add(waypoints.last())
        
        return smoothRoute
    }
}
