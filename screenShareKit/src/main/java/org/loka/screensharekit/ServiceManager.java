package org.loka.screensharekit;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public final class ServiceManager {
    private final Method getServiceMethod;

    private WindowManager windowManager;
    private DisplayManager displayManager;

    public ServiceManager() {
        try {
            getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private IInterface getService(String service, String type) {
        try {
            IBinder binder = (IBinder) getServiceMethod.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public WindowManager getWindowManager() {
        if (windowManager == null) {
            windowManager = new WindowManager(getService("window", "android.view.IWindowManager"));
        }
        return windowManager;
    }

    public DisplayManager getDisplayManager() {
        if (displayManager == null) {
            displayManager = new DisplayManager(getService("display", "android.hardware.display.IDisplayManager"));
        }
        return displayManager;
    }

}