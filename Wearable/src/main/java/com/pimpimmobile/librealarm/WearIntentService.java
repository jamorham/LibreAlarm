package com.pimpimmobile.librealarm;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;
import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;
import com.pimpimmobile.librealarm.shareddata.Status;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class WearIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = "LibreIntent";

    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_FOO = "com.pimpimmobile.librealarm.action.FOO";
    private static final String ACTION_BAZ = "com.pimpimmobile.librealarm.action.BAZ";

    // TODO: Rename parameters
    private static final String EXTRA_PARAM1 = "com.pimpimmobile.librealarm.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.pimpimmobile.librealarm.extra.PARAM2";

    public static GoogleApiClient mGoogleApiClient;
    private static MessageApi.MessageListener remoteListener;

    public WearIntentService() {
        super("WearIntentService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionFoo(Context context, String param1, String param2) {
        Intent intent = new Intent(context, WearIntentService.class);
        intent.setAction(ACTION_FOO);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    public static void startActionDefault(Context context) {
        Intent intent = new Intent(context, WearIntentService.class);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionBaz(Context context, String param1, String param2) {
        Intent intent = new Intent(context, WearIntentService.class);
        intent.setAction(ACTION_BAZ);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final PowerManager.WakeLock wl = JoH.getWakeLock(this, "intent-service", 30000);
        Log.e(TAG, "IntentService Triggered!");
        try {
            if (intent != null) {
                final String action = intent.getAction();
                if (ACTION_FOO.equals(action)) {
                    final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                    final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                    handleActionFoo(param1, param2);
                } else if (ACTION_BAZ.equals(action)) {
                    final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                    final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                    handleActionBaz(param1, param2);
                }
            }


            if (libreAlarm.noNFC()) {
                Log.e(TAG, "Device has no NFC! - not doing anything");
            } else {

                reconnectGoogle();

                if (PreferencesUtil.shouldUseRoot(this)) {
                    Log.d(TAG, "Using ROOT options!");
                    RootTools.executeScripts(true); // turn it on
                } else {
                    Log.d(TAG, "Not using root options");
                }
                final NfcManager nfcManager =
                        (NfcManager) this.getSystemService(Context.NFC_SERVICE);
                NfcAdapter mNfcAdapter = nfcManager.getDefaultAdapter(); // could be static?
                // mNfcAdapter.disableForegroundDispatch(this);
                if (mNfcAdapter != null) {
                    Log.d(TAG, "Got NFC adpater - intent service");
                    int counter = 0;
                    try {
                        // null pointer can trigger here from the systemapi
                        while (((!mNfcAdapter.isEnabled() || (!mGoogleApiClient.isConnected())) && counter < 9)) {
                            Log.d(TAG, "intent service nfc turned on (" + mNfcAdapter.isEnabled() + ") wait: " + counter + " google: " + mGoogleApiClient.isConnected());
                            try {
                                // quick and very dirty
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                //
                            }
                            counter++;
                        }

                    } catch (NullPointerException e) {
                        Log.wtf(TAG, "Null pointer exception from NFC subsystem: " + e.toString());
                        // TODO do we actually need to reboot watch here after some counter of failures without resolution?
                    }
                } else {
                    Log.e(TAG, "nfcAdapter is NULL!!");
                }

                // fire up the activity
                final Intent i = new Intent(this, WearActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            }
        } finally {
            JoH.releaseWakeLock(wl);
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionFoo(String param1, String param2) {
        // TODO: Handle action Foo
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private void handleActionBaz(String param1, String param2) {
        // TODO: Handle action Baz
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void reconnectGoogle() {
        Log.d(TAG, "Reconnect google called");
        if ((mGoogleApiClient == null) || (!mGoogleApiClient.isConnected())) {
            Log.d(TAG, "Attempting to connect to google api");
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                 //   .addOnConnectionFailedListener(this)
                    .build();

            mGoogleApiClient.connect();

        } else {
            Log.d(TAG, "Already connected google api");
            onConnected(null);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "google api is connected!! - not adding listener yet or sending status update!?");
        //Wearable.MessageApi.addListener(mGoogleApiClient, this);
        //sendStatusUpdate(Status.Type.ATTEMPTING);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    public synchronized static void tryToAddListener(MessageApi.MessageListener listener) {
        // TODO check nulls
        if (mGoogleApiClient.isConnected()) {
            removeExistingListener();
            Log.d(TAG, "Adding remote listener!");
            Wearable.MessageApi.addListener(mGoogleApiClient, listener);
        } else {
            Log.e(TAG, "Can't add listener as we are not connected!");
        }
    }

    public synchronized static void tryToRemoveListener(MessageApi.MessageListener listener) {
        // TODO check nulls
        if (mGoogleApiClient.isConnected()) {
            removeExistingListener();
            Wearable.MessageApi.removeListener(mGoogleApiClient, listener);
        }
    }

    private static void removeExistingListener() {
        if (remoteListener != null) {
            Log.e(TAG, "First removing remote listener!");
            Wearable.MessageApi.removeListener(mGoogleApiClient, remoteListener);
            remoteListener = null;
        }
    }

}
