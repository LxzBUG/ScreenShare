package org.loka.screenshare

import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.ar.rtc.Constants
import org.ar.rtc.IRtcEngineEventHandler
import org.ar.rtc.RtcEngine
import org.ar.rtc.video.ARVideoFrame
import org.loka.screensharekit.ScreenShareKit

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.start).setOnClickListener {
            ScreenShareKit.init(this)
                .onH264 { buffer, isKeyFrame,w,h, ts ->
                }.start()
        }

        findViewById<Button>(R.id.stop).setOnClickListener {
            ScreenShareKit.stop()
        }
    }
}