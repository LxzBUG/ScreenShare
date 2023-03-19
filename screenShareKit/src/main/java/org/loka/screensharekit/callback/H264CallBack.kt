package org.loka.screensharekit.callback

import java.nio.ByteBuffer

fun interface H264CallBack {
    fun onH264(buffer: ByteBuffer, isKeyFrame: Boolean, width:Int, height:Int, ts: Long)
}