package com.pimpimmobile.librealarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.pimpimmobile.librealarm.nightscout.NightscoutPreferences;
import com.pimpimmobile.librealarm.quicksettings.QuickSettingsItem;
import com.pimpimmobile.librealarm.quicksettings.QuickSettingsView;
import com.pimpimmobile.librealarm.quicksettings.QuickSettingsView.QuickSettingsChangeListener;
import com.pimpimmobile.librealarm.shareddata.GlucoseData;
import com.pimpimmobile.librealarm.shareddata.PredictionData;
import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;
import com.pimpimmobile.librealarm.shareddata.Status;
import com.pimpimmobile.librealarm.shareddata.Status.Type;
import com.pimpimmobile.librealarm.shareddata.WearableApi;
import com.pimpimmobile.librealarm.xdrip_plus.XdripPlusPreferences;

import java.util.Date;
import java.util.HashMap;

import static com.pimpimmobile.librealarm.R.string.sensor_error;
import static com.pimpimmobile.librealarm.shareddata.AlgorithmUtil.CONFIDENCE_LIMIT;
import static com.pimpimmobile.librealarm.shareddata.AlgorithmUtil.format;
import static com.pimpimmobile.librealarm.shareddata.Status.Type.ALARM_HIGH;
import static com.pimpimmobile.librealarm.shareddata.Status.Type.ALARM_LOW;

public class MainActivity extends Activity implements WearService.WearServiceListener,
        SimpleDatabase.DatabaseListener, HistoryAdapter.OnListItemClickedListener,
        AlarmDialogFragment.AlarmActionListener, QuickSettingsChangeListener {

    private static final String TAG = "LibreAlarm" + MainActivity.class.getSimpleName();

    private static final String INTENT_ALARM_ACTION = "alarm";

    private View mTriggerGlucoseButton;
    private TextView mStatusTextView;
    private TextView mStatsTextView;
    private HistoryAdapter mAdapter;
    private Button mActionButton;
    private ActionBarDrawerToggle mDrawerToggle;
    private WearService mService;
    private ProgressBar mProgressBar;
    private QuickSettingsView mQuickSettings;
    private TextView mSnoozeLowTextView;
    private View mSnoozeLowParent;
    private TextView mSnoozeHighTextView;
    private View mSnoozeHighParent;
    private boolean mIsFirstStartup;

    public static boolean activityVisible = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG,"Local Service connected!");
            mService = ((WearService.WearServiceBinder) service).getService();
            mService.setListener(MainActivity.this, MainActivity.this);
            onDataUpdated();
            mService.getDatabase().setListener(MainActivity.this);
            mService.getUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onDataUpdated();
            mService.getDatabase().setListener(MainActivity.this);
            mService.setListener(null, null);
            mService = null;
        }
    };

    public static Intent buildAlarmIntent(Context context, Status status) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(INTENT_ALARM_ACTION);
        intent.putExtra(AlarmDialogFragment.EXTRA_IS_HIGH, status.status == Type.ALARM_HIGH);
        intent.putExtra(AlarmDialogFragment.EXTRA_TREND_ORDINAL, status.alarmExtraTrendOrdinal);
        intent.putExtra(AlarmDialogFragment.EXTRA_VALUE, status.alarmExtraValue);
        return intent;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);

        ViewGroup layout = (ViewGroup) getLayoutInflater().inflate(R.layout.main_content, null);

        ((FrameLayout) findViewById(R.id.content_frame)).addView(layout);

        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mQuickSettings = (QuickSettingsView) findViewById(R.id.quick_settings);
        mQuickSettings.setUpdateListener(this);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        mStatusTextView = (TextView) layout.findViewById(R.id.status_view);
        mStatsTextView = (TextView) layout.findViewById(R.id.stats_view);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("first_startup", true)) {
            showDisclaimer(true);
            mIsFirstStartup = true;
        }

        mDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_close, R.string.drawer_open) {
            @Override
            public void onDrawerClosed(View drawerView) {
                HashMap<String, String> savedSettings = mQuickSettings.saveSettings();
                if (savedSettings.size() > 0) {
                    WearService.pushSettingsNow();
                    mService.sendData(WearableApi.SETTINGS, savedSettings, null);

                }
                super.onDrawerClosed(drawerView);
            }
        };
        drawerLayout.setDrawerListener(mDrawerToggle);

        bindService(new Intent(this, WearService.class), mConnection, BIND_AUTO_CREATE);
        startService(new Intent(this, WearService.class));

        mProgressBar = (ProgressBar) layout.findViewById(R.id.progress);
        mTriggerGlucoseButton = layout.findViewById(R.id.trigger_glucose);
        mTriggerGlucoseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mProgressBar.setVisibility(View.VISIBLE);
                mService.sendMessage(WearableApi.TRIGGER_GLUCOSE, "", null);
            }
        });

        mSnoozeHighTextView = (TextView) findViewById(R.id.snooze_high_text);
        findViewById(R.id.snooze_high_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.disableAlarm(getString(R.string.key_snooze_high), -1);
                prefs.edit().putString(getString(R.string.key_snooze_high), "-1").apply();
            }
        });

        mSnoozeHighParent = findViewById(R.id.snooze_high_parent);

        mSnoozeLowTextView = (TextView) findViewById(R.id.snooze_low_text);
        findViewById(R.id.snooze_low_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.disableAlarm(getString(R.string.key_snooze_low), -1);
                prefs.edit().putString(getString(R.string.key_snooze_low), "-1").apply();
            }
        });

        mSnoozeLowParent = findViewById(R.id.snooze_low_parent);

        mActionButton = (Button) layout.findViewById(R.id.action);
        mActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mService != null && mService.getReadingStatus() != null) {
                    switch (mService.getReadingStatus().status) {
                        case ALARM_HIGH:
                        case ALARM_LOW:
                        case ALARM_OTHER:
                            mService.sendMessage(WearableApi.CANCEL_ALARM, "", null);
                            mService.stopAlarm();
                            break;
                        case NOT_RUNNING:
                            mService.start();
                            break;
                        default:
                            mService.stop();
                            break;
                    }
                }
            }
        });

        mAdapter = new HistoryAdapter(this, this);
        RecyclerView recyclerView = (RecyclerView) layout.findViewById(R.id.history);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter.setHistory(null);
        perhapsShowAlarmFragment(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        perhapsShowAlarmFragment(intent);
    }

    private void perhapsShowAlarmFragment(Intent intent) {
        int extraValue = intent.getIntExtra(AlarmDialogFragment.EXTRA_VALUE, 0);

        if (INTENT_ALARM_ACTION.equals(intent.getAction()) && extraValue != 0) {

            AlarmDialogFragment fragment = AlarmDialogFragment.build(
                    intent.getBooleanExtra(AlarmDialogFragment.EXTRA_IS_HIGH, false),
                    intent.getIntExtra(AlarmDialogFragment.EXTRA_TREND_ORDINAL, 0), extraValue);

            Fragment oldFragment = getFragmentManager().findFragmentByTag("alarm");
            if (oldFragment != null) ((AlarmDialogFragment) oldFragment).dismiss();

            fragment.show(getFragmentManager().beginTransaction(), "alarm");
        }
    }

    private void showDisclaimer(final boolean mustBePositive) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mustBePositive) {
                            PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                                    .putBoolean("first_startup", false).apply();
                        }
                    }
                })
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.disclaimer_message);
        if (mustBePositive) {
            dialogBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            dialogBuilder.setOnKeyListener(new Dialog.OnKeyListener() {

                @Override
                public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        finish();
                    }
                    return true;
                }
            });
        }
        AlertDialog dialog = dialogBuilder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        if (mService != null) onDataUpdated();
        if (JoH.ratelimit("battery-optimize", 5)) {
            checkBatteryOptimization();
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();
            //Log.d(TAG, "Maybe ignoring battery optimization");
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "Requesting ignore battery optimization");
                try {
                    final Intent intent = new Intent();
                    // ignoring battery optimizations required for constant connection
                    // to peripheral device - eg CGM transmitter.
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    final String msg = "Device does not appear to support battery optimization whitelisting!";
                    JoH.static_toast_short(msg);
                    Log.wtf(TAG, msg);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        if (mService != null) mService.setListener(null, null);
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }
    @Override
    public void onPause() {
        super.onPause();
        activityVisible = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Bundle bundle = data == null ? null : data.getBundleExtra("result");
        if (bundle != null) {

            HashMap<String, String> settingsUpdated = new HashMap<>();
            for (String key : bundle.keySet()) {
                settingsUpdated.put(key, bundle.getString(key));
            }
            if (settingsUpdated.containsKey(getString(R.string.pref_key_mmol)))
                mQuickSettings.refresh();
            if (settingsUpdated.size() > 0) {
                if (mService != null) {
                    WearService.pushSettingsNow();
                    mService.sendData(WearableApi.SETTINGS, settingsUpdated, null);
                } else {
                    Log.wtf(TAG, "mService was null when settings were updated");
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        final int id = item.getItemId();
        if (id == R.id.disclaimer) {
            showDisclaimer(false);
        } else if (id == R.id.nightscout) {
            startActivity(new Intent(this, NightscoutPreferences.class));
        } else if (id == R.id.xdrip_plus) {
            startActivityForResult(new Intent(this, XdripPlusPreferences.class), 0);
        } else if (id == R.id.preferences) {
            startActivityForResult(new Intent(this, Preferences.class), 0);
        } else if (id == R.id.reboot) {
            rebootWatch();
        } else if (id == R.id.clearstats) {
            clearStats();
        } else if (id == R.id.downloadxdripplus) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://xdrip-plus-updates.appspot.com/stable/xdrip-plus-latest.apk")));
        }
        return super.onOptionsItemSelected(item);
    }

    private void rebootWatch() {
        if (mService != null) {
            mService.reboot();
            JoH.static_toast_long(this, getString(R.string.reboot_command));
        }
    }

    private void clearStats() {
        if (mService != null) {
            mService.clearstats();
            JoH.static_toast_long(this, getString(R.string.clear_stats_command));
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDataUpdated() {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onDatabaseChange();
                Status status = mService.getReadingStatus();
                mProgressBar.setVisibility((status != null && status.status == Type.ATTEMPTING) ? View.VISIBLE : View.GONE);
                if (status != null && mService.isConnected()) {
                    switch (status.status) {
                        case ALARM_HIGH:
                        case ALARM_LOW:
                        case ALARM_OTHER:
                            mActionButton.setText(R.string.button_alarm);
                            mActionButton.setVisibility(View.VISIBLE);
                            mTriggerGlucoseButton.setVisibility(View.GONE);
                            break;
                        case ATTEMPTING:
                        case ATTENPT_FAILED:
                        case WAITING:
                            if (!PreferencesUtil.getIsStartedPhone(getApplicationContext())) {
                                Log.e(TAG, "Marking as started when previously wasn't");
                                PreferencesUtil.setIsStartedPhone(getApplicationContext(), true);
                            }
                            mActionButton.setText(R.string.button_stop);
                            mActionButton.setVisibility(View.VISIBLE);
                            mTriggerGlucoseButton.setVisibility(View.VISIBLE);
                            break;
                        case NOT_RUNNING:
                            mActionButton.setText(R.string.button_start);
                            mActionButton.setVisibility(View.VISIBLE);
                            mTriggerGlucoseButton.setVisibility(View.GONE);
                            break;
                    }
                } else {
                    mActionButton.setVisibility(View.GONE);
                    mTriggerGlucoseButton.setVisibility(View.GONE);
                }

                if (mIsFirstStartup && status == null) {
                    mStatusTextView.setText(R.string.status_message_first_startup);
                } else {

                    if (mService == null) {
                        mStatusTextView.append("\nService is not connected, don't try to change anything yet!");
                    } else {
                        mStatusTextView.setText(mService.getStatusString());
                        // TODO add layout item for battery instead of using append
                        if (mService.getBatteryLevel() > 0)
                            mStatusTextView.append(" Batt: " + mService.getBatteryLevel() + "%");

                        final String statsString = mService.getStatsString();
                        if (statsString.length() > 0) mStatsTextView.setText(statsString);
                    }

                    if (PreferencesUtil.shouldUseRoot(context) && status != null &&
                            status.status == Type.ATTEMPTING && !status.hasRoot) {
                        mStatusTextView.append(" (no SuperSU)");
                    }
                    // simple indicator of root status, supersu root for wear is available at:
                    // http://forum.xda-developers.com/attachment.php?attachmentid=3342605&d=1433157678
                    // sha1: 00c2ccd6ff356fa5cf73124e978fc192af186d2d

                }

                updateAlarmSnoozeViews();
            }
        });

    }

    private void updateAlarmSnoozeViews() {

        long snoozeHigh = Long.valueOf(PreferencesUtil.getString(this,
                getString(R.string.key_snooze_high) + QuickSettingsItem.WATCH_VALUE));
        if (snoozeHigh > System.currentTimeMillis()) {
            mSnoozeHighParent.setVisibility(View.VISIBLE);
            mSnoozeHighTextView.setText(getString(R.string.alarm_disabled_high_text,
                    format(new Date(snoozeHigh))));
        } else {
            mSnoozeHighParent.setVisibility(View.GONE);
        }

        long snoozeLow = Long.valueOf(PreferencesUtil.getString(this,
                getString(R.string.key_snooze_low) + QuickSettingsItem.WATCH_VALUE));
        if (snoozeLow > System.currentTimeMillis()) {
            mSnoozeLowParent.setVisibility(View.VISIBLE);
            mSnoozeLowTextView.setText(getString(R.string.alarm_disabled_low_text,
                    format(new Date(snoozeLow))));
        } else {
            mSnoozeLowParent.setVisibility(View.GONE);
        }
    }


    @Override
    public void onDatabaseChange() {
        mAdapter.setHistory(mService.getDatabase().getPredictions());
    }

    @Override
        public void onAdapterItemClicked(PredictionData predictionData) {
            String s = "";

            boolean isMmol = PreferencesUtil.getBoolean(this, getString(R.string.pref_key_mmol), true);

            if (predictionData.glucoseLevel == -1) { // ERR
                s = getString(R.string.err_explanation);
            } else {
                for (GlucoseData data : mService.getDatabase().getTrend(predictionData.phoneDatabaseId)) {
                    s += format(new Date(data.realDate)) + ": " + data.glucose(isMmol) + "\n";
                }
                if (predictionData.glucoseLevel == 0 || predictionData.glucoseLevel < 0 || predictionData.confidence > CONFIDENCE_LIMIT) { // Sensor ERR null // Sensor ERR negative // Sensor ERR TrendArrow
                    s = getString(sensor_error);
                }
            }

        AlertDialog dialog = new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, null)
                .setTitle("").setMessage(s).create();
        dialog.show();
    }

    @Override
    public void turnOffAlarm() {
        mService.sendMessage(WearableApi.CANCEL_ALARM, "", null);
        mService.stopAlarm();
    }

    @Override
    public void snooze(int minutes, boolean isGlucoseHigh) {
        if (mService != null) {
            mService.sendMessage(WearableApi.CANCEL_ALARM, "", null);
            mService.disableAlarm(isGlucoseHigh ?
                    getString(R.string.key_snooze_high) :
                    getString(R.string.key_snooze_low), minutes);
            mService.stopAlarm();
        }
    }

    @Override
    public void onWatchSettingsUpdated() {
        mQuickSettings.watchValuesUpdated();
    }

    @Override
    public void onQuickSettingsChanged(String key, String value) {
        WearService.pushSettingsNow();
        if (mService != null) {
            mService.sendData(WearableApi.SETTINGS, key, value, null);
        }
    }
}