package org.loka.screensharekit

import android.app.Activity.RESULT_CANCELED
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.*


class ScreenReaderService : Service() {


    private var mMediaProjection: MediaProjection? = null
    private var mHandler: Handler? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private val mDensity by lazy { resources.displayMetrics.densityDpi }

    private var codec: MediaCodec?=null
    private var surface: Surface?=null
    private var configData: ByteBuffer?=null

    private val encodeBuilder by lazy { ScreenShareKit.encodeBuilder }

    override fun onCreate() {
        super.onCreate()
        Thread{
            Looper.prepare()
            mHandler = Handler()
            Looper.loop()

        }.start()
        initMediaCodec()
    }





    private fun initMediaCodec() {
        val format = MediaFormat.createVideoFormat(MIME, encodeBuilder.encodeConfig.width, encodeBuilder.encodeConfig.height)
        format.apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) //颜色格式
            setInteger(MediaFormat.KEY_BIT_RATE, encodeBuilder.encodeConfig.bitrate) //码流
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_FRAME_RATE, encodeBuilder.encodeConfig.frameRate) //帧数
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,1000000/45)
            if (Build.MANUFACTURER.contentEquals("XIAOMI")) {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
            } else {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
            setInteger(MediaFormat.KEY_COMPLEXITY, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }
        codec = MediaCodec.createEncoderByType(MIME)
        codec?.let {
            it.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                }
                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    val outputBuffer:ByteBuffer?
                    try {
                        outputBuffer = codec.getOutputBuffer(index)
                        if (outputBuffer == null){
                            return
                        }
                    }catch (e:IllegalStateException){
                        return
                    }
                    val keyFrame = (info.flags and  MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (keyFrame){
                        configData = ByteBuffer.allocate(info.size)
                        configData?.put(outputBuffer)
                    }else{
                        val data = createOutputBufferInfo(info,index,outputBuffer!!)
                        encodeBuilder.h264CallBack?.onH264(data.buffer,data.isKeyFrame,encodeBuilder.encodeConfig.width,encodeBuilder.encodeConfig.height,data.presentationTimestampUs)
                    }
                    codec.releaseOutputBuffer(index, false)

                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    encodeBuilder.errorCallBack?.onError(ErrorInfo(-1,e.message.toString()))
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    val width = format.getInteger(MediaFormat.KEY_WIDTH)
                    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    Log.d("Screenll","${width}++++${height}")

                }

            })
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = it.createInputSurface()
            it.start()
        }
    }


    private fun createOutputBufferInfo(info:MediaCodec.BufferInfo,index:Int,outputBuffer:ByteBuffer):OutputBufferInfo{
        outputBuffer.position(info.offset)
        outputBuffer.limit(info.offset+info.size)
        val keyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        return if (keyFrame){
            val buffer = ByteBuffer.allocateDirect(configData!!.capacity()+info.size)
            configData?.rewind()
            buffer.put(configData)
            buffer.put(outputBuffer)
            buffer.position(0)
            OutputBufferInfo(index,buffer,keyFrame,info.presentationTimeUs,info.size+configData!!.capacity())
        }else{
            OutputBufferInfo(index,outputBuffer.slice(),keyFrame,info.presentationTimeUs,info.size)
        }


    }


    override fun onBind(intent: Intent?): IBinder? {
        throw Exception("unable to bind!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if(isStartCommand(it)){
                val notification = NotificationUtils.getNotification(this)
                startForeground(notification.first, notification.second)
                startProjection(
                    it.getIntExtra(RESULT_CODE, RESULT_CANCELED), it.getParcelableExtra(
                        DATA
                    )!!
                )
            }else if (isStopCommand(it)){
                stopProjection()
                stopSelf()
            }else if (isResetCommand(it)){
                screenRotation()
            }
        }


        return super.onStartCommand(intent, flags, startId)
    }

    private fun screenRotation(){
        codec?.stop()
        codec?.release()
        val format = MediaFormat.createVideoFormat(MIME, encodeBuilder.encodeConfig.width, encodeBuilder.encodeConfig.height)
        format.apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) //颜色格式
            setInteger(MediaFormat.KEY_BIT_RATE, encodeBuilder.encodeConfig.bitrate) //码流
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_FRAME_RATE, encodeBuilder.encodeConfig.frameRate) //帧数
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,1000000/45)
            if (Build.MANUFACTURER.contentEquals("XIAOMI")) {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
            } else {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
            setInteger(MediaFormat.KEY_COMPLEXITY, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }
        codec = MediaCodec.createEncoderByType(MIME)
        codec?.let {
            it.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                }
                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    val outputBuffer:ByteBuffer?
                    try {
                        outputBuffer = codec.getOutputBuffer(index)
                        if (outputBuffer == null){
                            return
                        }
                    }catch (e:IllegalStateException){
                        return
                    }
                    val keyFrame = (info.flags and  MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (keyFrame){
                        configData = ByteBuffer.allocate(info.size)
                        configData?.put(outputBuffer)
                    }else{
                        val data = createOutputBufferInfo(info,index,outputBuffer!!)
                        encodeBuilder.h264CallBack?.onH264(data.buffer,data.isKeyFrame,encodeBuilder.encodeConfig.width,encodeBuilder.encodeConfig.height,data.presentationTimestampUs)
                    }
                    codec.releaseOutputBuffer(index, false)

                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    encodeBuilder.errorCallBack?.onError(ErrorInfo(-1,e.message.toString()))
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                }

            })
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = it.createInputSurface()
            it.start()
            createVirtualDisplay()
        }
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

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data)
            if (mMediaProjection != null) {
                createVirtualDisplay()
                mMediaProjection?.registerCallback(MediaProjectionStopCallback(), mHandler)
            }
        }
    }

    private fun stopProjection(){
        mHandler?.let {
            it.post {
                mMediaProjection?.stop()
            }
        }
    }

    private fun createVirtualDisplay() {
        mVirtualDisplay = mMediaProjection?.createVirtualDisplay(
            SCREENCAP_NAME,
            encodeBuilder.encodeConfig.width,
            encodeBuilder.encodeConfig.height,
            mDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            mHandler
        )
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            mHandler?.post(Runnable {
                codec?.run {
                    stop()
                    release()
                }
                mVirtualDisplay?.release()
                mMediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
            })
        }
    }


    internal companion object GetIntent{
        private const val MIME = "Video/AVC"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val RESET = "RESET"
        private const val SCREENCAP_NAME = "screen_cap"

        fun getStartIntent(context: Context?, resultCode: Int, data: Intent):Intent{
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, START)
                putExtra(RESULT_CODE, resultCode)
                putExtra(DATA, data)
            }
        }
        fun getStopIntent(context: Context?):Intent{
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, STOP)
            }
        }

        fun reset(context: Context?):Intent{
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, RESET)
            }
        }

    }
}