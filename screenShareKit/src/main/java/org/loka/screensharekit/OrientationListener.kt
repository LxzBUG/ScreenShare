package org.loka.screensharekit

import android.content.Context
import android.content.res.Configuration
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import java.util.concurrent.locks.ReentrantLock


abstract class OrientationListener : OrientationEventListener {
    @Volatile
    private var defaultScreenOrientation = CONFIGURATION_ORIENTATION_UNDEFINED
    var prevOrientation = ORIENTATION_UNKNOWN
    private var ctx: Context
    private val lock: ReentrantLock = ReentrantLock(true)

    constructor(context: Context) : super(context) {
        ctx = context
    }

    constructor(context: Context, rate: Int) : super(context, rate) {
        ctx = context
    }

    override fun onOrientationChanged(orientation: Int) {
        var currentOrientation = ORIENTATION_UNKNOWN
        if (orientation >= 330 || orientation < 30) {
            currentOrientation = Surface.ROTATION_0
        } else if (orientation >= 60 && orientation < 120) {
            currentOrientation = Surface.ROTATION_90
        } else if (orientation >= 150 && orientation < 210) {
            currentOrientation = Surface.ROTATION_180
        } else if (orientation >= 240 && orientation < 300) {
            currentOrientation = Surface.ROTATION_270
        }
        if (prevOrientation != currentOrientation && orientation != ORIENTATION_UNKNOWN) {
            prevOrientation = currentOrientation
            if (currentOrientation != ORIENTATION_UNKNOWN) reportOrientationChanged(
                currentOrientation
            )
        }
    }

    private fun reportOrientationChanged(currentOrientation: Int) {
        val defaultOrientation = deviceDefaultOrientation
        val orthogonalOrientation: Int =
            if (defaultOrientation == Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        val toReportOrientation: Int
        toReportOrientation =
            if (currentOrientation == Surface.ROTATION_0 || currentOrientation == Surface.ROTATION_180) defaultOrientation else orthogonalOrientation
        onSimpleOrientationChanged(toReportOrientation)
    }

    /**
     * Must determine what is default device orientation (some tablets can have default landscape). Must be initialized when device orientation is defined.
     *
     * @return value of [Configuration.ORIENTATION_LANDSCAPE] or [Configuration.ORIENTATION_PORTRAIT]
     */
    private val deviceDefaultOrientation: Int
        private get() {
            if (defaultScreenOrientation == CONFIGURATION_ORIENTATION_UNDEFINED) {
                lock.lock()
                defaultScreenOrientation = initDeviceDefaultOrientation(ctx)
                lock.unlock()
            }
            return defaultScreenOrientation
        }

    /**
     * Provides device default orientation
     *
     * @return value of [Configuration.ORIENTATION_LANDSCAPE] or [Configuration.ORIENTATION_PORTRAIT]
     */
    private fun initDeviceDefaultOrientation(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val config: Configuration = context.getResources().getConfiguration()
        val rotation = windowManager.defaultDisplay.rotation
        val isLand = config.orientation === Configuration.ORIENTATION_LANDSCAPE
        val isDefaultAxis = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
        var result = CONFIGURATION_ORIENTATION_UNDEFINED
        result = if (isDefaultAxis && isLand || !isDefaultAxis && !isLand) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        return result
    }

    /**
     * Fires when orientation changes from landscape to portrait and vice versa.
     *
     * @param orientation value of [Configuration.ORIENTATION_LANDSCAPE] or [Configuration.ORIENTATION_PORTRAIT]
     */
    abstract fun onSimpleOrientationChanged(orientation: Int)

    companion object {
        val CONFIGURATION_ORIENTATION_UNDEFINED: Int = Configuration.ORIENTATION_UNDEFINED
    }
}