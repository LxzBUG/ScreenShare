// IRotationWatcher.aidl
package android.view;

// Declare any non-default types here with import statements

interface IRotationWatcher {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
   oneway void onRotationChanged(int rotation);
}