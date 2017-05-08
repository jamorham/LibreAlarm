package com.pimpimmobile.librealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.ALARM_SERVICE;

/**
 * Created by jamorham on 04/04/2017.
 */

public class JoH {

    private static final boolean debug_wakelocks = false;
    private static final String TAG = "LibreAlarm-JoH";

    private static double benchmark_time = 0;
    private static Map<String, Double> benchmarks = new HashMap<String, Double>();
    private static final Map<String, Long> rateLimits = new HashMap<String, Long>();

    public static long tsl() {
        return System.currentTimeMillis();
    }

    public static long msSince(long when) {
        return (tsl() - when);
    }

    public static long msTill(long when) {
        return (when - tsl());
    }

    public static long absMsSince(long when) {
        return Math.abs(tsl() - when);
    }

    public static PowerManager.WakeLock getWakeLock(final String name, int millis) {
        final PowerManager pm = (PowerManager) libreAlarm.getAppContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
        wl.acquire(millis);
        if (debug_wakelocks) Log.d(TAG, "getWakeLock: " + name + " " + wl.toString());
        return wl;
    }

    public static void releaseWakeLock(PowerManager.WakeLock wl) {
        if (debug_wakelocks) Log.d(TAG, "releaseWakeLock: " + wl.toString());
        if (wl.isHeld()) wl.release();
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

    public static void cancelAlarm(Context context, PendingIntent serviceIntent) {
        // do we want a try catch block here?
        final AlarmManager alarm = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (serviceIntent != null) {
            Log.d(TAG, "Cancelling alarm " + serviceIntent.getCreatorPackage());
            alarm.cancel(serviceIntent);
        } else {
            Log.d(TAG, "Cancelling alarm: serviceIntent is null");
        }
    }

    public static long wakeUpIntent(Context context, long delayMs, PendingIntent pendingIntent) {
        final long wakeTime = JoH.tsl() + delayMs;
        Log.d(TAG, "Scheduling wakeup intent: " + dateTimeText(wakeTime));
        final AlarmManager alarm = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarm.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
        } else
            alarm.set(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
        return wakeTime;
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

    public static void static_toast(final Context context, final String msg, final int length) {
        try {
            if (!runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Toast.makeText(context, msg, length).show();
                        Log.i(TAG, "Displaying toast using fallback");
                    } catch (Exception e) {
                        Log.e(TAG, "Exception processing runnable toast ui thread: " + e);
                    }
                }
            })) {
                Log.e(TAG, "Couldn't display toast via ui thread: " + msg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Couldn't display toast due to exception: " + msg + " e: " + e.toString());
        }
    }

    public static void static_toast_long(final String msg) {
        static_toast(libreAlarm.getAppContext(), msg, Toast.LENGTH_LONG);
    }

    public static void static_toast_short(final String msg) {
        static_toast(libreAlarm.getAppContext(), msg, Toast.LENGTH_SHORT);
    }

    public static void static_toast_long(Context context, final String msg) {
        static_toast(context, msg, Toast.LENGTH_LONG);
    }

}
