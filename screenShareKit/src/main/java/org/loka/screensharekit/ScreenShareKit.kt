package org.loka.screensharekit

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

object ScreenShareKit{

    private var activity: FragmentActivity? = null
    private var fragment: Fragment? = null

    internal lateinit var encodeBuilder:EncodeBuilder

    fun init(activity: FragmentActivity) = EncodeBuilder(fragment, activity).also { encodeBuilder = it }


    fun init(fragment: Fragment) = EncodeBuilder(fragment,activity).also { encodeBuilder =it }

    fun stop(){
        encodeBuilder.stop()
    }





}