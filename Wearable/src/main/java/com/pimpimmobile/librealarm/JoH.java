
package com.pimpimmobile.librealarm;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;


/*
 * Created by jamorham on 04/04/2017.
 */


public class JoH {


    private static final String TAG = "LibreAlarm-JoH";
    private static final Map<String, Long> rateLimits = new HashMap<String, Long>();

    public static long tsl() {
        return System.currentTimeMillis();
    }

    // return true if below rate limit
    public static synchronized boolean ratelimit(String name, int seconds) {
        // check if over limit
        if ((rateLimits.containsKey(name)) && (JoH.tsl() - rateLimits.get(name) < (seconds * 1000))) {
            Log.d(TAG, name + " rate limited: " + seconds + " seconds");
            return false;
        }
        // not over limit
        rateLimits.put(name, JoH.tsl());
        return true;
    }

    // return true if below rate limit
    public static synchronized boolean quietratelimit(String name, int seconds) {
        // check if over limit
        if ((rateLimits.containsKey(name)) && (JoH.tsl() - rateLimits.get(name) < (seconds * 1000))) {
            return false;
        }
        // not over limit
        rateLimits.put(name, JoH.tsl());
        return true;
    }

    public static PowerManager.WakeLock getWakeLock(final String name, int millis) {
        return getWakeLock(libreAlarm.getAppContext(), name, millis);
    }

    public static PowerManager.WakeLock getWakeLock(Context context, final String name, int millis) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
        wl.acquire(millis);
        return wl;
    }

    public static void releaseWakeLock(PowerManager.WakeLock wl) {
        if (wl.isHeld()) wl.release();
    }

    public static boolean runOnUiThread(Runnable theRunnable) {
        final Handler mainHandler = new Handler(libreAlarm.getAppContext().getMainLooper());
        return mainHandler.post(theRunnable);
    }

    public static boolean runOnUiThreadDelayed(Runnable theRunnable, long delay) {
        final Handler mainHandler = new Handler(libreAlarm.getAppContext().getMainLooper());
        return mainHandler.postDelayed(theRunnable, delay);
    }

    public static void removeUiThreadRunnable(Runnable theRunnable)
    {
        final Handler mainHandler = new Handler(libreAlarm.getAppContext().getMainLooper());
        mainHandler.removeCallbacks(theRunnable);
    }

}
