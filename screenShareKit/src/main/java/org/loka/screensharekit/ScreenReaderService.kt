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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import org.ar.rtc.IRtcEngineEventHandler
import org.ar.rtc.RtcEngine
import org.ar.rtc.video.ARVideoFrame
import java.nio.ByteBuffer
import java.util.*


class ScreenReaderService : Service() {


    private var mMediaProjection: MediaProjection? = null
    private var mHandler: Handler? = null
    private var mDisplay: Display? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mDensity = 0

    private lateinit var codec: MediaCodec
    private lateinit var surface: Surface
    private lateinit var configData: ByteBuffer

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
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        ) //颜色格式
        format.setInteger(MediaFormat.KEY_BIT_RATE, encodeBuilder.encodeConfig.bitrate) //码流
        format.setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        );
        format.setInteger(MediaFormat.KEY_FRAME_RATE, encodeBuilder.encodeConfig.frameRate) //帧数
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        codec = MediaCodec.createEncoderByType(MIME)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            }
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {

                val outputBuffer = codec.getOutputBuffer(index)
                val keyFrame = (info.flags and  MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (keyFrame){
                    configData = ByteBuffer.allocate(info.size)
                    configData.put(outputBuffer)
                }else{
                    val data = createOutputBufferInfo(info,index,outputBuffer!!)
                    encodeBuilder.h264CallBack?.onH264(data.buffer,data.isKeyFrame,data.presentationTimestampUs)
                }
                codec.releaseOutputBuffer(index, false)

            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                encodeBuilder.errorCallBack?.onError(ErrorInfo(-1,e.message.toString()))
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

        })
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
    }


    private fun createOutputBufferInfo(info:MediaCodec.BufferInfo,index:Int,outputBuffer:ByteBuffer):OutputBufferInfo{
        outputBuffer.position(info.offset)
        outputBuffer.limit(info.offset+info.size)
        val keyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        return if (keyFrame){
            val buffer = ByteBuffer.allocateDirect(configData.capacity()+info.size)
            configData.rewind()
            buffer.put(configData)
            buffer.put(outputBuffer)
            buffer.position(0)
            OutputBufferInfo(index,buffer,keyFrame,info.presentationTimeUs,info.size+configData.capacity())
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

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data)
            if (mMediaProjection != null) {
                mDensity = Resources.getSystem().displayMetrics.densityDpi
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = windowManager.defaultDisplay
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
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
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
                codec.run {
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

    }
}