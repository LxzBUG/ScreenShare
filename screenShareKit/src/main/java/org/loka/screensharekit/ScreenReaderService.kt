package org.loka.screensharekit

import android.app.Activity.RESULT_CANCELED
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Surface
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ScreenReaderService : Service() {

    private var mMediaProjection: MediaProjection? = null
    private var mHandler: Handler? = null
    private var mHandlerThread: HandlerThread? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private val mDensity by lazy { resources.displayMetrics.densityDpi }

    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var configData: ByteBuffer? = null

    private val encodeBuilder by lazy { ScreenShareKit.encodeBuilder }
    private var isStop = false

    // RGBA相关
    private val lock = Object()
    private var isImageProcessing = false
    private val mQuit: AtomicBoolean = AtomicBoolean(false)
    private var mImgReader: ImageReader? = null
    private var mLastSendTSMs = 0L
    private var lastWidth = 0

    // audio
    private var audioCapture: AudioCapture? = null

    override fun onCreate() {
        super.onCreate()
        mHandlerThread = HandlerThread("ScreenReaderService-HandlerThread").apply { start() }
        mHandler = Handler(mHandlerThread!!.looper)
    }

    override fun onDestroy() {
        super.onDestroy()
        mHandlerThread?.quitSafely()
    }

    private fun initImageReader(width: Int, height: Int) {
        mImgReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        mImgReader?.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
        surface = mImgReader?.surface
    }

    private fun isRotationChange(): Boolean {
        return if (encodeBuilder.encodeConfig.width == lastWidth) {
            false
        } else {
            lastWidth = encodeBuilder.encodeConfig.width
            true
        }
    }

    private fun startCapture(width: Int, height: Int, frame: Int) {
        if (encodeBuilder.screenDataType == EncodeBuilder.SCREEN_DATA_TYPE.H264) {
            initMediaCodec(width, height, frame)
            createVirtualDisplay(width, height, surface)
        } else {
            Log.d("屏幕采集", "宽${width} 高${height}")
            initImageReader(width, height)
            createVirtualDisplay(width, height, surface)
        }
    }

    private fun startAudioCapture() {
        audioCapture?.startRecording()
    }

    private fun stopAudioCapture() {
        audioCapture?.stopRecording()
    }

    private fun stopCapture() {
        if (encodeBuilder.screenDataType == EncodeBuilder.SCREEN_DATA_TYPE.H264) {
            codec?.run {
                try {
                    stop()
                } catch (_: Exception) {}
                try {
                    release()
                } catch (_: Exception) {}
            }
            codec = null
            mVirtualDisplay?.release()
            mVirtualDisplay = null
        } else {
            mQuit.set(true)
            synchronized(lock) {
                while (isImageProcessing) {
                    try {
                        lock.wait()
                    } catch (_: InterruptedException) {
                    }
                }
                try {
                    mVirtualDisplay?.release()
                } catch (_: Exception) {}
                mVirtualDisplay = null
                try {
                    mImgReader?.close()
                } catch (_: Exception) {}
                mImgReader = null
            }
        }
    }

    private fun createVirtualDisplay(width: Int, height: Int, surface: Surface?) {
        mVirtualDisplay = mMediaProjection?.createVirtualDisplay(
            SCREENCAP_NAME,
            width, height,
            mDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            null
        )
    }

    private fun initMediaCodec(width: Int, height: Int, frame: Int) {
        var isCodecRunning = false
        val format = MediaFormat.createVideoFormat(MIME, width, height)
        format.apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            // 推荐码率算法：每像素比特率 0.15 bpp
            val bitrate = (width * height * frame * 0.15).toInt()
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frame)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1秒一个I帧
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            }
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        codec = MediaCodec.createEncoderByType(MIME)
        codec?.let { c ->
            c.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    synchronized(codec) {
                        isCodecRunning = true
                        val outputBuffer: ByteBuffer? = try {
                            codec.getOutputBuffer(index)
                        } catch (_: IllegalStateException) {
                            null
                        }
                        if (outputBuffer == null) return
                        val keyFrame = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (keyFrame) {
                            configData = ByteBuffer.allocate(info.size)
                            configData?.put(outputBuffer)
                        } else {
                            val data = createOutputBufferInfo(info, index, outputBuffer)
                            try {
                                encodeBuilder.h264CallBack?.onH264(
                                    data.buffer,
                                    data.isKeyFrame,
                                    encodeBuilder.encodeConfig.width,
                                    encodeBuilder.encodeConfig.height,
                                    data.presentationTimestampUs
                                )
                            } catch (e: Exception) {
                                Log.e("ScreenReaderService", "H264回调异常: "+e.message)
                            }
                        }
                        if (index >= 0 && (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 && isCodecRunning && !isStop) {
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    isCodecRunning = false
                    try {
                        encodeBuilder.errorCallBack?.onError(ErrorInfo(-2, "编码器错误"+e.message))
                    } catch (_: Exception) {}
                }
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
            })
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = c.createInputSurface()
            c.start()
        }
    }

    private fun createOutputBufferInfo(info: MediaCodec.BufferInfo, index: Int, outputBuffer: ByteBuffer): OutputBufferInfo {
        outputBuffer.position(info.offset)
        outputBuffer.limit(info.offset + info.size)
        val keyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        return if (keyFrame) {
            val buffer = ByteBuffer.allocateDirect((configData?.capacity() ?: 0) + info.size)
            configData?.rewind()
            configData?.let { buffer.put(it) }
            buffer.put(outputBuffer)
            buffer.position(0)
            OutputBufferInfo(index, buffer, keyFrame, info.presentationTimeUs, info.size + (configData?.capacity() ?: 0))
        } else {
            OutputBufferInfo(index, outputBuffer.slice(), keyFrame, info.presentationTimeUs, info.size)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        throw UnsupportedOperationException("unable to bind!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when {
                isStartCommand(it) -> {
                    isStop = false
                    val notification = NotificationUtils.getNotification(this)
                    startForeground(notification.first, notification.second)
                    startProjection(it.getIntExtra(RESULT_CODE, RESULT_CANCELED), it.getParcelableExtra(DATA)!!)
                }
                isStopCommand(it) -> {
                    isStop = true
                    stopProjection()
                    stopSelf()
                }
                isResetCommand(it) -> {
                    stopCapture()
                    startCapture(encodeBuilder.encodeConfig.width, encodeBuilder.encodeConfig.height, encodeBuilder.encodeConfig.frameRate)
                }
                isMuteCommand(it) -> {
                    val mute = it.getBooleanExtra(MUTE, false)
                    audioCapture?.setMicrophoneMute(mute)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun isStartCommand(intent: Intent): Boolean {
        return (intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                && intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), START))
    }

    private fun isStopCommand(intent: Intent): Boolean {
        return intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), STOP)
    }

    private fun isResetCommand(intent: Intent): Boolean {
        return intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), RESET)
    }
    private fun isMuteCommand(intent: Intent): Boolean {
        return intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), MUTE)
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data)
            mMediaProjection?.let { mp ->
                mp.registerCallback(MediaProjectionStopCallback(), mHandler)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && encodeBuilder.encodeConfig.audioCapture) {
                    audioCapture = AudioCapture(
                        encodeBuilder.encodeConfig.channels,
                        encodeBuilder.encodeConfig.sampleRate,
                        mp,
                        object : AudioRecordErrorCallback {
                            override fun onWebRtcAudioRecordInitError(var1: String?) {
                                try { encodeBuilder.errorCallBack?.onError(ErrorInfo(-5, var1.toString())) } catch (_: Exception) {}
                            }
                            override fun onWebRtcAudioRecordStartError(var1: AudioCapture.AudioRecordStartErrorCode?, var2: String?) {
                                try { encodeBuilder.errorCallBack?.onError(ErrorInfo(-4, var2.toString())) } catch (_: Exception) {}
                            }
                            override fun onWebRtcAudioRecordError(var1: String?) {
                                try { encodeBuilder.errorCallBack?.onError(ErrorInfo(-3, var1.toString())) } catch (_: Exception) {}
                            }
                        },
                        object : AudioFrameListener {
                            override fun onAudioData(var1: ByteArray) {
                                try { encodeBuilder.audioCallBack?.onAudio(var1, System.currentTimeMillis()) } catch (_: Exception) {}
                            }
                        }
                    )
                }
                startCapture(encodeBuilder.encodeConfig.width, encodeBuilder.encodeConfig.height, encodeBuilder.encodeConfig.frameRate)
                startAudioCapture()
            }
        }
    }

    private fun stopProjection() {
        mHandler?.post {
            try { mMediaProjection?.stop() } catch (_: Exception) {}
        }
        stopAudioCapture()
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            mHandler?.post {
                stopCapture()
                try { mMediaProjection?.unregisterCallback(this@MediaProjectionStopCallback) } catch (_: Exception) {}
            }
        }
    }

    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            reader?.let {
                synchronized(lock) {
                    val image = it.acquireLatestImage()
                    if (image != null) {
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val rowStride = plane.rowStride
                            val pixelStride = plane.pixelStride
                            val width = encodeBuilder.encodeConfig.width
                            val height = encodeBuilder.encodeConfig.height
                            isImageProcessing = true
                            // 兼容所有Android版本的RGBA拷贝
                            val rgba = ByteArray(width * height * 4)
                            val rowBuffer = ByteArray(width * 4)
                            for (row in 0 until height) {
                                buffer.position(row * rowStride)
                                buffer.get(rowBuffer, 0, width * 4)
                                System.arraycopy(rowBuffer, 0, rgba, row * width * 4, width * 4)
                            }
                            try {
                                encodeBuilder.rgbaCallback?.onRGBA(
                                    rgba,
                                    width,
                                    height,
                                    width, // stride
                                    encodeBuilder.device_rotation,
                                    isRotationChange()
                                )
                                mLastSendTSMs = System.currentTimeMillis()
                            } catch (e: Exception) {
                                Log.e("ScreenReaderService", "RGBA回调异常: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("ScreenReaderService", "ImageAvailable异常: ${e.message}")
                        } finally {
                            image.close()
                            isImageProcessing = false
                            lock.notifyAll()
                        }
                    }
                }
            }
        }
    }

    internal companion object GetIntent {
        private const val MIME = "Video/AVC"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val MUTE = "MUTE"
        private const val RESET = "RESET"
        private const val SCREENCAP_NAME = "screen_cap"

        fun getStartIntent(context: Context?, resultCode: Int, data: Intent): Intent {
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, START)
                putExtra(RESULT_CODE, resultCode)
                putExtra(DATA, data)
            }
        }
        fun getStopIntent(context: Context?): Intent {
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, STOP)
            }
        }
        fun getMuteMicIntent(context: Context?, mute: Boolean): Intent {
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, MUTE)
                putExtra(MUTE, mute)
            }
        }
        fun reset(context: Context?): Intent {
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, RESET)
            }
        }
    }
}