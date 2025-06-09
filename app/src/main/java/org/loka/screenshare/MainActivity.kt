package org.loka.screenshare

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import org.loka.screensharekit.EncodeBuilder
import org.loka.screensharekit.ScreenShareKit
import org.loka.screensharekit.callback.AudioCallBack
import org.loka.screensharekit.callback.H264CallBack
import org.loka.screensharekit.callback.RGBACallBack
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.start).setOnClickListener {
            requestCapture()
        }

        findViewById<Button>(R.id.stop).setOnClickListener {
            ScreenShareKit.stop()
        }
    }


    private fun requestCapture() {
        ScreenShareKit.setMicrophoneMute(true)
        ScreenShareKit.init(this).config(screenDataType = EncodeBuilder.SCREEN_DATA_TYPE.RGBA).onRGBA(object :RGBACallBack{
            override fun onRGBA(
                rgba: ByteArray,
                width: Int,
                height: Int,
                stride: Int,
                rotation: Int,
                rotationChanged: Boolean
            ) {
                //屏幕截图数据
            }

        }).onAudio(object :AudioCallBack{
            override fun onAudio(buffer: ByteArray?, ts: Long) {
                //音频数据
            }

        }).onStart({
            //用户同意采集，开始采集数据
        }).start()

    }
}