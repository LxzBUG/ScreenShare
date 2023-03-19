package org.loka.screensharekit

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.fragment.app.Fragment


class InvisibleFragment : Fragment(){

    private var encodeBuilder : EncodeBuilder? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    @TargetApi(Build.VERSION_CODES.M)
    fun requestMediaProjection(encodeBuilder: EncodeBuilder){
        this.encodeBuilder = encodeBuilder;
        mediaProjectionManager  = activity?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager?.createScreenCaptureIntent(), 5000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 5000){
            if (resultCode == Activity.RESULT_CANCELED){
                encodeBuilder?.errorCallBack?.onError(ErrorInfo(-1,"已取消"))
            }else{
                if (resultCode == Activity.RESULT_OK){
                    data?.let {
                        encodeBuilder?.startCallback?.onStart()
                        activity?.startService(ScreenReaderService.getStartIntent(context,resultCode,data))
                    }
                }
            }
        }
    }








}