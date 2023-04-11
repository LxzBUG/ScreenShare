package org.loka.screensharekit

data class EncodeConfig(var width:Int = 1080,var height:Int = 1920,var frameRate:Int = 60,var bitrate:Int = 1000000,var audioCapture:Boolean = true,var sampleRate:Int = 16000,var channels:Int = 2)
