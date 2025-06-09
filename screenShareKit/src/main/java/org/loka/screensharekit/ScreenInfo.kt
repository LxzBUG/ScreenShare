package org.loka.screensharekit

import org.loka.screensharekit.ScreenInfo

class ScreenInfo(val deviceSize: Size, val videoSize: Size, private val rotated: Boolean) {
    fun withRotation(rotation: Int): ScreenInfo {
        val newRotated = rotation and 1 != 0
        return if (rotated == newRotated) {
            this
        } else ScreenInfo(deviceSize.rotate(), videoSize.rotate(), newRotated)
    }
}