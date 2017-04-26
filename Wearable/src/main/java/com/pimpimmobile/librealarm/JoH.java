
package com.pimpimmobile.librealarm;

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

}
