package com.scribidoo.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationMathTest {

    @Test
    fun testCalculateCalibration_insufficientSamples_returnsFallback() {
        val result = CalibrationMath.calculateCalibration(emptyList())
        assertEquals(0, result.sampleCount)
        assertEquals(130f, result.recommendedMajorThresholdPx, 0.1f)
        assertEquals(0.14f, result.recommendedSizeThreshold, 0.01f)
    }

    @Test
    fun testCalculateCalibration_normalFingerWithTightBuffer() {
        val samples = (1..20).map { i ->
            TouchSample(
                x = 100f + i,
                y = 100f + i,
                touchMajor = 80f + (i % 5), // 80 - 84 px
                touchSize = 0.06f + (i % 3) * 0.01f, // 0.06 - 0.08
                pressure = 0.5f
            )
        }

        // Default 1.25x (+25% buffer)
        val result = CalibrationMath.calculateCalibration(samples, bufferFactor = 1.25f)
        assertEquals(20, result.sampleCount)
        assertTrue("Measured major should be ~82px", result.measuredMajorPx in 80f..85f)
        assertTrue(
            "Recommended threshold with 1.25x buffer should be ~105px (not 130px)",
            result.recommendedMajorThresholdPx in 100f..110f
        )
    }

    @Test
    fun testCalculateCalibration_withTwoFingerZoomSamples() {
        val tipSamples = (1..10).map {
            TouchSample(x = 50f, y = 50f, touchMajor = 75f, touchSize = 0.05f, pressure = 0.5f)
        }
        val zoomSamples = (1..10).map {
            TouchSample(x = 150f, y = 150f, touchMajor = 105f, touchSize = 0.09f, pressure = 0.5f)
        }

        val result = CalibrationMath.calculateCalibration(tipSamples, zoomSamples, bufferFactor = 1.25f)
        assertEquals(20, result.sampleCount)
        assertTrue(result.recommendedMajorThresholdPx in 90f..100f)
        assertTrue(result.recommendedZoomMajorThresholdPx in 125f..135f)
    }

    @Test
    fun testCalculateCalibration_strictVsGentleBuffer() {
        val samples = (1..10).map {
            TouchSample(x = 100f, y = 100f, touchMajor = 80f, touchSize = 0.06f, pressure = 0.5f)
        }
        val strictResult = CalibrationMath.calculateCalibration(samples, bufferFactor = 1.15f)
        val gentleResult = CalibrationMath.calculateCalibration(samples, bufferFactor = 1.35f)

        assertTrue(strictResult.recommendedMajorThresholdPx < gentleResult.recommendedMajorThresholdPx)
        assertEquals(92f, strictResult.recommendedMajorThresholdPx, 1f)
        assertEquals(108f, gentleResult.recommendedMajorThresholdPx, 1f)
    }
}
