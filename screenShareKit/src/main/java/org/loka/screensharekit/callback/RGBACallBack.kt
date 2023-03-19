package org.loka.screensharekit.callback

fun interface RGBACallBack{
    fun onRGBA(rgba: ByteArray,width:Int,height:Int,rotation:Int,rotationChanged:Boolean)
}