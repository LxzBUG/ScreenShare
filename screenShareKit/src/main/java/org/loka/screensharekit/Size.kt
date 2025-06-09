package org.loka.screensharekit

import android.graphics.Rect
import java.util.*

class Size(val width: Int, val height: Int) {

    fun rotate(): Size {
        return Size(height, width)
    }

    fun toRect(): Rect {
        return Rect(0, 0, width, height)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val size = o as Size
        return (width == size.width
                && height == size.height)
    }

    override fun hashCode(): Int {
        return Objects.hash(width, height)
    }

    override fun toString(): String {
        return ("Size{"
                + "width=" + width
                + ", height=" + height
                + '}')
    }
}