package com.scribidoo.app.canvas.input

enum class DominantHand {
    RIGHT_HANDED,
    LEFT_HANDED
}

data class PalmRejectionConfig(
    val isFingerDrawingEnabled: Boolean = true,
    val touchSizeThreshold: Float = 0.35f,
    val touchMajorThresholdPx: Float = 120f,
    val palmShieldBottomRatio: Float = 0.28f,
    val isPalmShieldActive: Boolean = true,
    val dominantHand: DominantHand = DominantHand.RIGHT_HANDED
)
