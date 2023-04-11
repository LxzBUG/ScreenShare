package org.loka.screensharekit

import java.nio.ByteBuffer

interface AudioFrameListener {
    fun onAudioData(var1:ByteArray)
}