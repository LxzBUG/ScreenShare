package org.loka.screensharekit;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRotationWatcher;
import android.view.OrientationEventListener;

import java.lang.reflect.Method;

public class OrientationEventHandler extends OrientationEventListener {
    private static final String TAG = "watchRotation";
    private static final int ORIENTATION_HYSTERESIS = 5;
    private int mLastOrientation = 0;
    private Context mContext;

    public OrientationEventHandler(Context context) {
        super(context);
        this.mContext = context;

    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (orientation == ORIENTATION_UNKNOWN) return;
        orientation = roundOrientation(orientation, 0);
        if (orientation == mLastOrientation) {
            return;
        }
        mLastOrientation = orientation;
        //do sth

        Log.d("rrrrrrr1111",orientation+"===");
    }

    private static int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min(dist, 360 - dist);
            changeOrientation = (dist >= 45 + ORIENTATION_HYSTERESIS);
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    private IRotationWatcher iRotationWatcher = new IRotationWatcher.Stub() {

        @Override
        public void onRotationChanged(int rotation) throws RemoteException {
            //do sth

            Log.d("rrrrrrr",rotation+"===");
        }
    };

    public void watchRotationReflect() {
        try {
            Method getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", new Class[]{String.class});
            Object ServiceManager = getServiceMethod.invoke(null, new Object[]{"window"});
            Class<?> cStub = Class.forName("android.view.IWindowManager$Stub");
            Method asInterface = cStub.getMethod("asInterface", IBinder.class);
            Object iWindowManager = asInterface.invoke(null, ServiceManager);
            Method watchRotation = iWindowManager.getClass().getMethod("watchRotation", IRotationWatcher.class);
            watchRotation.invoke(iWindowManager, iRotationWatcher);
        } catch (Exception e) {
            Log.d(TAG, "watchRotationReflect " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void removeRotationWatcherReflect() {
        try {
            Method getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", new Class[]{String.class});
            Object ServiceManager = getServiceMethod.invoke(null, new Object[]{"window"});
            Class<?> cStub = Class.forName("android.view.IWindowManager$Stub");
            Method asInterface = cStub.getMethod("asInterface", IBinder.class);
            Object iWindowManager = asInterface.invoke(null, ServiceManager);
            Method removeRotationWatcher = iWindowManager.getClass().getMethod("removeRotationWatcher", IRotationWatcher.class);
            removeRotationWatcher.invoke(iWindowManager, iRotationWatcher);
        } catch (Exception e) {
            Log.d(TAG, "removeRotationWatcherReflect " + e.getMessage());
            e.printStackTrace();
        }
    }
}
