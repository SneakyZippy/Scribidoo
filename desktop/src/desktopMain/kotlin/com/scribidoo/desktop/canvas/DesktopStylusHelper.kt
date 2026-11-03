package com.scribidoo.desktop.canvas

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import kotlin.math.pow

object DesktopStylusHelper {

    fun computeEffectivePressure(
        change: PointerInputChange,
        curveSetting: String = "LINEAR"
    ): Float {
        if (change.type != PointerType.Stylus) return 1.0f

        val raw = change.pressure.coerceIn(0.01f, 1.0f)
        return when (curveSetting.uppercase()) {
            "OFF" -> 1.0f
            "SOFT" -> raw.toDouble().pow(0.55).toFloat()
            "FIRM" -> raw.toDouble().pow(1.65).toFloat()
            else -> raw // LINEAR
        }
    }

    fun isBarrelButtonPressed(
        change: PointerInputChange,
        isSecondaryPressed: Boolean
    ): Boolean {
        return change.type == PointerType.Stylus && isSecondaryPressed
    }
}
