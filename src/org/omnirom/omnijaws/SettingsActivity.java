/*
 *  Copyright (C) 2015 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnijaws;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener  {

    private SharedPreferences mPrefs;
    private ListPreference mProvider;
    private CheckBoxPreference mCustomLocation;
    //private CheckBoxPreference mAutoUpdates;
    private ListPreference mUnits;
    private SwitchPreference mEnable;
    private boolean mTriggerUpdate;
    private ListPreference mUpdateInterval;

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.xml.settings);

        mEnable = (SwitchPreference) findPreference(Config.PREF_KEY_ENABLE);
        mCustomLocation = (CheckBoxPreference) findPreference(Config.PREF_KEY_CUSTOM_LOCATION);
        //mAutoUpdates = (CheckBoxPreference) findPreference(Config.PREF_KEY_AUTO_UPDATE);

        mProvider = (ListPreference) findPreference(Config.PREF_KEY_PROVIDER);
        mProvider.setOnPreferenceChangeListener(this);
        int idx = mProvider.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_PROVIDER,
                mProvider.getEntryValues()[0].toString()));
        mProvider.setValueIndex(idx);
        mProvider.setSummary(mProvider.getEntries()[idx]);

        mUnits = (ListPreference) findPreference(Config.PREF_KEY_UNITS);
        mUnits.setOnPreferenceChangeListener(this);
        idx = mUnits.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_UNITS,
                mUnits.getEntryValues()[0].toString()));
        mUnits.setValueIndex(idx);
        mUnits.setSummary(mUnits.getEntries()[idx]);

        mUpdateInterval = (ListPreference) findPreference(Config.PREF_KEY_UPDATE_INTERVAL);
        mUpdateInterval.setOnPreferenceChangeListener(this);
        idx = mUpdateInterval.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_UPDATE_INTERVAL,
                mUpdateInterval.getEntryValues()[0].toString()));
        mUpdateInterval.setValueIndex(idx);
        mUpdateInterval.setSummary(mUpdateInterval.getEntries()[idx]);

        if (mPrefs.getBoolean(Config.PREF_KEY_ENABLE, false)
                && !mPrefs.getBoolean(Config.PREF_KEY_CUSTOM_LOCATION, false)) {
            mTriggerUpdate = false;
            checkPermissions();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTriggerUpdate) {
            WeatherService.scheduleUpdate(this);
        }
        mTriggerUpdate = false;
    }


    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mCustomLocation) {
            if (!mCustomLocation.isChecked()) {
                mTriggerUpdate = true;
                checkPermissions();
            } else {
                WeatherService.startUpdate(this, true);
            }
            return true;
        /*} else if (preference == mAutoUpdates) {
            if (mAutoUpdates.isChecked()) {
                WeatherService.startUpdate(this, true);
            } else {
                WeatherService.cancelUpdate(this);
            }
            return true;*/
        } else if (preference == mEnable) {
            if (mEnable.isChecked()) {
                if (!mCustomLocation.isChecked()) {
                    mTriggerUpdate = true;
                    checkPermissions();
                } else {
                    WeatherService.scheduleUpdate(this);
                }
            } else {
                // stop any pending
                WeatherService.cancelUpdate(this);
                // clear cached
                Config.clearWeatherData(this);
                // tell provider listeners that its gone
                WeatherContentProvider.updateCachedWeatherInfo(this);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mProvider) {
            String value = (String) newValue;
            int idx = mProvider.findIndexOfValue(value);
            mProvider.setSummary(mProvider.getEntries()[idx]);
            mProvider.setValueIndex(idx);
            WeatherService.startUpdate(this, true);
            return true;
        } else if (preference == mUnits) {
            String value = (String) newValue;
            int idx = mUnits.findIndexOfValue(value);
            mUnits.setSummary(mUnits.getEntries()[idx]);
            mUnits.setValueIndex(idx);
            WeatherService.startUpdate(this, true);
            return true;
        } else if (preference == mUpdateInterval) {
            String value = (String) newValue;
            int idx = mUpdateInterval.findIndexOfValue(value);
            mUpdateInterval.setSummary(mUpdateInterval.getEntries()[idx]);
            mUpdateInterval.setValueIndex(idx);
            WeatherService.scheduleUpdate(this);
            return true;
        }
        return false;
    }
    
    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Dialog dialog;

        // Build and show the dialog
        builder.setTitle(R.string.weather_retrieve_location_dialog_title);
        builder.setMessage(R.string.weather_retrieve_location_dialog_message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            checkLocationEnabled();
        }
    }

    private void checkLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            showDialog();
        } else {
            if (mTriggerUpdate) {
                WeatherService.scheduleUpdate(this);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkLocationEnabled();
                }
            }
            return;
        }
    }
}
