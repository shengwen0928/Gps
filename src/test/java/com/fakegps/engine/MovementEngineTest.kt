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
        
        // Run multiple times to verify range
        for (i in 1..100) {
            val jittered = engine.applyGaussianJitter(lat, lng)
            val dist = engine.calculateDistance(lat, lng, jittered.first, jittered.second)
            assertTrue("Jitter $dist should be >= 0.5", dist >= 0.5)
            assertTrue("Jitter $dist should be <= 1.5", dist <= 1.5)
        }
    }

    @Test
    fun `test strict speed locking`() {
        val engine = MovementEngine()
        
        // Case 1: Too slow (not a turn)
        val slowSpeed = 12.0
        val clampedSlow = engine.clampSpeed(slowSpeed)
        assertEquals(15.0, clampedSlow, 0.001)
        
        // Case 2: Too fast
        val fastSpeed = 25.0
        val clampedFast = engine.clampSpeed(fastSpeed)
        assertEquals(20.0, clampedFast, 0.001)
        
        // Case 3: Within range
        val okSpeed = 17.0
        val clampedOk = engine.clampSpeed(okSpeed)
        assertEquals(17.0, clampedOk, 0.001)

        // Case 4: Turn speed (should NOT be clamped up to 15)
        val turnSpeed = 8.0
        val clampedTurn = engine.clampSpeed(turnSpeed)
        assertEquals(8.0, clampedTurn, 0.001)
    }

    @Test
    fun `test speed range with turn`() {
        val engine = MovementEngine()
        
        // Turn should still allow speed < 15
        val turnSpeed = engine.adjustSpeedForTurn(18.0, 0.0, 90.0)
        assertTrue("Turn speed $turnSpeed should be <= 8.0", turnSpeed <= 8.0)
    }
}
