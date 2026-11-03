package com.scribidoo.app.onboarding

data class TouchSample(
    val x: Float,
    val y: Float,
    val touchMajor: Float,
    val touchSize: Float,
    val pressure: Float
)

data class CalibrationResult(
    val measuredMajorPx: Float,
    val measuredSize: Float,
    val recommendedMajorThresholdPx: Float,
    val recommendedSizeThreshold: Float,
    val measuredZoomMajorPx: Float = 0f,
    val recommendedZoomMajorThresholdPx: Float = 220f,
    val sampleCount: Int = 0
)

object CalibrationMath {

    fun calculateCalibration(
        tipSamples: List<TouchSample>,
        zoomSamples: List<TouchSample> = emptyList(),
        bufferFactor: Float = 1.25f
    ): CalibrationResult {
        val validTip = tipSamples.filter { it.touchMajor > 5f && it.touchSize > 0.005f }
        if (validTip.size < 3) {
            return CalibrationResult(
                measuredMajorPx = 100f,
                measuredSize = 0.08f,
                recommendedMajorThresholdPx = 130f,
                recommendedSizeThreshold = 0.14f,
                measuredZoomMajorPx = 110f,
                recommendedZoomMajorThresholdPx = 200f,
                sampleCount = validTip.size
            )
        }

        val sortedMajor = validTip.map { it.touchMajor }.sorted()
        val sortedSize = validTip.map { it.touchSize }.sorted()

        val p90Major = percentile(sortedMajor, 0.90f)
        val p90Size = percentile(sortedSize, 0.90f)
        val medianMajor = percentile(sortedMajor, 0.50f)
        val medianSize = percentile(sortedSize, 0.50f)

        // Tight buffer (e.g. 1.25x for +25% headroom)
        val recommendedMajor = (p90Major * bufferFactor).coerceIn(75f, 240f)
        val recommendedSize = (p90Size * (bufferFactor + 0.10f)).coerceIn(0.10f, 0.40f)

        // Two-finger zoom analysis
        val validZoom = zoomSamples.filter { it.touchMajor > 5f && it.touchSize > 0.005f }
        val medianZoomMajor = if (validZoom.isNotEmpty()) {
            percentile(validZoom.map { it.touchMajor }.sorted(), 0.50f)
        } else {
            medianMajor * 1.15f
        }
        val p90ZoomMajor = if (validZoom.isNotEmpty()) {
            percentile(validZoom.map { it.touchMajor }.sorted(), 0.90f)
        } else {
            p90Major * 1.20f
        }
        val recommendedZoomLimit = maxOf(recommendedMajor * 1.15f, p90ZoomMajor * 1.20f).coerceIn(95f, 260f)

        return CalibrationResult(
            measuredMajorPx = medianMajor,
            measuredSize = medianSize,
            recommendedMajorThresholdPx = recommendedMajor,
            recommendedSizeThreshold = recommendedSize,
            measuredZoomMajorPx = medianZoomMajor,
            recommendedZoomMajorThresholdPx = recommendedZoomLimit,
            sampleCount = validTip.size + validZoom.size
        )
    }

    private fun percentile(sortedList: List<Float>, rank: Float): Float {
        if (sortedList.isEmpty()) return 0f
        val index = ((sortedList.size - 1) * rank).toInt().coerceIn(0, sortedList.size - 1)
        return sortedList[index]
    }
}
