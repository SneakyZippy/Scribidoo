package com.scribidoo.app.canvas.input

import android.view.MotionEvent

enum class ClassifiedTool {
    STYLUS,
    FINGER,
    MOUSE,
    UNKNOWN
}

enum class InputVerdict {
    ACCEPT_FOR_DRAWING,
    ACCEPT_FOR_VIEWPORT,
    DISCARD_PALM
}

enum class DominantHand {
    RIGHT_HANDED,
    LEFT_HANDED
}

data class PalmRejectionConfig(
    val isFingerDrawingEnabled: Boolean = true, // Enabled by default for touch screens
    val touchSizeThreshold: Float = 0.35f, // Slider: 0.15 to 0.60
    val touchMajorThresholdPx: Float = 120f, // Slider: 60 to 260
    val palmShieldBottomRatio: Float = 0.28f, // Slider: 0.0 to 0.50 (height exclusion)
    val isPalmShieldActive: Boolean = true,
    val dominantHand: DominantHand = DominantHand.RIGHT_HANDED
)

class InputClassifier(
    var config: PalmRejectionConfig = PalmRejectionConfig()
) {
    /**
     * Identifies tool type from Android MotionEvent.
     */
    fun classifyTool(event: MotionEvent, pointerIndex: Int): ClassifiedTool {
        return when (event.getToolType(pointerIndex)) {
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_ERASER -> ClassifiedTool.STYLUS

            MotionEvent.TOOL_TYPE_FINGER -> ClassifiedTool.FINGER
            MotionEvent.TOOL_TYPE_MOUSE -> ClassifiedTool.MOUSE
            else -> ClassifiedTool.UNKNOWN
        }
    }

    /**
     * Evaluates whether an incoming touch point is legitimate drawing input,
     * viewport gesture (pan/pinch), or accidental palm touch.
     */
    fun evaluateTouch(
        event: MotionEvent,
        pointerIndex: Int,
        containerWidth: Float,
        containerHeight: Float,
        activePointersCount: Int
    ): InputVerdict {
        val tool = classifyTool(event, pointerIndex)

        // 1. Active Stylus: Native hardware digitizer separation
        if (tool == ClassifiedTool.STYLUS) {
            return InputVerdict.ACCEPT_FOR_DRAWING
        }

        // 2. Multi-touch handling: 2 fingers are reserved for pan & zoom gestures
        if (activePointersCount >= 2) {
            return InputVerdict.ACCEPT_FOR_VIEWPORT
        }

        // 3. Single finger handling
        if (tool == ClassifiedTool.FINGER) {
            // If "Draw with Finger / Cheap Pen" is turned OFF, finger only pans/zooms
            if (!config.isFingerDrawingEnabled) {
                return InputVerdict.ACCEPT_FOR_VIEWPORT
            }

            // Finger drawing IS enabled: Apply software palm rejection heuristics
            val touchSize = event.getSize(pointerIndex)
            val touchMajor = event.getTouchMajor(pointerIndex)
            val xPos = event.getX(pointerIndex)
            val yPos = event.getY(pointerIndex)

            // A. Contact area check: Palms have large contact ellipses
            if (touchMajor > config.touchMajorThresholdPx) {
                return InputVerdict.DISCARD_PALM
            }

            if (touchSize > config.touchSizeThreshold && touchMajor > 0f) {
                return InputVerdict.DISCARD_PALM
            }

            // B. Handedness Palm Shield Heuristic: Rejects accidental rests in the writing quadrant
            if (config.isPalmShieldActive && containerHeight > 0f && containerWidth > 0f) {
                val shieldCutoffY = containerHeight * (1.0f - config.palmShieldBottomRatio)
                if (yPos > shieldCutoffY && touchSize > 0.16f) {
                    when (config.dominantHand) {
                        DominantHand.RIGHT_HANDED -> {
                            // Right-handed writers rest palm towards the bottom-right
                            if (xPos > containerWidth * 0.35f) {
                                return InputVerdict.DISCARD_PALM
                            }
                        }
                        DominantHand.LEFT_HANDED -> {
                            // Left-handed writers rest palm towards the bottom-left
                            if (xPos < containerWidth * 0.65f) {
                                return InputVerdict.DISCARD_PALM
                            }
                        }
                    }
                }
            }

            return InputVerdict.ACCEPT_FOR_DRAWING
        }

        return InputVerdict.ACCEPT_FOR_DRAWING
    }
}
