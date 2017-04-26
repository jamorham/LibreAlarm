package com.pimpimmobile.librealarm;

import android.app.Application;
import android.content.Context;
import android.util.Log;


/**
 * Created by jamorham on 04/04/2017.
 */

public class libreAlarm extends Application {

    private static final String TAG = "libreAlarm";

    // private static Context context;

    @Override
    public void onCreate() {
        //   context = getApplicationContext();
        Log.d(TAG, "APPLICATION CREATED !!!");
        super.onCreate();
    }

    //   public static Context getAppContext() {
    //      return context;
    //  }

}
