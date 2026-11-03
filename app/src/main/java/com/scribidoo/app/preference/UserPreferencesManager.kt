package com.scribidoo.app.preference

import android.content.Context
import android.content.SharedPreferences
import com.scribidoo.app.canvas.input.DominantHand
import com.scribidoo.app.canvas.input.PalmRejectionConfig

class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).apply()

    fun savePalmConfig(config: PalmRejectionConfig) {
        prefs.edit()
            .putBoolean(KEY_FINGER_DRAWING, config.isFingerDrawingEnabled)
            .putFloat(KEY_TOUCH_SIZE, config.touchSizeThreshold)
            .putFloat(KEY_TOUCH_MAJOR, config.touchMajorThresholdPx)
            .putFloat(KEY_TWO_FINGER_MAJOR, config.twoFingerMajorThresholdPx)
            .putFloat(KEY_SHIELD_RATIO, config.palmShieldBottomRatio)
            .putBoolean(KEY_SHIELD_ACTIVE, config.isPalmShieldActive)
            .putString(KEY_DOMINANT_HAND, config.dominantHand.name)
            .putBoolean(KEY_CONSTRAIN_DRAWING, config.constrainDrawingToPage)
            .putBoolean(KEY_SHAPE_RECOGNITION, config.isShapeRecognitionEnabled)
            .putLong(KEY_SHAPE_HOLD_DURATION, config.shapeHoldDurationMs)
            .putStringSet(KEY_HIDDEN_TOOLS, config.hiddenTools)
            .apply()
    }

    fun loadPalmConfig(): PalmRejectionConfig {
        val defaults = PalmRejectionConfig()
        val handStr = prefs.getString(KEY_DOMINANT_HAND, defaults.dominantHand.name)
        val hand = try {
            DominantHand.valueOf(handStr ?: defaults.dominantHand.name)
        } catch (_: Exception) {
            defaults.dominantHand
        }

        return PalmRejectionConfig(
            isFingerDrawingEnabled = prefs.getBoolean(KEY_FINGER_DRAWING, defaults.isFingerDrawingEnabled),
            touchSizeThreshold = prefs.getFloat(KEY_TOUCH_SIZE, defaults.touchSizeThreshold),
            touchMajorThresholdPx = prefs.getFloat(KEY_TOUCH_MAJOR, defaults.touchMajorThresholdPx),
            twoFingerMajorThresholdPx = prefs.getFloat(KEY_TWO_FINGER_MAJOR, defaults.twoFingerMajorThresholdPx),
            palmShieldBottomRatio = prefs.getFloat(KEY_SHIELD_RATIO, defaults.palmShieldBottomRatio),
            isPalmShieldActive = prefs.getBoolean(KEY_SHIELD_ACTIVE, defaults.isPalmShieldActive),
            dominantHand = hand,
            constrainDrawingToPage = prefs.getBoolean(KEY_CONSTRAIN_DRAWING, defaults.constrainDrawingToPage),
            isShapeRecognitionEnabled = prefs.getBoolean(KEY_SHAPE_RECOGNITION, defaults.isShapeRecognitionEnabled),
            shapeHoldDurationMs = prefs.getLong(KEY_SHAPE_HOLD_DURATION, defaults.shapeHoldDurationMs),
            hiddenTools = prefs.getStringSet(KEY_HIDDEN_TOOLS, defaults.hiddenTools) ?: defaults.hiddenTools
        )
    }

    fun saveCalibratedTipMetrics(majorPx: Float, size: Float) {
        prefs.edit()
            .putFloat(KEY_CALIBRATED_MAJOR, majorPx)
            .putFloat(KEY_CALIBRATED_SIZE, size)
            .apply()
    }

    fun loadCalibratedTipMetrics(): Pair<Float, Float>? {
        if (!prefs.contains(KEY_CALIBRATED_MAJOR) || !prefs.contains(KEY_CALIBRATED_SIZE)) {
            return null
        }
        val major = prefs.getFloat(KEY_CALIBRATED_MAJOR, 0f)
        val size = prefs.getFloat(KEY_CALIBRATED_SIZE, 0f)
        return if (major > 0f) Pair(major, size) else null
    }

    companion object {
        private const val PREFS_NAME = "scribidoo_user_prefs"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_FINGER_DRAWING = "finger_drawing_enabled"
        private const val KEY_TOUCH_SIZE = "touch_size_threshold"
        private const val KEY_TOUCH_MAJOR = "touch_major_threshold_px"
        private const val KEY_TWO_FINGER_MAJOR = "two_finger_major_threshold_px"
        private const val KEY_SHIELD_RATIO = "palm_shield_bottom_ratio"
        private const val KEY_SHIELD_ACTIVE = "palm_shield_active"
        private const val KEY_DOMINANT_HAND = "dominant_hand"
        private const val KEY_CONSTRAIN_DRAWING = "constrain_drawing_to_page"
        private const val KEY_SHAPE_RECOGNITION = "shape_recognition_enabled"
        private const val KEY_SHAPE_HOLD_DURATION = "shape_hold_duration_ms"
        private const val KEY_HIDDEN_TOOLS = "hidden_tools"
        private const val KEY_CALIBRATED_MAJOR = "calibrated_tip_major_px"
        private const val KEY_CALIBRATED_SIZE = "calibrated_tip_size"
    }
}
