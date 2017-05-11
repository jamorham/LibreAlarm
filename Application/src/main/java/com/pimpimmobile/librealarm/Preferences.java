package com.pimpimmobile.librealarm;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.util.HashMap;

public class Preferences extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private HashMap<String, String> mChanged = new HashMap<>();

    static CheckBoxPreference half_speed;
    static EditTextPreference half_speed_value;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public void onBackPressed() {
        setResult();
        WearService.pushSettingsNow();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        WearService.pushSettingsNow();
        half_speed = null;
        half_speed_value = null;
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getString(R.string.pref_key_glucose_interval).equals(key)) {
            String value = sharedPreferences.getString(key, "5");
            if (TextUtils.isEmpty(value) || Integer.valueOf(value) < 1) {
                sharedPreferences.edit().putString(key, "5").apply();
            }
        } else if (getString(R.string.pref_key_half_percent).equals(key)) {
            String value = sharedPreferences.getString(key, "30");
            if (TextUtils.isEmpty(value) || (Integer.valueOf(value) < 1) || (Integer.valueOf(value) > 90)) {
                sharedPreferences.edit().putString(key, "30").apply();
            }
        }
        mChanged.put(key, sharedPreferences.getAll().get(key).toString());
        SettingsFragment.freshenDynamicEntries();
    }

    private void setResult() {
        Intent result = new Intent();
        Bundle bundle = new Bundle();
        for (String key : mChanged.keySet()) {
            bundle.putString(key, mChanged.get(key));
        }
        result.putExtra("result", bundle);
        setResult(RESULT_OK, result);
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // TODO not really the right way to do this
            half_speed = (CheckBoxPreference) findPreference(getString(R.string.pref_key_auto_half_speed));
            half_speed_value = (EditTextPreference) findPreference(getString(R.string.pref_key_half_percent));

            freshenDynamicEntries();
        }

        private static void freshenDynamicEntries() {
            try {
                half_speed.setSummary(libreAlarm.getAppContext().getString(R.string.scan_half_as_often_when_watch) + " " + half_speed_value.getText() + "%");
            } catch (Exception e) {
                //
            }
        }

    }


}