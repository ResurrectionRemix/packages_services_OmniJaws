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

import java.util.Date;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

public class WeatherService extends Service {
    static final String TAG = "WeatherService";
    private static final String ACTION_UPDATE = "org.omnirom.omnijaws.ACTION_UPDATE";
    private static final String ACTION_ALARM = "org.omnirom.omnijaws.ACTION_ALARM";
    private static final String EXTRA_FORCE = "force";

    static final String ACTION_CANCEL_LOCATION_UPDATE =
            "org.omnirom.omnijaws.CANCEL_LOCATION_UPDATE";

    public static final String BROADCAST_INTENT= "org.omnirom.omnijaws.BROADCAST_INTENT";
    public static final String STOP_INTENT= "org.omnirom.omnijaws.STOP_INTENT";

    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;
    static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L; // request for at most 5 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes
    private static final long ALARM_INTERVAL = AlarmManager.INTERVAL_HOUR;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private PowerManager.WakeLock mWakeLock;
    private boolean mRunning;
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener;

    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }
    
    public WeatherService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread("OpenDelta Service Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs,
                    String key) {
                if (key.equals(Config.PREF_KEY_PROVIDER) ||
                        key.equals(Config.PREF_KEY_UNITS) ||
                        key.equals(Config.PREF_KEY_LOCATION_ID)) {
                    try {
                        startUpdate(WeatherService.this, true);
                    } catch(Exception e) {
                        Log.e(TAG, "updatePrefs", e);
                    }
                }
            }
        };
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
    }

    public static void startUpdate(Context context, boolean force) {
        start(context, ACTION_UPDATE, force);
    }

    private static void start(Context context, String action, boolean force) {
        Intent i = new Intent(context, WeatherService.class);
        i.setAction(action);
        if (force) {
            i.putExtra(EXTRA_FORCE, force);
        }
        context.startService(i);
    }

    public static PendingIntent alarmPending(Context context) {
        Intent intent = new Intent(context, WeatherService.class);
        intent.setAction(ACTION_ALARM);
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean force = intent.getBooleanExtra(EXTRA_FORCE, false);
        if (ACTION_CANCEL_LOCATION_UPDATE.equals(intent.getAction())) {
            WeatherLocationListener.cancel(this);
            if (!mRunning) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (mRunning) {
            return START_REDELIVER_INTENT;
        }
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Service started, but no network ... stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!force) {
            long lastUpdate = Config.getLastUpdateTime(this);
            if (lastUpdate != 0) {
                long now = System.currentTimeMillis();
                if (lastUpdate + ALARM_INTERVAL > now) {
                    Log.d(TAG, "Service started, but update not due ... stopping");
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }
        }
        if (ACTION_UPDATE.equals(intent.getAction()) ||
                ACTION_ALARM.equals(intent.getAction())) {
            updateWeather();
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);

        Intent result = new Intent(STOP_INTENT);
        sendBroadcast(result);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected() || !info.isAvailable()) {
            Log.d(TAG, "No network connection is available for weather update");
            return false;
        }
        return true;
    }
    
    private Location getCurrentLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        Log.v(TAG, "Current location is " + location);

        if (location != null && location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
            Log.d(TAG, "Ignoring inaccurate location");
            location = null;
        }

        // If lastKnownLocation is not present (because none of the apps in the
        // device has requested the current location to the system yet) or outdated,
        // then try to get the current location use the provider that best matches the criteria.
        boolean needsUpdate = location == null;
        if (location != null) {
            long delta = System.currentTimeMillis() - location.getTime();
            needsUpdate = delta > OUTDATED_LOCATION_THRESHOLD_MILLIS;
        }
        if (needsUpdate) {
            Log.d(TAG, "Getting best location provider");
            String locationProvider = lm.getBestProvider(sLocationCriteria, true);
            if (TextUtils.isEmpty(locationProvider)) {
                Log.e(TAG, "No available location providers matching criteria.");
            } else {
                WeatherLocationListener.registerIfNeeded(this, locationProvider);
            }
        }

        return location;
    }

    private static void scheduleUpdate(Context context, long timeFromNow) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long due = System.currentTimeMillis() + timeFromNow;

        Log.d(TAG, "Scheduling next update at " + new Date(due));
        am.set(AlarmManager.RTC, due, alarmPending(context));
    }

    public static void canceUpdate(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Log.d(TAG, "Cancel pending update");

        am.cancel(alarmPending(context));
    }

    private void updateWeather() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mRunning = true;
                    mWakeLock.acquire();
                    AbstractWeatherProvider provider = Config.getProvider(WeatherService.this);
                    WeatherInfo w = null;
                    if (!Config.isCustomLocation(WeatherService.this)) {
                        Location location = getCurrentLocation();
                        if (location != null) {
                            w = provider.getLocationWeather(location, Config.isMetric(WeatherService.this));
                        }
                    } else if (Config.getLocationId(WeatherService.this) != null){
                        w = provider.getCustomWeather(Config.getLocationId(WeatherService.this), Config.isMetric(WeatherService.this));
                    }
                    if (w != null) {
                        Config.setWeatherData(WeatherService.this, w);
                        WeatherContentProvider.updateCachedWeatherInfo(WeatherService.this);
                        Intent result = new Intent(BROADCAST_INTENT);
                        sendBroadcast(result);
                    }
                } finally {
                    mWakeLock.release();
                    mRunning = false;
                    if (Config.isAutoUpdate(WeatherService.this)) {
                        scheduleUpdate(WeatherService.this, ALARM_INTERVAL);
                    }
                }
            }
         });
    }
}