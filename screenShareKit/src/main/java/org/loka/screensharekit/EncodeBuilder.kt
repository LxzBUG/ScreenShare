package org.loka.screensharekit

import android.content.res.Resources
import android.media.projection.MediaProjection
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import org.loka.screensharekit.callback.ErrorCallBack
import org.loka.screensharekit.callback.H264CallBack

class EncodeBuilder(fragment: Fragment?,fragmentActivity: FragmentActivity?) {

    private lateinit var activity: FragmentActivity
    private var fragment: Fragment? = null

    @JvmField
    var h264CallBack : H264CallBack? = null
    var errorCallBack : ErrorCallBack? = null
    internal val encodeConfig = EncodeConfig()

    init {
        fragmentActivity?.let {
            activity = it
        }?: run {
            fragment?.let {
                activity = it.requireActivity()
            }
        }
        this.fragment = fragment
        encodeConfig.width = Resources.getSystem().displayMetrics.widthPixels
        encodeConfig.height = Resources.getSystem().displayMetrics.heightPixels
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

    fun onError(callBack: ErrorCallBack?):EncodeBuilder{
        return apply {
            errorCallBack = callBack
        }
    }

    fun start(){
        invisibleFragment.requestMediaProjection(this)
    }

    fun stop(){
        activity.startService(ScreenReaderService.getStopIntent(activity))
    }

    fun config(width:Int = 0,height:Int = 0,frameRate:Int = 0,bitrate:Int = 0):EncodeBuilder{
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
        return this
    }

}