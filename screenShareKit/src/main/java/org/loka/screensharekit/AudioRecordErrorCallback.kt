package org.loka.screensharekit

interface AudioRecordErrorCallback {
    fun onWebRtcAudioRecordInitError(var1: String?)

    fun onWebRtcAudioRecordStartError(
        var1: AudioCapture.AudioRecordStartErrorCode?,
        var2: String?
    )

    fun onWebRtcAudioRecordError(var1: String?)
}