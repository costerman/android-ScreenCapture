package com.example.android.screencapture;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by costerman on 2/12/15.
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
