package com.pimpimmobile.librealarm;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.pimpimmobile.librealarm.nightscout.NightscoutUploader;
import com.pimpimmobile.librealarm.quicksettings.QuickSettingsItem;
import com.pimpimmobile.librealarm.shareddata.AlertRules;
import com.pimpimmobile.librealarm.shareddata.AlgorithmUtil;
import com.pimpimmobile.librealarm.shareddata.PredictionData;
import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;
import com.pimpimmobile.librealarm.shareddata.ReadingData;
import com.pimpimmobile.librealarm.shareddata.Status;
import com.pimpimmobile.librealarm.shareddata.WearableApi;
import com.pimpimmobile.librealarm.xdrip_plus.XdripPlusBroadcast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Service which keeps the phone connected to the watch.
 */
public class WearService extends Service implements DataApi.DataListener, MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "LibreAlarmService";

    private static final long FAILOVER_TIMEOUT = 11 * 60 * 1000;

    private final WearServiceBinder binder = new WearServiceBinder();

    private Activity mActivity;

    private GoogleApiClient mGoogleApiClient;

    private boolean mResolvingError;

    public WearServiceListener mListener;

    private MediaPlayer mAlarmPlayer;

    private TextToSpeech mTextToSpeech;

    private Status mReadingStatus;

    private static PredictionData mLastReading;

    private static PendingIntent serviceFailoverIntent;

    private static long failover_time;

    private static ArrayList<String> essential_settings;

    private static ArrayList<String> essential_string_settings;

    private static boolean push_settings_now = false;

    private boolean mNotificationShowing = false;

    private boolean mAlarmReceiverIsRunning = false;

    private SimpleDatabase mDatabase = new SimpleDatabase(this);

    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            // Nop
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    public static void pushSettingsNow() {
        push_settings_now = true;
    }

    private static boolean shouldServiceRun() {
        return true; // always on for now
    }

    public static void immortality(Context context, long period) {
        if (JoH.ratelimit("immortality", 5)) {
            if (period == 0) period = FAILOVER_TIMEOUT;
            setFailoverTimer(context, period);
            if (shouldServiceRun()) AlarmReceiver.post(libreAlarm.getAppContext());
        }
    }

    public synchronized static void setFailoverTimer(Context context, long period) {
        if (shouldServiceRun()) {
            Log.d(TAG, "setFailoverTimer: Fallover Restarting in: " + (period / 60000 + " minutes"));
            JoH.cancelAlarm(context, serviceFailoverIntent);
            serviceFailoverIntent = PendingIntent.getService(context, 0, new Intent(context, WearService.class), 0);
            failover_time = JoH.wakeUpIntent(libreAlarm.getAppContext(), period, serviceFailoverIntent);
        }
    }

    private static ResultCallback<MessageApi.SendMessageResult> mMessageListener =
            new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                    Log.e(TAG, "Message send result: " + sendMessageResult.getStatus().getStatusMessage());
                    //  runOnUiThread(new Runnable() {
                    //   @Override
                    //    public void run() {
                    //         Log.i(TAG, "messagesbeingsent: " + mMessagesBeingSent + ", finish? " + mFinishAfterSentMessages);
                    //         if (--mMessagesBeingSent <= 0 && mFinishAfterSentMessages) {
                    //             finish();
                    //         }
                    //         }
                    //   });
                }
            };

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        final PowerManager.WakeLock wl = JoH.getWakeLock("onMessageReceived", 60000);
        final Context context = this;
        JoH.runOnUiThread(new Runnable() {
            @Override
            public void run() {


                try {
                    immortality(context, 0);
                    String data = new String(messageEvent.getData(), Charset.forName("UTF-8"));
                    Log.i(TAG, "Message receiver: " + messageEvent.getPath() + ", " + data);
                    switch (messageEvent.getPath()) {
                        case WearableApi.CANCEL_ALARM:
                            stopAlarm();
                            break;
                        case WearableApi.SETTINGS:
                            Toast.makeText(context, R.string.settings_updated_on_watch, Toast.LENGTH_LONG).show();
                            HashMap<String, String> prefs = PreferencesUtil.toMap(data);
                            for (String key : prefs.keySet()) {
                                final String this_value = prefs.get(key);
                                if (this_value.equals(PreferencesUtil.TRUE_MARKER) || this_value.equals(PreferencesUtil.FALSE_MARKER)) {
                                    // its a boolean type ignore for now as none are sourced from the watch
                                    //PreferencesUtil.putBoolean(context, key, this_value.equals(PreferencesUtil.TRUE_MARKER));
                                } else {
                                    PreferencesUtil.putString(context, key + QuickSettingsItem.WATCH_VALUE, prefs.get(key));
                                }
                            }

                            if (mListener != null) mListener.onWatchSettingsUpdated();
                            break;
                        case WearableApi.STATUS:
                            mReadingStatus = new Gson().fromJson(data, Status.class);
                            Status.Type type = mReadingStatus.status;
                            if (type == Status.Type.ALARM_HIGH || type == Status.Type.ALARM_LOW) {
                                startAlarm(mReadingStatus);
                            } else {
                                stopAlarm();
                            }
                            updateAlarmReceiver(type != Status.Type.NOT_RUNNING);
                            updateNotification(type != Status.Type.NOT_RUNNING);
                            if (mListener != null) mListener.onDataUpdated();

                            if (essential_settings != null) {
                                if ((push_settings_now) || (JoH.ratelimit("push-settings", 86400))) {
                                    push_settings_now = false;
                                    Log.d(TAG, "Sending essential settings!");
                                    final HashMap<String, String> map = new HashMap<>();
                                    for (String key : essential_settings) {
                                        map.put(key, PreferencesUtil.getBoolean(context, key, false) ? PreferencesUtil.TRUE_MARKER : PreferencesUtil.FALSE_MARKER);
                                    }
                                    for (String key : essential_string_settings) {
                                        map.put(key, PreferencesUtil.getString(context, key, ""));
                                    }
                                    sendData(WearableApi.SETTINGS, map, null);
                                }
                            }
                            break;
                        case WearableApi.GLUCOSE:
                            ReadingData.TransferObject object =
                                    new Gson().fromJson(data, ReadingData.TransferObject.class);
                            if (mLastReading == null || mLastReading.realDate < object.data.prediction.realDate) {
                                mLastReading = object.data.prediction;
                                if (mAlarmReceiverIsRunning) AlarmReceiver.post(context);
                                if (mReadingStatus != null) {
                                    updateNotification((mReadingStatus.status == null ? null : mReadingStatus.status != Status.Type.NOT_RUNNING));
                                }
                            }

                            mDatabase.storeReading(object.data);
                            WearableApi.sendMessage(mGoogleApiClient, WearableApi.GLUCOSE, String.valueOf(object.id), mMessageListener);
                            if (mListener != null) mListener.onDataUpdated();
                            if (PreferencesUtil.isXdripPlusEnabled(context))
                                XdripPlusBroadcast.syncXdripPlus(getApplicationContext(), data, object, getBatteryLevel());
                            if (PreferencesUtil.isNsRestEnabled(context)) syncNightscout();
                            runTextToSpeech(object.data.prediction);
                            break;
                    }
                } finally {
                    JoH.releaseWakeLock(wl);
                }
            }
        });
    }


    private void updateAlarmReceiver(boolean started) {
        if (started && !mAlarmReceiverIsRunning) {
            AlarmReceiver.start(this);
        } else if (!started && mAlarmReceiverIsRunning) {
            AlarmReceiver.stop(this);
        }
        mAlarmReceiverIsRunning = started;
    }

    private void updateNotification(Boolean show) {
        if (show == null) {
            show = mNotificationShowing;
        }

        if (show) {
            boolean isMmol = PreferencesUtil.getBoolean(this, getString(R.string.pref_key_mmol), true);
            Notification.Builder builder = new Notification.Builder(this);
            builder.setSmallIcon(R.drawable.ic_launcher);
            Intent notificationIntent = new Intent(this, MainActivity.class);

            if (mLastReading != null) {
                builder.setContentTitle("" + mLastReading.glucose(isMmol) + " " +
                        (isMmol ? getString(R.string.mmol) : getString(R.string.mgdl)));
            } else {
                builder.setContentTitle("");
            }
            builder.setContentText("");
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            builder.setContentIntent(pendingIntent);
            startForeground(1922, builder.build());
        } else {
            stopForeground(true);
        }
        mNotificationShowing = show;
    }


    private void syncNightscout() {
        final List<PredictionData> data = mDatabase.getNsSyncData();
        new Thread() {
            @Override
            public void run() {
                final PowerManager.WakeLock wl = JoH.getWakeLock("nightscout-up", 60000);
                try {
                    NightscoutUploader uploader = new NightscoutUploader(WearService.this);
                    List<PredictionData> result = uploader.upload(data);
                    mDatabase.setNsSynced(result);
                    super.run();
                } finally {
                    JoH.releaseWakeLock(wl);
                }
            }
        }.start();
    }

    public void start() {
        Log.e(TAG, "Starting everything");
        WearableApi.sendMessage(mGoogleApiClient, WearableApi.START, "", mMessageListener);
        PreferencesUtil.setIsStartedPhone(this, true);
    }

    public void stop() {
        Log.e(TAG, "Stopping everything");
        WearableApi.sendMessage(mGoogleApiClient, WearableApi.STOP, "", mMessageListener);
        PreferencesUtil.setIsStartedPhone(this, false);
    }

    public void getUpdate() {
        WearableApi.sendMessage(mGoogleApiClient, WearableApi.GET_UPDATE, "", mMessageListener);
    }

    public void disableAlarm(String key, int minutes) {
        String value = String.valueOf((long) minutes * 60000 + System.currentTimeMillis());
        sendData(WearableApi.SETTINGS, key, value, null);
    }

    public class WearServiceBinder extends Binder {
        public WearService getService() {
            return WearService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupTextToSpeech();
        reconnectGoogleApi();
        populateEssentialSettings();
    }

    private synchronized void populateEssentialSettings() {
        if (essential_settings == null) {
            essential_settings = new ArrayList<>();
            essential_settings.add(getString(R.string.key_all_alarms_disabled));
            essential_settings.add(getString(R.string.pref_key_root));
            essential_settings.add(getString(R.string.pref_key_clock_speed));
            essential_settings.add(getString(R.string.pref_key_disable_touchscreen));
            essential_settings.add(getString(R.string.pref_key_uninstall_xdrip));

            essential_string_settings = new ArrayList<>();
            essential_string_settings.add(getString(R.string.pref_key_glucose_interval));
        }
    }

    private void reconnectGoogleApi() {
        if (JoH.ratelimit("reconnect-google", 15)) {
            try {
                if (!isConnected()) {
                    Log.d(TAG, "Attempting to reconnect wear api");
                    mGoogleApiClient.connect();
                }
            } catch (NullPointerException e) {
                Log.d(TAG, "Creating new api connection");
                mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(Wearable.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
                mGoogleApiClient.connect();
            }

        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final PowerManager.WakeLock wl = JoH.getWakeLock("WearStartCmd", 60000);
        try {
            Log.i(TAG, "onStartCommand!!! " + JoH.dateTimeText(JoH.tsl()));
            if (!isConnected()) reconnectGoogleApi();
            if (PreferencesUtil.getIsStartedPhone(this)) {
                immortality(this, 0);
                if (intent != null) {
                    Log.d(TAG, "Intent: " + intent.getAction());
                } else {
                    Log.d(TAG, "Null intent");
                }
                if (mReadingStatus == null) {
                    Log.d(TAG, "mReadingStatus is Null");
                } else {
                    Log.d(TAG, "mReadingStatus status: " + mReadingStatus.status);
                }

                if ((mReadingStatus == null) || (mReadingStatus.status == Status.Type.NOT_RUNNING)) {
                    if (JoH.ratelimit("force-start", 600)) {
                        Log.e(TAG, "Force starting service");
                        start();
                    }
                }
                // TODO check what if mReadingStatus == null
                if (intent != null && "alarmreceiver".equals(intent.getAction())
                        && PreferencesUtil.getIsStartedPhone(this)) {
                    //  &&  mReadingStatus != null && mReadingStatus.status != Status.Type.NOT_RUNNING) {
                    long delay = Integer.valueOf(PreferencesUtil.getCheckGlucoseInterval(this)) * 60000;
                    if (mLastReading == null || mLastReading.realDate + delay < System.currentTimeMillis()) {
                        if (JoH.ratelimit("trigger-glucose", 20)) {
                            Log.i(TAG, "Trigger Glucose!");
                            sendMessage(WearableApi.TRIGGER_GLUCOSE, "", mMessageListener);
                        }
                    } else {
                        Log.d(TAG, "last reading was: " + JoH.msSince(mLastReading.realDate));
                    }
                }
                reconnectGoogleApi();
            } else {
                Log.e(TAG, "Service is marked as stopped - shutting down");
                stopSelf();
                return START_NOT_STICKY;
            }
        } finally {
            JoH.releaseWakeLock(wl);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        immortality(this, 0);
        if (!mResolvingError) {
            try {
                Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            } catch (NullPointerException e) {
                Log.e(TAG, "Got null pointer in onDestroy: " + e);
            }
            stopAlarm();
        }

        try {
            mTextToSpeech.shutdown();
        } catch (NullPointerException e) {
            //
        }
        try {
            mAlarmPlayer.release();
        } catch (NullPointerException e) {
            //
        }
        try {
            mDatabase.close();
        } catch (NullPointerException e) {
            //
        }
        super.onDestroy();
    }

    public boolean isConnected() {
        return mGoogleApiClient != null && mGoogleApiClient.isConnected();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Wear connected");
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        mResolvingError = false;
        if (mListener != null) mListener.onDataUpdated();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        if (mListener != null) mListener.onDataUpdated();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution() && mActivity != null) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(mActivity, 1000);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            Log.e(TAG, "Connection to Google API client has failed");
            mResolvingError = false;
            if (mListener != null) mListener.onDataUpdated();
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
        }
    }

    public SimpleDatabase getDatabase() {
        return mDatabase;
    }

    public Status getReadingStatus() {
        return mReadingStatus;
    }

    private void startAlarm(Status status) {
        boolean usePhoneAlarm = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_key_phone_alarm), false);
        if (usePhoneAlarm) {
            if (mAlarmPlayer == null) {
                mAlarmPlayer = MediaPlayer.create(
                        this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            }
            mAlarmPlayer.start();
        }

        startActivity(MainActivity.buildAlarmIntent(this, status));
    }

    public void stopAlarm() {
        if (mAlarmPlayer != null && mAlarmPlayer.isPlaying()) {
            mAlarmPlayer.pause();
            mAlarmPlayer.seekTo(0);
        }
    }

    private void setupTextToSpeech() {
        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                if (status == TextToSpeech.ERROR) {
                    Toast.makeText(WearService.this, R.string.error_text_to_speech_init, Toast.LENGTH_LONG).show();
                }
            }
        });

        mTextToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {

            }

            @Override
            public void onDone(String utteranceId) {
                AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
                manager.abandonAudioFocus(mAudioFocusChangeListener);
            }

            @Override
            public void onError(String utteranceId) {
                AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
                manager.abandonAudioFocus(mAudioFocusChangeListener);
            }
        });
    }

    private void runTextToSpeech(PredictionData data) {

        final PowerManager.WakeLock wl = JoH.getWakeLock("tts", 30000);
        try {
            if (!PreferencesUtil.getBoolean(this, getString(R.string.pref_key_text_to_speech)))
                return;

            boolean alarmOnly = PreferencesUtil.getBoolean(this, getString(R.string.pref_key_text_to_speech_only_alarm));

            if (alarmOnly && AlertRules.checkDontPostpone(this, data) == AlertRules.Danger.NOTHING)
                return;

            AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
            manager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

            String message;

            if (data.errorCode == PredictionData.Result.OK) {
                boolean isMmol = PreferencesUtil.getBoolean(this, getString(R.string.pref_key_mmol), true);
                String glucose = data.glucose(isMmol);

                AlgorithmUtil.TrendArrow arrow = AlgorithmUtil.getTrendArrow(data);
                String trend;
                switch (arrow) {
                    case UP:
                        trend = getString(R.string.text_to_speech_trend_up);
                        break;
                    case DOWN:
                        trend = getString(R.string.text_to_speech_trend_down);
                        break;
                    case SLIGHTLY_UP:
                        trend = getString(R.string.text_to_speech_trend_slightly_up);
                        break;
                    case SLIGHTLY_DOWN:
                        trend = getString(R.string.text_to_speech_trend_slightly_down);
                        break;
                    case FLAT:
                        trend = getString(R.string.text_to_speech_trend_flat);
                        break;
                    case UNKNOWN:
                    default:
                        trend = getString(R.string.text_to_speech_trend_unknown);
                        break;
                }
                message = getString(R.string.text_to_speech_message, glucose, trend);
            } else {
                message = getString(R.string.text_to_speech_error);
            }

            if (Build.VERSION.SDK_INT < 21) {
                HashMap<String, String> map = new HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "glucose-speech");
                mTextToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, map);
            } else {
                mTextToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "glucose-speech");
            }

        } finally {
            JoH.releaseWakeLock(wl);
        }
    }

    public int getBatteryLevel() {
        if ((mReadingStatus != null) && (mReadingStatus.battery > 0)) {
            return mReadingStatus.battery;
        } else {
            return 0;
        }
    }

    public String getStatusString() {
        if (isConnected() && mReadingStatus != null) {
            switch (mReadingStatus.status) {
                case ALARM_HIGH:
                case ALARM_LOW:
                case ALARM_OTHER:
                    return getString(R.string.status_text_alarm);
                case ATTEMPTING:
                    return getString(R.string.status_check_attempt, mReadingStatus.attempt, mReadingStatus.maxAttempts);
                case ATTENPT_FAILED:
                    return getString(R.string.status_check_attempt_failed, mReadingStatus.attempt, mReadingStatus.maxAttempts);
                case WAITING:
                    return getString(R.string.status_text_next_check, AlgorithmUtil.format(new Date(mReadingStatus.nextCheck)));
                case NOT_RUNNING:
                    return getString(R.string.status_text_not_running);
                default:
                    return "";
            }
        } else {
            if (!isConnected()) {
                if (JoH.ratelimit("poll-update", 15)) {
                    getUpdate();
                    JoH.runOnUiThreadDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if ((MainActivity.activityVisible) && (!isConnected())) getUpdate();
                        }
                    }, 16000);
                }
            }
            return isConnected() ? "Not connected" : "Wear not connected";
        }
    }

    public void sendData(String command, HashMap<String, String> data, ResultCallback<DataApi.DataItemResult> listener) {
        if (isConnected()) WearableApi.sendData(mGoogleApiClient, command, data, listener);
    }

    public void sendData(String command, String key, String data, ResultCallback<DataApi.DataItemResult> listener) {
        if (isConnected()) WearableApi.sendData(mGoogleApiClient, command, key, data, listener);
    }

    public void sendMessage(String command, String message, ResultCallback<MessageApi.SendMessageResult> listener) {
        if (isConnected()) {
            WearableApi.sendMessage(mGoogleApiClient, command, message, listener);
        } else {
            Log.e(TAG, "Google api not connected");
            reconnectGoogleApi();
        }
    }

    public void setListener(Activity activity, WearServiceListener listener) {
        mActivity = activity;
        mListener = listener;
    }

    public interface WearServiceListener {
        void onDataUpdated();

        void onWatchSettingsUpdated();
    }
}
