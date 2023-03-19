package org.loka.screensharekit

import android.os.Build
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import org.loka.screensharekit.callback.ErrorCallBack
import org.loka.screensharekit.callback.H264CallBack
import org.loka.screensharekit.callback.RGBACallBack
import org.loka.screensharekit.callback.StartCaptureCallback

class EncodeBuilder(fragment: Fragment?,fragmentActivity: FragmentActivity?):Device.RotationListener{

    private lateinit var activity: FragmentActivity
    private var fragment: Fragment? = null

    @JvmField
    var h264CallBack : H264CallBack? = null
    var errorCallBack : ErrorCallBack? = null
    var rgbaCallback:RGBACallBack?=null
    var startCallback:StartCaptureCallback?=null
    internal val encodeConfig = EncodeConfig()
    private val device by lazy { Device() }
    var device_rotation = 0
    var screenDataType = SCREEN_DATA_TYPE.H264

    public enum class SCREEN_DATA_TYPE{
        H264,RGBA
    }

    init {
        fragmentActivity?.let {
            activity = it
        }?: run {
            fragment?.let {
                activity = it.requireActivity()
            }
        }
        this.fragment = fragment
    }


    private val fragmentManager : FragmentManager
        get() {
            return fragment?.childFragmentManager ?: activity.supportFragmentManager
        }


    private val invisibleFragment : InvisibleFragment
        get() {
            val existedFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG)
            return if (existedFragment != null) {
                existedFragment as InvisibleFragment
            } else {
                val invisibleFragment = InvisibleFragment()
                fragmentManager.beginTransaction()
                    .add(invisibleFragment, FRAGMENT_TAG)
                    .commitNowAllowingStateLoss()
                invisibleFragment
            }
        }

    companion object {
        private const val FRAGMENT_TAG = "InvisibleFragment"
    }




    fun onH264(callBack: H264CallBack?):EncodeBuilder{
        return apply {
            h264CallBack = callBack
        }
    }

    fun onStart(callBack:StartCaptureCallback?):EncodeBuilder{
        return apply {
            startCallback = callBack
        }
    }

    fun onRGBA(callBack:RGBACallBack):EncodeBuilder{
        return apply {
            rgbaCallback = callBack
        }
    }

    fun onError(callBack: ErrorCallBack?):EncodeBuilder{
        return apply {
            errorCallBack = callBack
        }
    }


    fun start(){
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
            invisibleFragment.requestMediaProjection(this)
            device.setRotationListener(this)
        }else{
            errorCallBack?.onError(ErrorInfo(-3,"当前系统版本不支持"))
        }

    }

    fun stop(){
        activity.startService(ScreenReaderService.getStopIntent(activity))
        device.setRotationListener(null)
    }



    private fun screenRotation(isLandscape: Boolean){
        if (isLandscape){
            if (encodeConfig.width<encodeConfig.height) {
                val w = encodeConfig.height
                val h = encodeConfig.width
                encodeConfig.width = w
                encodeConfig.height = h
            }
        }else{
            if (encodeConfig.height<encodeConfig.width) {
                val w = encodeConfig.height
                val h = encodeConfig.width
                encodeConfig.width = w
                encodeConfig.height = h
            }
        }
        activity.startService(ScreenReaderService.reset(activity))

    }

    fun config(width:Int = 0,height:Int = 0,frameRate:Int = 0,bitrate:Int = 0,screenDataType: SCREEN_DATA_TYPE=SCREEN_DATA_TYPE.H264):EncodeBuilder{
        if (width>0){
            encodeConfig.width = width
        }
        if (height>0){
            encodeConfig.height = height
        }
        if (frameRate>0){
            encodeConfig.frameRate = frameRate
        }

        if (bitrate>0){
            encodeConfig.bitrate = bitrate
        }

        this.screenDataType = screenDataType
        return this
    }

    override fun onRotationChanged(rotation: Int) {
        when(rotation){
            0->{
                device_rotation = 0
            }
            1->{
                device_rotation = 90
            }
            2->{
                device_rotation = 180
            }
            3->{
                device_rotation = 270
            }
        }
        if (rotation==3||rotation==1){
            screenRotation(true)
        }else{
            screenRotation(false)
        }
    }


}