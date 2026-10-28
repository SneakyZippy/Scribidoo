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
     * Determines whether a specific pointer touch is an accidental palm/hand contact.
     */
    fun isPalmTouch(
        event: MotionEvent,
        pointerIndex: Int,
        containerWidth: Float = 0f,
        containerHeight: Float = 0f
    ): Boolean {
        // 1. Hardware tool type: Stylus is never a palm
        val toolType = event.getToolType(pointerIndex)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return false
        }
        // Native Android palm recognition (TOOL_TYPE_PALM = 4)
        if (toolType == 4) {
            return true
        }

        val touchSize = event.getSize(pointerIndex)
        val touchMajor = event.getTouchMajor(pointerIndex)

        // 2. Physical contact patch check:
        // A normal finger on high-DPI touchscreens (like 420 dpi Pixel) has touchMajor ~70 - 150px and size ~0.02 - 0.12.
        // A rested palm or heel of hand has touchMajor >= 190px or size >= 0.18.
        if (touchMajor >= config.touchMajorThresholdPx) return true
        if (touchSize >= config.touchSizeThreshold) return true

        return false
    }

    /**
     * Checks if a two-pointer event is a genuine two-finger gesture (pan/pinch-zoom),
     * rather than a palm resting on the glass alongside a writing finger.
     */
    fun isGenuineTwoFingerGesture(event: MotionEvent): Boolean {
        if (event.pointerCount != 2) return false

        // If either tool type is a stylus or hardware palm, it's not a two-finger gesture
        val t0 = event.getToolType(0)
        val t1 = event.getToolType(1)
        if (t0 == MotionEvent.TOOL_TYPE_STYLUS || t1 == MotionEvent.TOOL_TYPE_STYLUS) return false
        if (t0 == 4 || t1 == 4) return false

        val m0 = event.getTouchMajor(0)
        val m1 = event.getTouchMajor(1)
        val s0 = event.getSize(0)
        val s1 = event.getSize(1)

        // Absolute limits for legitimate fingertips
        if (m0 > config.touchMajorThresholdPx || m1 > config.touchMajorThresholdPx) return false
        if (s0 > config.touchSizeThreshold || s1 > config.touchSizeThreshold) return false

        // Relative symmetry: genuine two-finger pinch/zoom contacts are almost identical in size (ratio < 1.75).
        // A resting palm alongside a fingertip has a large size disparity (ratio > 1.8).
        val maxM = maxOf(m0, m1)
        val minM = minOf(m0, m1).coerceAtLeast(1f)
        if (maxM / minM > 1.75f) return false

        val maxS = maxOf(s0, s1)
        val minS = minOf(s0, s1).coerceAtLeast(0.01f)
        if (maxS / minS > 2.0f) return false

        return true
    }

    /**
     * When multiple pointers are touching (e.g. palm resting while writing with finger/pen),
     * finds the index of the legitimate drawing pointer (the sharpest, smallest contact).
     */
    fun findBestDrawingPointerIndex(event: MotionEvent): Int? {
        val count = event.pointerCount
        if (count == 0) return null

        // 1. Stylus always has priority
        for (i in 0 until count) {
            val t = event.getToolType(i)
            if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER) {
                return i
            }
        }

        // 2. Filter out definite palm touches
        val nonPalms = (0 until count).filterNot { isPalmTouch(event, it) }
        if (nonPalms.isEmpty()) return null
        if (nonPalms.size == 1) return nonPalms.first()

        // 3. If multiple candidates exist, use dominant hand geometry + contact size:
        if (nonPalms.size == 2) {
            val i0 = nonPalms[0]
            val i1 = nonPalms[1]
            val p0X = event.getX(i0)
            val p0Y = event.getY(i0)
            val p1X = event.getX(i1)
            val p1Y = event.getY(i1)

            // Natural writing posture: the writing tip is higher up (smaller Y) than the resting palm.
            // For RIGHT_HANDED: resting palm rests to the bottom-right (larger Y, larger X).
            // For LEFT_HANDED: resting palm rests to the bottom-left (larger Y, smaller X).
            val spatialBias = if (config.dominantHand == DominantHand.RIGHT_HANDED) {
                (p1Y - p0Y) + 0.5f * (p1X - p0X)
            } else {
                (p1Y - p0Y) + 0.5f * (p0X - p1X)
            }

            // Strong geometric indication (> 50px offset)
            if (spatialBias > 50f) return i0
            if (spatialBias < -50f) return i1
        }

        return nonPalms.minByOrNull { event.getTouchMajor(it) }
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
            // If finger drawing is turned OFF, finger only pans/zooms
            if (!config.isFingerDrawingEnabled) {
                return InputVerdict.ACCEPT_FOR_VIEWPORT
            }

            if (isPalmTouch(event, pointerIndex, containerWidth, containerHeight)) {
                return InputVerdict.DISCARD_PALM
            }

            return InputVerdict.ACCEPT_FOR_DRAWING
        }

        return InputVerdict.ACCEPT_FOR_DRAWING
    }
}
