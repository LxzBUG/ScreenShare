package org.loka.screenshare

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.loka.screensharekit.ScreenShareKit
import org.loka.screensharekit.callback.ErrorCallBack
import org.loka.screensharekit.callback.H264CallBack

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)




        ScreenShareKit.init(this)
            .onH264 {buffer, isKeyFrame, ts ->

            }.start()





    }
}