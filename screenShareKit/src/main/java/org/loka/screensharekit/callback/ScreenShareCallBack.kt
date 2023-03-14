package org.loka.screensharekit.callback

import org.loka.screensharekit.ErrorInfo
import java.nio.ByteBuffer

interface ScreenShareCallBack {
    fun onStart()
    fun onError(error:ErrorInfo)
    fun onH264(buffer: ByteBuffer, isKeyFrame: Boolean, width:Int, height:Int, ts: Long)
}