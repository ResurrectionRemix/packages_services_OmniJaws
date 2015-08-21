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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

public class WeatherService extends Service {
    private static final String TAG = "WeatherService";
    private static final String ACTION_UPDATE = "org.omnirom.omnijaws.ACTION_UPDATE";
    private static final String ACTION_CANCEL_LOCATION_UPDATE =
            "org.omnirom.omnijaws.CANCEL_LOCATION_UPDATE";

    public static final String BROADCAST_INTENT= "org.omnirom.omnijaws.BROADCAST_INTENT";
    public static final String EXTRA_DATA= "weather";
    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;
    private static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L; // request for at most 5 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private PowerManager.WakeLock mWakeLock;
    private boolean mRunning;

    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }
    
    private static class WeatherLocationListener implements LocationListener {
        private Context mContext;
        private PendingIntent mTimeoutIntent;
        private static WeatherLocationListener sInstance = null;

        static void registerIfNeeded(Context context, String provider) {
            synchronized (WeatherLocationListener.class) {
                Log.d(TAG, "Registering location listener");
                if (sInstance == null) {
                    final Context appContext = context.getApplicationContext();
                    final LocationManager locationManager =
                            (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);

                    // Check location provider after set sInstance, so, if the provider is not
                    // supported, we never enter here again.
                    sInstance = new WeatherLocationListener(appContext);
                    // Check whether the provider is supported.
                    // NOTE!!! Actually only WeatherUpdateService class is calling this function
                    // with the NETWORK_PROVIDER, so setting the instance is safe. We must
                    // change this if this call receive different providers
                    LocationProvider lp = locationManager.getProvider(provider);
                    if (lp != null) {
                        Log.d(TAG, "LocationManager - Requesting single update");
                        locationManager.requestSingleUpdate(provider, sInstance,
                                appContext.getMainLooper());
                        sInstance.setTimeoutAlarm();
                    }
                }
            }
        }

        static void cancel(Context context) {
            synchronized (WeatherLocationListener.class) {
                if (sInstance != null) {
                    final Context appContext = context.getApplicationContext();
                    final LocationManager locationManager =
                        (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
                    Log.d(TAG, "Aborting location request after timeout");
                    locationManager.removeUpdates(sInstance);
                    sInstance.cancelTimeoutAlarm();
                    sInstance = null;
                }
            }
        }

        private WeatherLocationListener(Context context) {
            super();
            mContext = context;
        }

        private void setTimeoutAlarm() {
            Intent intent = new Intent(mContext, WeatherService.class);
            intent.setAction(ACTION_CANCEL_LOCATION_UPDATE);

            mTimeoutIntent = PendingIntent.getService(mContext, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
            long elapseTime = SystemClock.elapsedRealtime() + LOCATION_REQUEST_TIMEOUT;
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapseTime, mTimeoutIntent);
        }

        private void cancelTimeoutAlarm() {
            if (mTimeoutIntent != null) {
                AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
                am.cancel(mTimeoutIntent);
                mTimeoutIntent = null;
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            // Now, we have a location to use. Schedule a weather update right now.
            Log.d(TAG, "The location has changed, schedule an update ");
            synchronized (WeatherLocationListener.class) {
                startUpdate(mContext);
                cancelTimeoutAlarm();
                sInstance = null;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Now, we have a location to use. Schedule a weather update right now.
            Log.d(TAG, "The location service has become available, schedule an update ");
            if (status == LocationProvider.AVAILABLE) {
                synchronized (WeatherLocationListener.class) {
                    startUpdate(mContext);
                    cancelTimeoutAlarm();
                    sInstance = null;
                }
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Not used
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Not used
        }
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
        mWakeLock.setReferenceCounted(false);
    }

    public static void startUpdate(Context context) {
        start(context, ACTION_UPDATE);
    }

    private static void start(Context context, String action) {
        Intent i = new Intent(context, WeatherService.class);
        i.setAction(action);
        context.startService(i);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE.equals(intent.getAction())) {
            updateWeather();
        }
        return START_REDELIVER_INTENT;
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
                    } else {
                        w = provider.getCustomWeather(Config.getLocationId(WeatherService.this), Config.isMetric(WeatherService.this));
                    }
                    if (w != null) {
                        Config.setWeatherData(WeatherService.this, w);
                        Intent result = new Intent(BROADCAST_INTENT);
                        sendBroadcast(result);
                    }
                } finally {
                    mWakeLock.release();
                    mRunning = false;
                }
            }
         });
    }
}