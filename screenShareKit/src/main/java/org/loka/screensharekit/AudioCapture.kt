package org.loka.screensharekit

import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.loka.screensharekit.ScreenShareKit.packageUid
import java.nio.ByteBuffer

class AudioCapture(
    private val channels: Int,
    private val sampleRate: Int,
    private val mediaProjection: MediaProjection,
    private val errorCallback: AudioRecordErrorCallback?,
    private val audioFrameListener: AudioFrameListener
) : IAudioCapture {
    private var byteBuffer: ByteBuffer? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: AudioRecordThread? = null

    @Volatile
    private var microphoneMute = false
    private var emptyBytes: ByteArray?=null
    @TargetApi(29)
    override fun startRecording(): Boolean {
        Log.d("AudioCapture", "startRecording")
        return if (initRecording() <= 0) {
            false
        } else {
            try {
                audioRecord!!.startRecording()
            } catch (var2: IllegalStateException) {
                reportWebRtcAudioRecordStartError(
                    AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION,
                    "AudioRecord.startRecording failed: " + var2.message
                )
                return false
            }
            if (audioRecord!!.recordingState != 3) {
                reportWebRtcAudioRecordStartError(
                    AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH,
                    "AudioRecord.startRecording failed - incorrect state :" + audioRecord!!.recordingState
                )
                false
            } else {
                audioThread = AudioRecordThread("AudioRecordJavaThread")
                audioThread!!.start()
                true
            }
        }
    }

    @TargetApi(29)
    fun initRecording(): Int {
        Log.d(
            "AudioCapture",
            "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")"
        )
        return if (audioRecord != null) {
            reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.")
            -1
        } else {
            val bytesPerFrame = channels * 2
            val framesPerBuffer = sampleRate / 100
            byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer)
            if (byteBuffer?.hasArray() == false) {
                reportWebRtcAudioRecordInitError("ByteBuffer does not have backing array.")
                -1
            } else {
                Log.d("AudioCapture", "byteBuffer.capacity: " + byteBuffer?.capacity())
                emptyBytes = ByteArray(byteBuffer?.capacity()?:0)
                val channelConfig = channelCountToConfiguration(channels)
                val minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferSize != -1 && minBufferSize != -2) {
                    Log.d("AudioCapture", "AudioRecord.getMinBufferSize: $minBufferSize")
                    val bufferSizeInBytes =
                        Math.max(2 * minBufferSize, byteBuffer?.capacity()?:0)
                    Log.d("AudioCapture", "bufferSizeInBytes: $bufferSizeInBytes")
                    val config =
                        AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .excludeUid(packageUid).build()
                    val audioFormat = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate).setChannelMask(channelConfig).build()
                    audioRecord = AudioRecord.Builder().setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSizeInBytes)
                        .setAudioPlaybackCaptureConfig(config)
                        .build()
                    if (audioRecord != null && audioRecord!!.state == 1) {
                        logMainParameters()
                        framesPerBuffer
                    } else {
                        reportWebRtcAudioRecordInitError("Failed to create a new AudioRecord instance")
                        releaseAudioResources()
                        -1
                    }
                } else {
                    reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: $minBufferSize")
                    -1
                }
            }
        }
    }

    override fun stopRecording() {
        Log.d("AudioCapture", "stopRecording")
        if (audioThread != null) {
            audioThread!!.stopThread()
            if (!joinUninterruptibly(audioThread, 2000L)) {
                Log.e("AudioCapture", "Join of AudioRecordJavaThread timed out")
            }
            audioThread = null
        }
        releaseAudioResources()
    }

    fun joinUninterruptibly(thread: Thread?, timeoutMs: Long): Boolean {
        val startTimeMs = SystemClock.elapsedRealtime()
        var timeRemainingMs = timeoutMs
        var wasInterrupted = false
        while (timeRemainingMs > 0) {
            try {
                thread!!.join(timeRemainingMs)
                break
            } catch (e: InterruptedException) {
                // Someone is asking us to return early at our convenience. We can't cancel this operation,
                // but we should preserve the information and pass it along.
                wasInterrupted = true
                val elapsedTimeMs = SystemClock.elapsedRealtime() - startTimeMs
                timeRemainingMs = timeoutMs - elapsedTimeMs
            }
        }
        // Pass interruption information along.
        if (wasInterrupted) {
            Thread.currentThread().interrupt()
        }
        return !thread!!.isAlive
    }

    private fun logMainParameters() {
        Log.d(
            "AudioCapture",
            "AudioRecord: session ID: " + audioRecord!!.audioSessionId + ", channels: " + audioRecord!!.channelCount + ", sample rate: " + audioRecord!!.sampleRate
        )
    }

    private fun channelCountToConfiguration(channels: Int): Int {
        return if (channels == 1) 16 else 12
    }

    fun setMicrophoneMute(mute: Boolean) {
        Log.e("AudioCapture", "setMicrophoneMute($mute)")
        microphoneMute = mute
    }

    private fun releaseAudioResources() {
        Log.d("AudioCapture", "releaseAudioResources")
        if (audioRecord != null) {
            audioRecord!!.release()
            audioRecord = null
        }
    }

    private fun reportWebRtcAudioRecordInitError(errorMessage: String) {
        Log.e("AudioCapture", "Init recording error: $errorMessage")
        if (errorCallback != null) {
            errorCallback.onWebRtcAudioRecordInitError(errorMessage)
        }
    }

    private fun reportWebRtcAudioRecordStartError(
        errorCode: AudioRecordStartErrorCode,
        errorMessage: String
    ) {
        Log.e("AudioCapture", "Start recording error: $errorCode. $errorMessage")
        if (errorCallback != null) {
            errorCallback.onWebRtcAudioRecordStartError(errorCode, errorMessage)
        }
    }

    private fun reportWebRtcAudioRecordError(errorMessage: String) {
        Log.e("AudioCapture", "Run-time recording error: $errorMessage")
        if (errorCallback != null) {
            errorCallback.onWebRtcAudioRecordError(errorMessage)
        }
    }

    private inner class AudioRecordThread(name: String?) : Thread(name) {
        @Volatile
        private var keepAlive = true
        val threadInfo: String
            get() = "@[name=" + currentThread().name + ", id=" + currentThread().id + "]"

        override fun run() {
            Process.setThreadPriority(-19)
            assertTrue(audioRecord!!.recordingState == 3)
            Log.d("AudioCapture", "audioRecordState " + audioRecord!!.recordingState)
            while (keepAlive) {
                val bytesRead = audioRecord!!.read(byteBuffer!!, byteBuffer!!.capacity())
                if (bytesRead == byteBuffer!!.capacity()) {
                    if (microphoneMute) {
                        byteBuffer?.clear()
                        byteBuffer?.put(emptyBytes)
                    }
                    if (keepAlive) {
                        val audioData = ByteArray(bytesRead)
                        byteBuffer?.rewind()
                        byteBuffer!![audioData]
                        audioFrameListener.onAudioData(audioData)
                    }
                    Log.d("AudioCapture", "Read $bytesRead bytes of audio data")
                } else {
                    val errorMessage = "AudioRecord.read failed: $bytesRead"
                    Log.e("AudioCapture", errorMessage)
                    if (bytesRead == -3) {
                        keepAlive = false
                        reportWebRtcAudioRecordError(errorMessage)
                    }
                }
            }
            try {
                if (audioRecord != null) {
                    audioRecord!!.stop()
                }
            } catch (var3: IllegalStateException) {
                Log.e("AudioCapture", "AudioRecord.stop failed: " + var3.message)
            }
        }

        fun stopThread() {
            Log.d("AudioCapture", "stopThread")
            keepAlive = false
        }
    }

    enum class AudioRecordStartErrorCode {
        AUDIO_RECORD_START_EXCEPTION, AUDIO_RECORD_START_STATE_MISMATCH
    }

    companion object {
        private const val TAG = "AudioCapture"
        private const val BITS_PER_SAMPLE = 16
        private const val CALLBACK_BUFFER_SIZE_MS = 10
        private const val BUFFERS_PER_SECOND = 100
        private const val BUFFER_SIZE_FACTOR = 2
        private const val AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000L
        private fun assertTrue(condition: Boolean) {
            if (!condition) {
                throw AssertionError("Expected condition to be true")
            }
        }
    }
}