package org.loka.screensharekit

public interface IAudioCapture {
    fun startRecording(): Boolean
    fun stopRecording()
}