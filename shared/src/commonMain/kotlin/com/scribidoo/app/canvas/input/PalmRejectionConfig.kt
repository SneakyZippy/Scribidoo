package com.scribidoo.app.canvas.input

enum class DominantHand {
    RIGHT_HANDED,
    LEFT_HANDED
}

data class PalmRejectionConfig(
    val isFingerDrawingEnabled: Boolean = true,
    val touchSizeThreshold: Float = 0.20f,
    val touchMajorThresholdPx: Float = 200f,
    val twoFingerMajorThresholdPx: Float = 220f,
    val palmShieldBottomRatio: Float = 0.28f,
    val isPalmShieldActive: Boolean = true,
    val dominantHand: DominantHand = DominantHand.RIGHT_HANDED,
    val constrainDrawingToPage: Boolean = false,
    val isShapeRecognitionEnabled: Boolean = true,
    val shapeHoldDurationMs: Long = 450L,
    val hiddenTools: Set<String> = emptySet()
)
