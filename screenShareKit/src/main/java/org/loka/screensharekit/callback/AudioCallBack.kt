package org.loka.screensharekit.callback

import java.nio.ByteBuffer

fun interface AudioCallBack {
    fun onAudio(buffer: ByteArray?,ts: Long)
}