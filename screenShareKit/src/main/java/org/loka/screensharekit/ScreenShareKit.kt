package org.loka.screensharekit

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

object ScreenShareKit{

    private var activity: FragmentActivity? = null
    private var fragment: Fragment? = null
    var packageUid: Int = -1
    internal lateinit var encodeBuilder:EncodeBuilder

    fun init(activity: FragmentActivity) = EncodeBuilder(fragment, activity).also {
        packageUid = getUid(activity,activity.packageName)
        encodeBuilder = it
    }
    private fun getUid(activity:FragmentActivity,packageName: String): Int {
        try {
            val pm: PackageManager = activity.packageManager
            val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
            return appInfo.uid
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return -1
        }
    }

    fun init(fragment: Fragment) = EncodeBuilder(fragment,activity).also { encodeBuilder =it }

    fun setMicrophoneMute(mute:Boolean){
        encodeBuilder.setMicrophoneMute(mute)
    }

    fun stop(){
        encodeBuilder.stop()
    }







}