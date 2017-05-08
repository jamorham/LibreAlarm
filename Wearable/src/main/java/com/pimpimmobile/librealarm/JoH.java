
package com.pimpimmobile.librealarm;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
    public static String hourMinuteString() {
        // Date date = new Date();
        // SimpleDateFormat sd = new SimpleDateFormat("HH:mm");
        //  return sd.format(date);
        return hourMinuteString(JoH.tsl());
    }

    public static String hourMinuteString(long timestamp) {
        return android.text.format.DateFormat.format("kk:mm", timestamp).toString();
    }

    public static String dateTimeText(long timestamp) {
        return android.text.format.DateFormat.format("yyyy-MM-dd kk:mm:ss", timestamp).toString();
    }

    public static String dateText(long timestamp) {
        return android.text.format.DateFormat.format("yyyy-MM-dd", timestamp).toString();
    }

    // qs = quick string conversion of double for printing
    public static String qs(double x) {
        return qs(x, 2);
    }

    public static String qs(double x, int digits) {

        if (digits == -1) {
            digits = 0;
            if (((int) x != x)) {
                digits++;
                if ((((int) x * 10) / 10 != x)) {
                    digits++;
                    if ((((int) x * 100) / 100 != x)) digits++;
                }
            }
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("#", symbols);
        df.setMaximumFractionDigits(digits);
        df.setMinimumIntegerDigits(1);
        return df.format(x);
    }


}
