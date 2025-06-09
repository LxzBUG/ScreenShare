package org.loka.screensharekit

import android.graphics.Point
import android.os.Build
import android.os.RemoteException
import org.loka.screensharekit.ServiceManager
import org.loka.screensharekit.ScreenInfo
import android.view.IRotationWatcher
import org.loka.screensharekit.DisplayInfo
import java.lang.AssertionError

class Device {
    private val serviceManager = ServiceManager()

    @get:Synchronized
    var screenInfo: ScreenInfo
    private var rotationListener: RotationListener? = null

    init {
        screenInfo = computeScreenInfo(1920)
        registerRotationWatcher(object : IRotationWatcher.Stub() {
            @Throws(RemoteException::class)
            override fun onRotationChanged(rotation: Int) {
                synchronized(this@Device) {
                    screenInfo = screenInfo.withRotation(rotation)
                    // notify
                    if (rotationListener != null) {
                        rotationListener!!.onRotationChanged(rotation)
                    }
                }
            }
        })
    }

    private fun computeScreenInfo(maxSize: Int): ScreenInfo {
        // Compute the video size and the padding of the content inside this video.
        // Principle:
        // - scale down the great side of the screen to maxSize (if necessary);
        // - scale down the other side so that the aspect ratio is preserved;
        // - round this value to the nearest multiple of 8 (H.264 only accepts multiples of 8)
        val displayInfo = serviceManager.displayManager!!.displayInfo
        val rotated = displayInfo.rotation and 1 != 0
        val deviceSize = displayInfo.size
        var w = deviceSize.width and 7.inv() // in case it's not a multiple of 8
        var h = deviceSize.height and 7.inv()
        if (maxSize > 0) {
            if (BuildConfig.DEBUG && maxSize % 8 != 0) {
                throw AssertionError("Max size must be a multiple of 8")
            }
            val portrait = h > w
            var major = if (portrait) h else w
            var minor = if (portrait) w else h
            if (major > maxSize) {
                val minorExact = minor * maxSize / major
                // +4 to round the value to the nearest multiple of 8
                minor = minorExact + 4 and 7.inv()
                major = maxSize
            }
            w = if (portrait) minor else major
            h = if (portrait) major else minor
        }
        val videoSize = Size(w, h)
        return ScreenInfo(deviceSize, videoSize, rotated)
    }

    fun getPhysicalPoint(position: Position): Point? {
        val screenInfo// it hides the field on purpose, to read it with a lock
                = screenInfo // read with synchronization
        val videoSize = screenInfo.videoSize
        val clientVideoSize = position.screenSize
        if (!videoSize.equals(clientVideoSize)) {
            // The client sends a click relative to a video with wrong dimensions,
            // the device may have been rotated since the event was generated, so ignore the event
            return null
        }
        val deviceSize = screenInfo.deviceSize
        val point = position.point
        val scaledX = point.x * deviceSize.width / videoSize.width
        val scaledY = point.y * deviceSize.height / videoSize.height
        return Point(scaledX, scaledY)
    }

    fun registerRotationWatcher(rotationWatcher: IRotationWatcher?) {
        serviceManager.windowManager!!.registerRotationWatcher(rotationWatcher)
    }

    @Synchronized
    fun setRotationListener(rotationListener: RotationListener?) {
        this.rotationListener = rotationListener
    }

    fun NewgetPhysicalPoint(point: Point): Point {
        val screenInfo// it hides the field on purpose, to read it with a lock
                = screenInfo // read with synchronization
        val videoSize = screenInfo.videoSize
        //        Size clientVideoSize = position.getScreenSize();
        val deviceSize = screenInfo.deviceSize
        //        Point point = position.getPoint();
        val scaledX = point.x * deviceSize.width / videoSize.width
        val scaledY = point.y * deviceSize.height / videoSize.height
        return Point(scaledX, scaledY)
    }

    interface RotationListener {
        fun onRotationChanged(rotation: Int)
    }

    companion object {
        val deviceName: String
            get() = Build.MODEL
    }
}