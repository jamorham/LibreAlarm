package com.pimpimmobile.librealarm;

import android.app.Application;
import android.content.Context;
import android.nfc.NfcManager;
import android.util.Log;

import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;


/**
 * Created by jamorham on 04/04/2017.
 */

public class libreAlarm extends Application {

    private static final String TAG = "libreAlarm";
    public static Boolean hasNFC;
    // private static Context context;

    @Override
    public void onCreate() {
        //   context = getApplicationContext();
        Log.d(TAG, "APPLICATION CREATED !!!");
        super.onCreate();
        // detect if this device has nfc
        final NfcManager nfcManager = (NfcManager) this.getSystemService(Context.NFC_SERVICE);
        hasNFC = nfcManager.getDefaultAdapter() != null;
        if (hasNFC && (PreferencesUtil.shouldUseRoot(getApplicationContext()))) {
            RootTools.swichNFCState(false); // disable on start
        }
    }

    public static boolean noNFC() {
        return hasNFC != null && !hasNFC;
    }

    //   public static Context getAppContext() {
    //      return context;
    //  }

}
