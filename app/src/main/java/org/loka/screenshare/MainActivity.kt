package org.loka.screenshare

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import org.loka.screensharekit.ScreenShareKit

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


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ScreenShareKit.screenRotation(newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun requestCapture() {
        ScreenShareKit.init(this).onH264({ buffer, isKeyFrame, w, h, ts ->
        }).start()
    }
}