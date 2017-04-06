package com.pimpimmobile.librealarm;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * Created by jamorham on 05/04/2017.
 */

public class BootCompletedReceiver extends WakefulBroadcastReceiver {

    private static final String TAG = "LibreAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "Received startup broadcast!");
        if (JoH.ratelimit("broadcast", 300)) {
            Intent i = new Intent(context, WearService.class);
            context.startService(i);
        }
    }

}
