package com.example.android.screencapture;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

/**
 * Created by costerman on 2/12/15.
 */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String KEY_PREF_CAPTURE_SCREENSHOT = "pref_capture_screenshot";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){
        if(key.equals(KEY_PREF_CAPTURE_SCREENSHOT)){
            Preference captureScreenshotPref = findPreference(key);
            captureScreenshotPref.setSummary(sharedPreferences.getString(key, ""));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        getPreferenceScreen().getSharedPreferences()
//                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        getPreferenceScreen().getSharedPreferences()
//                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
