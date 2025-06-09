package org.loka.screensharekit

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.IInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class ServiceManager {
    private var getServiceMethod: Method? = null
    var windowManager: WindowManager? = null
        get() {
            if (field == null) {
                field = WindowManager(getService("window", "android.view.IWindowManager"))
            }
            return field
        }
        private set
    var displayManager: DisplayManager? = null
        get() {
            if (field == null) {
                field = DisplayManager(
                    getService(
                        "display",
                        "android.hardware.display.IDisplayManager"
                    )
                )
            }
            return field
        }
        private set

    init {
        getServiceMethod = try {
            Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    private fun getService(service: String, type: String): IInterface {
        return try {
            val binder = getServiceMethod!!.invoke(null, service) as IBinder
            val asInterfaceMethod =
                Class.forName("$type\$Stub").getMethod("asInterface", IBinder::class.java)
            asInterfaceMethod.invoke(null, binder) as IInterface
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
}