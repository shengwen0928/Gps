package com.fakegps.engine

import org.junit.Assert.*
import org.junit.Test

class MovementEngineTest {

    @Test
    fun `test next location calculation based on speed`() {
        val engine = MovementEngine()
        val startLat = 25.0330 
        val startLng = 121.5654
        val targetLat = 25.0335
        val targetLng = 121.5660
        val speedKmh = 18.0 
        val intervalMs = 1000L 

        val nextLoc = engine.calculateNextLocation(startLat, startLng, targetLat, targetLng, speedKmh, intervalMs)
        
        assertNotNull(nextLoc)
        assertTrue(nextLoc.first > startLat)
    }

    @Test
    fun `test gaussian jitter range`() {
        val engine = MovementEngine()
        val lat = 25.0
        val lng = 121.0
        
        val jittered = engine.applyGaussianJitter(lat, lng)
        
        val dist = engine.calculateDistance(lat, lng, jittered.first, jittered.second)
        assertTrue(dist > 0.0)
        assertTrue(dist < 10.0) 
    }

    @Test
    fun `test slow down on sharp turns`() {
        val engine = MovementEngine()
        
        val normalSpeed = 18.0
        val slowSpeed = engine.adjustSpeedForTurn(normalSpeed, 0.0, 90.0)
        
        assertTrue(slowSpeed <= 10.0)
    }
}
