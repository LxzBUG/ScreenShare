package org.loka.screensharekit

import android.os.IInterface

class DisplayManager(private val manager: IInterface) {
    // width and height already take the rotation into account
    val displayInfo: DisplayInfo
        get() = try {
            val displayInfo =
                manager.javaClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(
                    manager, 0
                )
            val cls: Class<*> = displayInfo.javaClass
            // width and height already take the rotation into account
            val width = cls.getDeclaredField("logicalWidth").getInt(displayInfo)
            val height = cls.getDeclaredField("logicalHeight").getInt(displayInfo)
            val rotation = cls.getDeclaredField("rotation").getInt(displayInfo)
            DisplayInfo(Size(width, height), rotation)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
}