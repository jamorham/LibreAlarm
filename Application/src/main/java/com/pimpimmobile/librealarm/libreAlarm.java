package com.pimpimmobile.librealarm;

import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import io.fabric.sdk.android.Fabric;

import static com.pimpimmobile.librealarm.WearService.immortality;

/**
 * Created by jamorham on 04/04/2017.
 */

public class libreAlarm extends Application {

    private static final String TAG = "libreAlarm";

    private static boolean fabricInited = false;
    private static Context context;

    @Override
    public void onCreate() {
        context = getApplicationContext();
        Log.d(TAG, "APPLICATION CREATED !!!");
        super.onCreate();
        try {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("enable_crashlytics", true)) {
                initCrashlytics(this);
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception enabling crashlytics");
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        PreferenceManager.setDefaultValues(this, R.xml.nightscout_preferences, true);
        PreferenceManager.setDefaultValues(this, R.xml.xdrip_plus_preferences, true);

        immortality(this, 5000);
    }

    public static Context getAppContext() {
        return context;
    }


    public synchronized static void initCrashlytics(Context context) {
        if (!fabricInited) {
            try {
                Crashlytics crashlyticsKit = new Crashlytics.Builder()
                        .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                        .build();
                Fabric.with(context, crashlyticsKit);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            fabricInited = true;
        }
    }


}
