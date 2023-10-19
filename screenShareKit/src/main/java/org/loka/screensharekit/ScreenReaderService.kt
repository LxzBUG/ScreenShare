package org.loka.screensharekit

import android.app.Activity.RESULT_CANCELED
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


class ScreenReaderService : Service() {


    private var mMediaProjection: MediaProjection? = null
    private var mHandler: Handler? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private val mDensity by lazy { resources.displayMetrics.densityDpi }

    private var codec: MediaCodec?=null
    private var surface: Surface?=null
    private var configData: ByteBuffer?=null

    private val encodeBuilder by lazy { ScreenShareKit.encodeBuilder }
    private var isStop = false

    val stateLock = java.lang.Object()
    val mMutex = java.lang.Object()
    private var rotation = 0

    ////rbga

    private var rgbaData:ByteArray? = null
    private var rowStride = 0
    private val mQuit: AtomicBoolean = AtomicBoolean(false)
    private var mImgReader: ImageReader? = null
    private var mLastSendTSMs = 0L

    //audio
    private var  audioCapture :AudioCapture?=null

    override fun onCreate() {
        super.onCreate()
        Thread{
            Looper.prepare()
            mHandler = Handler()
            Looper.loop()

        }.start()
    }

    private fun initImageReader(width:Int,height: Int){
        mImgReader = ImageReader.newInstance(width,height, PixelFormat.RGBA_8888,2)
        mImgReader?.setOnImageAvailableListener(ImageAvailableListener(),mHandler)
        surface = mImgReader?.surface
    }

    private fun isRotationChange():Boolean{
        if (encodeBuilder.device_rotation==rotation){
            return false
        }else{
            rotation = encodeBuilder.device_rotation
            return true
        }
    }


    private fun startCapture(width:Int,height:Int,frame:Int){
        if (encodeBuilder.screenDataType==EncodeBuilder.SCREEN_DATA_TYPE.H264){
            initMediaCodec(width, height,frame)
            createVirtualDisplay(width,height,surface)
        }else{
            synchronized(stateLock){
                initImageReader(width, height)
                createVirtualDisplay(width, height,surface)
            }
            mQuit.set(false)
            thread(true){
                var preTS = 0L
                var intervalTS = 1000 / frame //计算每帧需要等待的时间间隔
                while (!mQuit.get()){
                    val startTs = System.currentTimeMillis()
                    if (preTS==0L){
                        preTS = startTs
                    }
                    if (startTs-mLastSendTSMs>intervalTS){
                        rgbaData?.let {
                            encodeBuilder.rgbaCallback?.onRGBA(it,width,height,rowStride/4,encodeBuilder.device_rotation,isRotationChange())
                            mLastSendTSMs = System.currentTimeMillis()
                        }
                    }
                    val diffTS = startTs - preTS
                    var waitTime = Math.max(intervalTS+intervalTS-diffTS,0)
                    synchronized(mMutex){
                        try {
                            waitTime = Math.max(waitTime,50)
                            mMutex.wait(waitTime)
                        }catch (e:InterruptedException){
                            e.printStackTrace()
                        }
                    }
                    preTS = startTs
                }
            }
        }
        audioCapture?.startRecording()

    }

    private fun stopCapture(){
        if (encodeBuilder.screenDataType==EncodeBuilder.SCREEN_DATA_TYPE.H264){
            codec?.run {
                stop()
                release()
            }
            mVirtualDisplay?.release()
        }else{
            synchronized(stateLock){
                mQuit.set(true)
                synchronized(mMutex){
                    mMutex.notify()
                }
            }
            mVirtualDisplay?.release()
            mImgReader?.close()
        }
        audioCapture?.stopRecording()

    }


    private fun createVirtualDisplay(width: Int,height: Int,surface: Surface?){
        mVirtualDisplay = mMediaProjection?.createVirtualDisplay(
            SCREENCAP_NAME,
            width,height,
            mDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            null
        )
    }

    private fun initMediaCodec(width: Int,height: Int,frame: Int) {
        var isCodecRunning = false
        val format = MediaFormat.createVideoFormat(MIME, width, height)
        format.apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) //颜色格式
            setInteger(MediaFormat.KEY_BIT_RATE, encodeBuilder.encodeConfig.bitrate) //码流
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_FRAME_RATE, frame) //帧数
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,100_000L)
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
                    synchronized(codec) {
                        isCodecRunning = true
                        val outputBuffer: ByteBuffer?
                        try {
                            outputBuffer = codec.getOutputBuffer(index)
                            if (outputBuffer == null) {
                                return
                            }
                        } catch (e: IllegalStateException) {
                            return
                        }
                        val keyFrame = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (keyFrame) {
                            configData = ByteBuffer.allocate(info.size)
                            configData?.put(outputBuffer)
                        } else {
                            val data = createOutputBufferInfo(info, index, outputBuffer!!)
                            encodeBuilder.h264CallBack?.onH264(
                                data.buffer,
                                data.isKeyFrame,
                                encodeBuilder.encodeConfig.width,
                                encodeBuilder.encodeConfig.height,
                                data.presentationTimestampUs
                            )
                        }
                        if (index >= 0) {
                            // 判断缓冲区是否已经被释放
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                                // 判断编码器是否处于运行状态
                                if (isCodecRunning) {
                                    if (!isStop) {
                                        codec.releaseOutputBuffer(index, false)
                                    }
                                }
                            }
                        }
                    }

                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    isCodecRunning = false
                    encodeBuilder.errorCallBack?.onError(ErrorInfo(-2,"编码器错误${e.message.toString()}"))
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {

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
                isStop = false
                val notification = NotificationUtils.getNotification(this)
                startForeground(notification.first, notification.second)
                startProjection(it.getIntExtra(RESULT_CODE, RESULT_CANCELED), it.getParcelableExtra(DATA)!!)
            }else if (isStopCommand(it)){
                isStop = true
                stopProjection()
                stopSelf()
            }else if (isResetCommand(it)){
                    stopCapture()
                    startCapture(encodeBuilder.encodeConfig.width,encodeBuilder.encodeConfig.height,encodeBuilder.encodeConfig.frameRate)
            }else if (isMuteCommand(it)){
                val mute = it.getBooleanExtra(MUTE,false)
                audioCapture?.setMicrophoneMute(mute)
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
            if (mMediaProjection != null) {
                mMediaProjection?.registerCallback(MediaProjectionStopCallback(), mHandler)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (encodeBuilder.encodeConfig.audioCapture){
                        audioCapture = AudioCapture(encodeBuilder.encodeConfig.channels,encodeBuilder.encodeConfig.sampleRate,mMediaProjection!!,object :AudioRecordErrorCallback{
                            override fun onWebRtcAudioRecordInitError(var1: String?) {
                                encodeBuilder.errorCallBack?.onError(ErrorInfo(-5,var1.toString()))
                            }

                            override fun onWebRtcAudioRecordStartError(
                                var1: AudioCapture.AudioRecordStartErrorCode?,
                                var2: String?
                            ) {
                                encodeBuilder.errorCallBack?.onError(ErrorInfo(-4,var2.toString()))
                            }

                            override fun onWebRtcAudioRecordError(var1: String?) {
                                encodeBuilder.errorCallBack?.onError(ErrorInfo(-3,var1.toString()))
                            }
                        },object :AudioFrameListener{
                            override fun onAudioData(var1: ByteArray) {
                                encodeBuilder.audioCallBack?.onAudio(var1,System.currentTimeMillis())
                            }

                        })
                    }
                }
                startCapture(encodeBuilder.encodeConfig.width,encodeBuilder.encodeConfig.height,encodeBuilder.encodeConfig.frameRate)
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


    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            mHandler?.post(Runnable {
                stopCapture()
                mMediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
            })
        }
    }


    private inner class ImageAvailableListener:ImageReader.OnImageAvailableListener{
        override fun onImageAvailable(reader: ImageReader?) {
            reader?.let {
                val image = it.acquireLatestImage()
                if (image!=null){
                    try {
                        val planes = image.planes
                        val buffer = planes.get(0).getBuffer()
                        if (buffer==null){
                            return
                        }
                        rowStride = planes[0].rowStride
                        val remaining = buffer.remaining()
                        if (remaining<=0){
                            return
                        }
                        val data = ByteArray(remaining)
                        if (data.size==remaining){
                            buffer.get(data,0,remaining)
                            rgbaData=data
                        }else{
                        }
                    }catch (e:Exception){
                    }finally {
                        image.close() // 及时关闭Image
                    }

                }
            }
        }

    }
    internal companion object GetIntent{
        private const val MIME = "Video/AVC"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val MUTE = "MUTE"
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

        fun getMuteMicIntent(context: Context?,mute:Boolean):Intent{
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, MUTE)
                putExtra(MUTE,mute)
            }
        }

        fun reset(context: Context?):Intent{
            return Intent(context, ScreenReaderService::class.java).apply {
                putExtra(ACTION, RESET)
            }
        }
    }

}