package org.loka.screensharekit;

import android.annotation.TargetApi;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Process;
import android.util.Log;

import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AudioCapture implements IAudioCapture {
    private static final String TAG = "AudioCapture";
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    private static final int BUFFERS_PER_SECOND = 100;
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000L;
    private final int channels;
    private final int sampleRate;
    @NonNull
    private final MediaProjection mediaProjection;
    @NonNull
    private final AudioFrameListener   audioFrameListener;
    @Nullable
    private ByteBuffer byteBuffer;
    @Nullable
    private AudioRecord audioRecord;
    @Nullable
    private AudioRecordThread audioThread = null;
    private volatile boolean microphoneMute = false;
    private byte[] emptyBytes;
    @Nullable
    private final AudioRecordErrorCallback errorCallback;

    public AudioCapture(int channels, int sampleRate, @NonNull MediaProjection mediaProjection, @Nullable AudioRecordErrorCallback errorCallback, @NonNull AudioFrameListener listener) {
        this.mediaProjection = mediaProjection;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.audioFrameListener = listener;
        this.errorCallback = errorCallback;
    }

    @TargetApi(29)
    public boolean startRecording() {
        Log.d("AudioCapture", "startRecording");
        if (this.initRecording() <= 0) {
            return false;
        } else {
            try {
                this.audioRecord.startRecording();
            } catch (IllegalStateException var2) {
                this.reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION, "AudioRecord.startRecording failed: " + var2.getMessage());
                return false;
            }

            if (this.audioRecord.getRecordingState() != 3) {
                this.reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH, "AudioRecord.startRecording failed - incorrect state :" + this.audioRecord.getRecordingState());
                return false;
            } else {
                this.audioThread = new AudioRecordThread("AudioRecordJavaThread");
                this.audioThread.start();
                return true;
            }
        }
    }

    @TargetApi(29)
    int initRecording() {
        Log.d("AudioCapture", "initRecording(sampleRate=" + this.sampleRate + ", channels=" + this.channels + ")");
        if (this.audioRecord != null) {
            this.reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.");
            return -1;
        } else {
            int bytesPerFrame = this.channels * 2;
            int framesPerBuffer = this.sampleRate / 100;
            this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
            if (!this.byteBuffer.hasArray()) {
                this.reportWebRtcAudioRecordInitError("ByteBuffer does not have backing array.");
                return -1;
            } else {
                Log.d("AudioCapture", "byteBuffer.capacity: " + this.byteBuffer.capacity());
                this.emptyBytes = new byte[this.byteBuffer.capacity()];
                int channelConfig = this.channelCountToConfiguration(this.channels);
                int minBufferSize = AudioRecord.getMinBufferSize(this.sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
                if (minBufferSize != -1 && minBufferSize != -2) {
                    Log.d("AudioCapture", "AudioRecord.getMinBufferSize: " + minBufferSize);
                    int bufferSizeInBytes = Math.max(2 * minBufferSize, this.byteBuffer.capacity());
                    Log.d("AudioCapture", "bufferSizeInBytes: " + bufferSizeInBytes);
                    AudioPlaybackCaptureConfiguration config = (new AudioPlaybackCaptureConfiguration.Builder(this.mediaProjection)).addMatchingUsage(AudioAttributes.USAGE_MEDIA).build();
                    AudioFormat audioFormat = (new AudioFormat.Builder()).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(this.sampleRate).setChannelMask(channelConfig).build();
                    this.audioRecord = new AudioRecord.Builder().setAudioFormat(audioFormat).setBufferSizeInBytes(bufferSizeInBytes).setAudioPlaybackCaptureConfig(config).build();
                    if (this.audioRecord != null && this.audioRecord.getState() == 1) {
                        this.logMainParameters();
                        return framesPerBuffer;
                    } else {
                        this.reportWebRtcAudioRecordInitError("Failed to create a new AudioRecord instance");
                        this.releaseAudioResources();
                        return -1;
                    }
                } else {
                    this.reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: " + minBufferSize);
                    return -1;
                }
            }
        }
    }

    public void stopRecording() {
        Log.d("AudioCapture", "stopRecording");
        if (this.audioThread != null) {
            this.audioThread.stopThread();
            if (!ThreadUtils.joinUninterruptibly(this.audioThread, 2000L)) {
               Log.e("AudioCapture", "Join of AudioRecordJavaThread timed out");
            }
            this.audioThread = null;
        }

        this.releaseAudioResources();
    }

    private void logMainParameters() {
        Log.d("AudioCapture", "AudioRecord: session ID: " + this.audioRecord.getAudioSessionId() + ", channels: " + this.audioRecord.getChannelCount() + ", sample rate: " + this.audioRecord.getSampleRate());
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private int channelCountToConfiguration(int channels) {
        return channels == 1 ? 16 : 12;
    }

    public void setMicrophoneMute(boolean mute) {
       Log.e("AudioCapture", "setMicrophoneMute(" + mute + ")");
        this.microphoneMute = mute;
    }

    private void releaseAudioResources() {
        Log.d("AudioCapture", "releaseAudioResources");
        if (this.audioRecord != null) {
            this.audioRecord.release();
            this.audioRecord = null;
        }

    }

    private void reportWebRtcAudioRecordInitError(String errorMessage) {
       Log.e("AudioCapture", "Init recording error: " + errorMessage);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioRecordInitError(errorMessage);
        }

    }

    private void reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode errorCode, String errorMessage) {
       Log.e("AudioCapture", "Start recording error: " + errorCode + ". " + errorMessage);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioRecordStartError(errorCode, errorMessage);
        }

    }

    private void reportWebRtcAudioRecordError(String errorMessage) {
       Log.e("AudioCapture", "Run-time recording error: " + errorMessage);
        if (this.errorCallback != null) {
            this.errorCallback.onWebRtcAudioRecordError(errorMessage);
        }

    }

    private class AudioRecordThread extends Thread {
        private volatile boolean keepAlive = true;

        public AudioRecordThread(String name) {
            super(name);
        }

        String getThreadInfo() {
            return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId() + "]";
        }

        public void run() {
            Process.setThreadPriority(-19);
            Log.d("AudioCapture", "AudioRecordThread " + this.getThreadInfo());
            AudioCapture.assertTrue(AudioCapture.this.audioRecord.getRecordingState() == 3);
            Log.d("AudioCapture", "audioRecordState " + AudioCapture.this.audioRecord.getRecordingState());
            while(this.keepAlive) {
                int bytesRead = AudioCapture.this.audioRecord.read(AudioCapture.this.byteBuffer, AudioCapture.this.byteBuffer.capacity());
                if (bytesRead == AudioCapture.this.byteBuffer.capacity()) {
                    if (AudioCapture.this.microphoneMute) {
                        AudioCapture.this.byteBuffer.clear();
                        AudioCapture.this.byteBuffer.put(AudioCapture.this.emptyBytes);
                    }

                    if (this.keepAlive) {
                        byte[] audioData = new byte[bytesRead];
                        byteBuffer.rewind();
                        byteBuffer.get(audioData);
                        AudioCapture.this.audioFrameListener.onAudioData(audioData);
                    }
                    Log.d("AudioCapture", "Read " + bytesRead + " bytes of audio data");
                } else {
                    String errorMessage = "AudioRecord.read failed: " + bytesRead;
                   Log.e("AudioCapture", errorMessage);
                    if (bytesRead == -3) {
                        this.keepAlive = false;
                        AudioCapture.this.reportWebRtcAudioRecordError(errorMessage);
                    }
                }
            }

            try {
                if (AudioCapture.this.audioRecord != null) {
                    AudioCapture.this.audioRecord.stop();
                }
            } catch (IllegalStateException var3) {
               Log.e("AudioCapture", "AudioRecord.stop failed: " + var3.getMessage());
            }

        }

        public void stopThread() {
            Log.d("AudioCapture", "stopThread");
            this.keepAlive = false;
        }
    }

    public static enum AudioRecordStartErrorCode {
        AUDIO_RECORD_START_EXCEPTION,
        AUDIO_RECORD_START_STATE_MISMATCH;

        private AudioRecordStartErrorCode() {
        }
    }
}

